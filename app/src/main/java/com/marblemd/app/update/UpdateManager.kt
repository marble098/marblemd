package com.marblemd.app.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.marblemd.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

enum class UpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY,
    ERROR
}

data class UpdateAsset(
    val abi: String,
    val name: String,
    val url: String,
    val sha256: String
)

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val tag: String,
    val commit: String,
    val asset: UpdateAsset
)

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val latestVersion: String? = null,
    val architecture: String = UpdateManager.preferredAbi(),
    val progress: Int = 0,
    val message: String = "Updates are checked automatically."
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

class UpdateManager(private val context: Context) {
    private val preferences =
        context.getSharedPreferences("marblemd_updates", Context.MODE_PRIVATE)

    fun shouldAutoCheck(): Boolean {
        val last = preferences.getLong(KEY_LAST_SUCCESSFUL_CHECK, 0L)
        return System.currentTimeMillis() - last >= AUTO_CHECK_INTERVAL_MS
    }

    fun isUnmeteredNetwork(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity != null && !connectivity.isActiveNetworkMetered
    }

    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            if (!force && !shouldAutoCheck()) return@withContext UpdateCheckResult.UpToDate

            runCatching {
                val json = fetchText(UPDATE_MANIFEST_URL)
                val root = JSONObject(json)
                val versionCode = root.getLong("versionCode")
                val versionName = root.getString("versionName")
                val tag = root.getString("tag")
                val commit = root.optString("commit")
                val assets = root.getJSONObject("assets")
                val chosen = chooseAsset(assets)
                    ?: error("No compatible APK is published for ${preferredAbi()}")

                preferences.edit()
                    .putLong(KEY_LAST_SUCCESSFUL_CHECK, System.currentTimeMillis())
                    .apply()

                if (versionCode <= BuildConfig.VERSION_CODE.toLong()) {
                    UpdateCheckResult.UpToDate
                } else {
                    UpdateCheckResult.Available(
                        UpdateInfo(
                            versionCode = versionCode,
                            versionName = versionName,
                            tag = tag,
                            commit = commit,
                            asset = chosen
                        )
                    )
                }
            }.getOrElse { error ->
                UpdateCheckResult.Error(error.message ?: "Unable to check for updates")
            }
        }

    suspend fun download(
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val finalFile = File(dir, info.asset.name)
            val partFile = File(dir, "${info.asset.name}.part")
            if (partFile.exists()) partFile.delete()

            val connection = openConnection(info.asset.url)
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastPercent = -1

            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = ((copied * 100L) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            connection.disconnect()

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(info.asset.sha256, ignoreCase = true)) {
                partFile.delete()
                error("Downloaded APK checksum does not match the signed release metadata")
            }

            verifyApk(info, partFile)

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            onProgress(100)
            finalFile
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    fun launchInstaller(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        activity.startActivity(intent)
    }

    private fun chooseAsset(assets: JSONObject): UpdateAsset? {
        val preferred = preferredAbi()
        val keys = buildList {
            add(preferred)
            Build.SUPPORTED_ABIS
                .map(::normalizeAbi)
                .filter { it != preferred }
                .forEach(::add)
            add("universal")
        }.distinct()

        for (key in keys) {
            val value = assets.optJSONObject(key) ?: continue
            return UpdateAsset(
                abi = key,
                name = value.getString("name"),
                url = value.getString("url"),
                sha256 = value.getString("sha256")
            )
        }
        return null
    }

    private fun verifyApk(info: UpdateInfo, apk: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            android.content.pm.PackageManager.GET_SIGNATURES
        }

        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Downloaded file is not a valid Android APK")

        if (archive.packageName != BuildConfig.APPLICATION_ID) {
            error("Downloaded APK package name is not MarbleMD")
        }
        if (PackageInfoCompat.getLongVersionCode(archive) != info.versionCode) {
            error("Downloaded APK version does not match update metadata")
        }

        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val currentCertificates = certificateDigests(installed)
        val archiveCertificates = certificateDigests(archive)

        if (currentCertificates.isEmpty() || archiveCertificates.isEmpty()) {
            error("Unable to verify APK signing certificate")
        }
        if (currentCertificates.intersect(archiveCertificates).isEmpty()) {
            error("Update signing certificate differs from the installed MarbleMD")
        }
    }

    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }
        return signatures.map { signature ->
            val bytes = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun fetchText(url: String): String {
        val connection = openConnection(url)
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .also { connection.disconnect() }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty(
            "User-Agent",
            "MarbleMD/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        connection.setRequestProperty("Accept", "application/octet-stream, application/json")
        connection.connect()
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            error("Update server returned HTTP $code")
        }
        return connection
    }

    companion object {
        private const val UPDATE_MANIFEST_URL =
            "https://github.com/marble098/marblemd/releases/latest/download/update.json"
        private const val KEY_LAST_SUCCESSFUL_CHECK = "last_successful_check"
        private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 45_000

        fun preferredAbi(): String =
            Build.SUPPORTED_ABIS.firstOrNull()?.let(::normalizeAbi) ?: "universal"

        private fun normalizeAbi(abi: String): String = when (abi.lowercase(Locale.US)) {
            "arm64-v8a", "arm64" -> "arm64-v8a"
            "armeabi-v7a", "armeabi" -> "armeabi-v7a"
            "x86_64", "x64" -> "x86_64"
            "x86" -> "x86"
            else -> abi.lowercase(Locale.US)
        }
    }
}
