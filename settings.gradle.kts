/*
 * HospitOmni — Gradle settings: one deployable module (bootstrap) plus
 * bounded-context libraries.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "hospitomni"

include(
    "shared",
    "account",
    "property",
    "ari",
    "booking",
    "channel",
    "bootstrap",
    "doctor",
)
