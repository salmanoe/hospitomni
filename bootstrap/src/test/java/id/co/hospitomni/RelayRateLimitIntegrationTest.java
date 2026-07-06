/*
 * Rate limits are not faults: with max-attempts=1 (where any counted
 * failure dead-letters immediately), a 429-shaped push still delivers
 * after the OTA's retry-after — deferred, never dead-lettered, and
 * last_push_error stays clean. Injected latency slows pushes without
 * breaking the relay.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni;

import id.co.hospitomni.config.ApiKeyAuthFilter;
import id.co.hospitomni.config.DevDataSeeder;
import id.co.hospitomni.config.IdempotencyFilter;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "hospitomni.relay.fixed-delay=PT0.5S",
                "hospitomni.relay.backoff-base-seconds=1",
                // Any COUNTED failure dead-letters on the spot — so a delivery
                // after throttling proves the rate-limit path counted nothing.
                "hospitomni.relay.max-attempts=1",
        })
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class RelayRateLimitIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private record Content(String propertyId, String roomTypeId) {
    }

    @BeforeEach
    void resetMock() {
        rest.exchange("/api/v1/mock-ota/pushes", HttpMethod.DELETE, entity(null), String.class);
    }

    @Test
    void rateLimitedPushDefersAndDeliversWithoutDeadLetter() throws Exception {
        Content content = seedContentAndChannel();
        post2xx("/api/v1/mock-ota/rate-limit-next", """
                {"count":1,"retry_after_seconds":2}""");

        post2xx("/api/v1/availability", """
                {"values":[{"property_id":"%s","room_type_id":"%s",
                  "date":"2027-11-01","availability":3}]}"""
                .formatted(content.propertyId(), content.roomTypeId()));

        // First attempt is throttled; the deferred retry still lands even
        // though max-attempts=1 would have dead-lettered a counted failure.
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .until(() -> pushCount(content.propertyId()) > 0);

        JsonNode status = getOk("/api/v1/sync-status?property_id=" + content.propertyId())
                .path("channels").path(0);
        assertEquals(0, status.path("dead_letters").asLong(), status.toString());
        assertTrue(status.path("last_push_error").isNull(),
                "throttling must not surface as a push error: " + status);
    }

    @Test
    void latencySlowsPushesWithoutBreakingDelivery() throws Exception {
        Content content = seedContentAndChannel();
        post2xx("/api/v1/mock-ota/latency", """
                {"millis":400}""");

        post2xx("/api/v1/availability", """
                {"values":[{"property_id":"%s","room_type_id":"%s",
                  "date":"2027-11-02","availability":2}]}"""
                .formatted(content.propertyId(), content.roomTypeId()));
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .until(() -> pushCount(content.propertyId()) > 0);

        post2xx("/api/v1/mock-ota/latency", """
                {"millis":0}""");
    }

    // ── fixture/helpers ─────────────────────────────────────────────────

    private Content seedContentAndChannel() throws Exception {
        String propertyId = postCreated("/api/v1/properties", """
                {"title":"RateLimit Hotel %s","currency":"IDR","timezone":"Asia/Jakarta"}"""
                .formatted(UUID.randomUUID())).path("id").asString();
        String roomTypeId = postCreated("/api/v1/room-types", """
                {"property_id":"%s","title":"Std","count_of_rooms":5,
                 "occ_adults":2,"occ_children":0}""".formatted(propertyId)).path("id").asString();
        postCreated("/api/v1/property-channels", """
                {"property_id":"%s","ota_name":"mock"}""".formatted(propertyId));
        return new Content(propertyId, roomTypeId);
    }

    private int pushCount(String propertyId) throws Exception {
        return getOk("/api/v1/mock-ota/pushes?property_id=" + propertyId).size();
    }

    private JsonNode postCreated(String path, String json) throws Exception {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "POST " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private void post2xx(String path, String json) {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "POST " + path + " → " + response.getBody());
    }

    private JsonNode getOk(String path) throws Exception {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.GET, entity(null), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private HttpEntity<String> entity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthFilter.API_KEY_HEADER, DevDataSeeder.DEV_RAW_KEY);
        headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return new HttpEntity<>(json, headers);
    }
}
