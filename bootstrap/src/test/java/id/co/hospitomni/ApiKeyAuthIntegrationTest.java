/*
 * Account auth end to end against Testcontainers Postgres 18:
 * no key → 401 Problem Details; seeded key → 200 with the seeded property;
 * actuator health open. Runs the `local` profile so DevDataSeeder provides
 * the seeded account/key/property (verifying the seeder too).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni;

import id.co.hospitomni.config.ApiKeyAuthFilter;
import id.co.hospitomni.config.DevDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class ApiKeyAuthIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void propertiesWithoutKeyIs401ProblemDetails() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/properties", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"status\":401"));
    }

    @Test
    void propertiesWithBogusKeyIs401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/properties", HttpMethod.GET,
                withKey("homni_not_a_real_key"), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void propertiesWithSeededKeyIs200AndListsSeededProperty() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/properties", HttpMethod.GET,
                withKey(DevDataSeeder.DEV_RAW_KEY), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("Dev Hotel Bandung"));
        assertTrue(body.contains("Asia/Jakarta"));
    }

    @Test
    void actuatorHealthIsOpen() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private static HttpEntity<Void> withKey(String rawKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthFilter.API_KEY_HEADER, rawKey);
        return new HttpEntity<>(headers);
    }
}
