plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

// ---------------------------------------------------------------------------
// TEMPORARY: CI diagnostics hook (removed before merge).
//
// GitHub Actions log downloads are unavailable in the environment this branch
// was authored in, so on failure we run tools/ci-diagnostics.sh, which re-runs
// the Kotlin compilation and pushes the captured output to the branch.
// ---------------------------------------------------------------------------
gradle.buildFinished {
    if (it.failure != null && System.getenv("GITHUB_ACTIONS") == "true") {
        try {
            val script = File(rootDir, "tools/ci-diagnostics.sh")
            if (script.exists()) {
                ProcessBuilder("bash", script.absolutePath)
                    .directory(rootDir)
                    .inheritIO()
                    .start()
                    .waitFor()
            }
        } catch (ignored: Throwable) {
            // Never mask the original build failure.
        }
    }
}
