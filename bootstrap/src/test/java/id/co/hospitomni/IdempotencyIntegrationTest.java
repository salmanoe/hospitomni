/*
 * Idempotency-Key contract on north-API writes: missing key → 400, retry
 * with the same key + payload → stored response replayed (and the write
 * executed once), key reuse with a different payload → 422, mock-OTA hooks
 * exempt, and the OpenAPI spec advertises the header as required so the
 * generated HospitOps client sends it. 401 still wins over the 400 when
 * the key AND the api key are both missing.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni;

import id.co.hospitomni.config.ApiKeyAuthFilter;
import id.co.hospitomni.config.DevDataSeeder;
import id.co.hospitomni.config.IdempotencyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class IdempotencyIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void writeWithoutKeyIs400ProblemDetail() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/properties", HttpMethod.POST,
                entity(propertyJson("No Key Hotel"), null), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String body = String.valueOf(response.getBody());
        assertTrue(body.contains("Idempotency-Key"), body);
        assertTrue(body.contains("\"status\":400"), body);
    }

    @Test
    void retryWithSameKeyAndPayloadReplaysWithoutReExecuting() throws Exception {
        // Non-ASCII title: replay must come back UTF-8, not the servlet
        // default ISO-8859-1, or the body-equality check below fails.
        String key = UUID.randomUUID().toString();
        String title = "Réplay Hôtel " + UUID.randomUUID();
        String json = propertyJson(title);

        ResponseEntity<String> first = rest.exchange(
                "/api/v1/properties", HttpMethod.POST, entity(json, key), String.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode(), String.valueOf(first.getBody()));

        ResponseEntity<String> retry = rest.exchange(
                "/api/v1/properties", HttpMethod.POST, entity(json, key), String.class);
        assertEquals(HttpStatus.CREATED, retry.getStatusCode());
        assertEquals("true", retry.getHeaders().getFirst(IdempotencyFilter.REPLAYED_HEADER));
        assertEquals(first.getBody(), retry.getBody(),
                "replay must return the stored response verbatim");

        // The write ran once: exactly one property carries the unique title.
        JsonNode properties = objectMapper
                .readTree(rest.exchange("/api/v1/properties", HttpMethod.GET,
                        entity(null, null), String.class).getBody())
                .path("data");
        long matching = properties.valueStream()
                .filter(p -> title.equals(p.path("title").asString()))
                .count();
        assertEquals(1, matching);
    }

    @Test
    void sameKeyWithDifferentPayloadIs422() {
        String key = UUID.randomUUID().toString();
        ResponseEntity<String> first = rest.exchange(
                "/api/v1/properties", HttpMethod.POST,
                entity(propertyJson("Original Payload Hotel"), key), String.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode(), String.valueOf(first.getBody()));

        ResponseEntity<String> reused = rest.exchange(
                "/api/v1/properties", HttpMethod.POST,
                entity(propertyJson("Tampered Payload Hotel"), key), String.class);
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, reused.getStatusCode());
        assertTrue(String.valueOf(reused.getBody()).contains("different request payload"));
    }

    @Test
    void unauthenticatedWriteIs401NotMissingKey400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/properties", HttpMethod.POST,
                new HttpEntity<>(propertyJson("Anonymous Hotel"), headers), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void mockOtaWritesAreExempt() {
        // The mock OTA is the simulated far end, not a north-API client;
        // redelivery semantics are exercised there on purpose.
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/mock-ota/fail-next", HttpMethod.POST,
                entity("""
                        {"count":1}""", null), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), String.valueOf(response.getBody()));
    }

    @Test
    void openApiSpecMarksIdempotencyKeyRequiredOnWrites() throws Exception {
        JsonNode spec = objectMapper.readTree(
                rest.getForEntity("/v3/api-docs", String.class).getBody());
        JsonNode createProperty = spec.path("paths").path("/api/v1/properties").path("post");
        boolean documented = createProperty.path("parameters").valueStream().anyMatch(param ->
                IdempotencyFilter.IDEMPOTENCY_KEY_HEADER.equals(param.path("name").asString())
                        && param.path("required").asBoolean());
        assertTrue(documented, "POST /properties must document a required Idempotency-Key header");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static String propertyJson(String title) {
        return """
                {"title":"%s","currency":"IDR","timezone":"Asia/Jakarta"}""".formatted(title);
    }

    private HttpEntity<String> entity(String json, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthFilter.API_KEY_HEADER, DevDataSeeder.DEV_RAW_KEY);
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (idempotencyKey != null) {
            headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        return new HttpEntity<>(json, headers);
    }
}
