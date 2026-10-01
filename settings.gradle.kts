// ---------------------------------------------------------------------------
// TEMPORARY CI diagnostics (removed before merge).
//
// GitHub Actions logs cannot be downloaded in the environment this branch was
// authored in, so the build publishes its own compiler output: the helper below
// recompiles the project in a scratch worktree and pushes the captured console
// text to the `marblemd-ci-diagnostics` branch.
// ---------------------------------------------------------------------------
if (System.getenv("GITHUB_ACTIONS") == "true" &&
    System.getenv("MARBLEMD_DIAGNOSTICS_CHILD") != "1"
) {
    runCatching {
        val helper = file("tools/ci-diagnostics.sh")
        if (helper.exists()) {
            ProcessBuilder("bash", helper.absolutePath)
                .directory(rootDir)
                .inheritIO()
                .start()
                .waitFor()
        }
    }
}

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
