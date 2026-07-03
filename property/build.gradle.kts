/*
 * Channex-compatible content: properties, room types, rate plans (owns the UUIDs).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */

dependencies {
    api(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
