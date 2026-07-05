/*
 * Inbound OTA bookings exposed as a cursor-based, replayable booking-events stream.
 * Persistence split by workload: the booking aggregate is JPA; the append-only
 * revision stream and availability adjustments are JdbcClient + SQL.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */

dependencies {
    api(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
