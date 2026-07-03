/*
 * OTA adapter seam + outbox relay: OtaAdapterPort + registry, dirty-cell
 * writer (same-tx event listener), scheduled relay with SKIP LOCKED claims,
 * MockOtaAdapter first.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */

dependencies {
    api(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
