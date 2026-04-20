plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "artinus-backend-assignment"

include(":subscription:domain")
include(":subscription:app")
