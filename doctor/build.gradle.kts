/*
 * Standalone health-check CLI ("doctor"): walks the public API exactly like
 * a PMS client would — key, mappings, ARI readback, event stream, sync
 * status — and exits non-zero on failure. Deliberately NOT a Spring app and
 * NOT wired into bootstrap: it must fail when the service is down.
 *
 * Run: gradlew :doctor:run --args="--api-key=<key> [--base-url=…] [--property-id=…]"
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */

plugins {
    application
}

dependencies {
    // JSON parsing only; version managed by the Boot BOM applied root-wide.
    implementation("tools.jackson.core:jackson-databind")
}

application {
    mainClass.set("id.co.hospitomni.doctor.Doctor")
}
