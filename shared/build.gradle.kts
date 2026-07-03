/*
 * Shared kernel — typed IDs, Money (per-currency exponent; IDR is exponent-0), ApiResponse, Guard, domain events. Stays THIN: no JPA, no business logic.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */

dependencies {
    implementation("org.springframework:spring-context")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
}
