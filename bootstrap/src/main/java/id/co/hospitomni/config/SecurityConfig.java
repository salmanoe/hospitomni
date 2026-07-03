/*
 * API-key security: stateless, CSRF off (no browser session to ride),
 * actuator health open, everything else requires a resolved account.
 * 401s are RFC 9457 Problem Details (PLAN.md north-side contract).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(problemDetailsEntryPoint()))
                .build();
    }

    /** RFC 9457 Problem Details body for missing/unknown/revoked keys. */
    private static AuthenticationEntryPoint problemDetailsEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401,\
                    "detail":"Missing, unknown, or revoked user-api-key header."}""");
        };
    }
}
