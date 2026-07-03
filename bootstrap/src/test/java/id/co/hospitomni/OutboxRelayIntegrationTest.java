/*
 * PLAN.md build step 4 DoD: ARI writes fan out through the dirty-cell outbox
 * to the MockOtaAdapter; readback on the mock confirms receipt. Also proves
 * the paused kill switch and the retry-after-failure path. Relay tick and
 * backoff are shortened via test properties.
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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "hospitomni.relay.fixed-delay=PT0.5S",
                "hospitomni.relay.backoff-base-seconds=1",
        })
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class OutboxRelayIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private record Content(String propertyId, String roomTypeId, String ratePlanId) {
    }

    @Test
    void ariWriteFansOutToMockOtaRangeCompressed() {
        Content content = seedContentAndChannel("Relay Hotel");

        postOk("/api/v1/availability", """
                {"values":[
                  {"property_id":"%s","room_type_id":"%s",
                   "date_from":"2027-01-01","date_to":"2027-01-03","availability":4}
                ]}""".formatted(content.propertyId(), content.roomTypeId()));
        postOk("/api/v1/restrictions", """
                {"values":[{"property_id":"%s","rate_plan_id":"%s",
                  "date_from":"2027-01-01","date_to":"2027-01-03","rate":650000,"min_stay":1}]}"""
                .formatted(content.propertyId(), content.ratePlanId()));

        JsonNode push = awaitFirstPush(content.propertyId());
        assertEquals("mock", push.path("ota_name").asString());
        assertEquals(0, push.path("epoch").asLong());

        // Availability: 3 dirty days, one value → exactly one compressed range.
        JsonNode availability = push.path("availability");
        assertEquals(1, availability.size(), "expected one compressed range: " + push);
        assertEquals("2027-01-01", availability.get(0).path("date_from").asString());
        assertEquals("2027-01-03", availability.get(0).path("date_to").asString());
        assertEquals(4, availability.get(0).path("availability").asInt());

        // Restrictions: current values pushed with the full field set.
        JsonNode restrictions = push.path("restrictions");
        assertEquals(1, restrictions.size(), "expected one compressed range: " + push);
        assertEquals(650000, restrictions.get(0).path("rate").asLong());
        assertEquals(1, restrictions.get(0).path("min_stay").asInt());
    }

    @Test
    void pausedChannelBlocksPushesUntilResumed() {
        Content content = seedContentAndChannel("Paused Hotel");
        String channelId = getOk("/api/v1/property-channels?property_id=" + content.propertyId())
                .get(0).path("id").asString();

        exchangeOk(HttpMethod.PUT, "/api/v1/property-channels/" + channelId,
                "{\"paused\":true}");
        postOk("/api/v1/availability", """
                {"values":[{"property_id":"%s","room_type_id":"%s",
                  "date":"2027-02-01","availability":2}]}"""
                .formatted(content.propertyId(), content.roomTypeId()));

        // Several relay ticks pass; the paused channel must stay silent.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> pushCount(content.propertyId()) == 0);

        exchangeOk(HttpMethod.PUT, "/api/v1/property-channels/" + channelId,
                "{\"paused\":false}");
        JsonNode push = awaitFirstPush(content.propertyId());
        assertEquals(2, push.path("availability").get(0).path("availability").asInt());
    }

    @Test
    void failedPushRetriesWithBackoffAndSucceeds() {
        Content content = seedContentAndChannel("Retry Hotel");
        postOk2xx("/api/v1/mock-ota/fail-next", "{\"count\":1}");

        postOk("/api/v1/availability", """
                {"values":[{"property_id":"%s","room_type_id":"%s",
                  "date":"2027-03-01","availability":9}]}"""
                .formatted(content.propertyId(), content.roomTypeId()));

        // First attempt throws (injected), backoff ~1s, retry delivers.
        JsonNode push = awaitFirstPush(content.propertyId());
        assertEquals(9, push.path("availability").get(0).path("availability").asInt());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private Content seedContentAndChannel(String title) {
        String propertyId = postCreated("/api/v1/properties", """
                {"title":"%s","currency":"IDR","timezone":"Asia/Jakarta"}""".formatted(title))
                .path("id").asString();
        String roomTypeId = postCreated("/api/v1/room-types", """
                {"property_id":"%s","title":"Std","count_of_rooms":5,
                 "occ_adults":2,"occ_children":0}""".formatted(propertyId))
                .path("id").asString();
        String ratePlanId = postCreated("/api/v1/rate-plans", """
                {"property_id":"%s","room_type_id":"%s","title":"BAR","currency":"IDR"}"""
                .formatted(propertyId, roomTypeId))
                .path("id").asString();
        JsonNode channel = postCreated("/api/v1/property-channels", """
                {"property_id":"%s","ota_name":"mock"}""".formatted(propertyId));
        assertEquals("mock", channel.path("ota_name").asString());
        return new Content(propertyId, roomTypeId, ratePlanId);
    }

    private JsonNode awaitFirstPush(String propertyId) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .until(() -> pushCount(propertyId) > 0);
        return getOk("/api/v1/mock-ota/pushes?property_id=" + propertyId).get(0);
    }

    private int pushCount(String propertyId) {
        return getOk("/api/v1/mock-ota/pushes?property_id=" + propertyId).size();
    }

    private JsonNode postCreated(String path, String json) {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "POST " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private void postOk(String path, String json) {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "POST " + path + " → " + response.getBody());
    }

    private void postOk2xx(String path, String json) {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "POST " + path + " → " + response.getBody());
    }

    private JsonNode exchangeOk(HttpMethod method, String path, String json) {
        ResponseEntity<String> response = rest.exchange(path, method, entity(json), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                method + " " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private JsonNode getOk(String path) {
        return exchangeOk(HttpMethod.GET, path, null);
    }

    private HttpEntity<String> entity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthFilter.API_KEY_HEADER, DevDataSeeder.DEV_RAW_KEY);
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return new HttpEntity<>(json, headers);
    }
}
