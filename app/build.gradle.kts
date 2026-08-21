import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.marblemd.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.marblemd.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val ciKeystorePath = System.getenv("MARBLEMD_KEYSTORE")
    val ciReleaseSigning = if (!ciKeystorePath.isNullOrBlank()) {
        signingConfigs.create("ciRelease") {
            storeFile = file(ciKeystorePath)
            storePassword = System.getenv("MARBLEMD_STORE_PASSWORD") ?: "marblemd-ci"
            keyAlias = System.getenv("MARBLEMD_KEY_ALIAS") ?: "marblemd"
            keyPassword = System.getenv("MARBLEMD_KEY_PASSWORD") ?: "marblemd-ci"
        }
    } else null

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ciReleaseSigning?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    // MarbleMD is pure managed code, so the universal APK already works on every Android ABI.
    // ABI split outputs are still requested for users/distributors that prefer architecture-labelled APKs.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

val fontsDir = layout.projectDirectory.dir("src/main/assets/fonts")
val fetchFonts by tasks.registering {
    group = "marblemd"
    description = "Fetches OFL fonts used by MarbleMD (Vazirmatn + Noto Sans)."
    outputs.files(
        fontsDir.file("Vazirmatn.ttf"),
        fontsDir.file("NotoSans.ttf")
    )
    doLast {
        val dir = fontsDir.asFile
        dir.mkdirs()
        val fonts = mapOf(
            "Vazirmatn.ttf" to "https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/variable/Vazirmatn%5Bwght%5D.ttf",
            "NotoSans.ttf" to "https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans%5Bwdth%2Cwght%5D.ttf"
        )
        fonts.forEach { (name, url) ->
            val out = dir.resolve(name)
            if (!out.exists() || out.length() < 10_000L) {
                logger.lifecycle("Downloading $name")
                URI(url).toURL().openStream().use { input -> out.outputStream().use(input::copyTo) }
            }
        }
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(fetchFonts)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // AGP 9.x requires unique library namespaces. Keep the VectorDrawable pair
    // on the current stable release instead of old transitive 1.0.0 artifacts.
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image-picasso:4.6.2")
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("io.noties.markwon:linkify:4.6.2")

    testImplementation("junit:junit:4.13.2")
}
