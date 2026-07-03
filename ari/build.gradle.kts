/*
 * Availability + restrictions storage; range compression; partial-update
 * merge. High-volume set-shaped path: JdbcTemplate batch SQL, no JPA
 * (PLAN.md decision "Persistence split by workload").
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
