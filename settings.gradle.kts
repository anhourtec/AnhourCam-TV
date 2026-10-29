pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "camgrid-tv"

include(":bitvision-client")
include(":bitvision-client-cli")
include(":ipcam")
include(":layouts")
include(":pairing")
include(":app")
