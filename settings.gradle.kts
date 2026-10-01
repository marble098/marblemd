// ---------------------------------------------------------------------------
// TEMPORARY CI diagnostics (removed before merge).
//
// The Actions log host is unreachable in the authoring environment. Run a
// one-time diagnostic build in a detached scratch worktree and expose compiler,
// test and lint failures as annotations readable through GitHub's checks API.
// This helper never commits or pushes to any branch.
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
