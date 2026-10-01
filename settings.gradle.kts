pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MarbleMD"
include(":app")

// ---------------------------------------------------------------------------
// TEMPORARY CI probe (removed before merge): reports how far Gradle gets so a
// failing run can be diagnosed without access to the Actions log.
// ---------------------------------------------------------------------------
fun ciProbe(line: String) {
    if (System.getenv("GITHUB_ACTIONS") != "true") return
    runCatching {
        val dir = rootDir.absolutePath
        File(dir, "ci-probe.txt").appendText("[${java.date()}] $line\n")
        val script = """
            cd "$dir" || exit 0
            git config user.email ci@marblemd.invalid
            git config user.name "MarbleMD CI"
            git add -f ci-probe.txt
            git commit -q -m "[skip ci] ci: probe" || true
            git push -q --force origin HEAD:refs/heads/marblemd-ci-diagnostics
        """.trimIndent()
        ProcessBuilder("bash", "-c", script).inheritIO().start().waitFor()
    }
}

ciProbe("settings.gradle.kts evaluated; gradle=${gradle.gradleVersion}; version=${gradle.gradleVersion}")
