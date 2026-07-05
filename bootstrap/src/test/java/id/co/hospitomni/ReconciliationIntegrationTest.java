/*
 * End-to-end drift correction: relay pushes ARI to the mock OTA; a
 * reconciliation over the synced window finds zero drift; wiping the mock
 * (the OTA "lost" our state) makes the next run report every set night as
 * drifted and trigger the fenced full refresh — epoch bumps, the relay
 * re-pushes with the new epoch, and a third run is clean again. Runs are
 * listed newest-first and the outcome lands on /sync-status. Bad windows
 * are 400, foreign channels 404.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
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

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class ReconciliationIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private record Fixture(String propertyId, String roomTypeId, String ratePlanId, String channelId) {
    }

    @Test
    void driftIsDetectedAndSelfHealedByFencedFullRefresh() {
        Fixture fx = seedSyncedChannel();

        // In sync → clean run, no refresh, epoch untouched.
        JsonNode clean = reconcile(fx.channelId());
        assertEquals(0, clean.path("drift_count").asInt(), clean.toString());
        assertEquals(false, clean.path("refreshed").asBoolean());
        assertEquals(0, clean.path("epoch").asLong());

        // The OTA "loses" our state: every set night is now drifted.
        deleteOk("/api/v1/mock-ota/pushes");
        JsonNode drifted = reconcile(fx.channelId());
        assertEquals(6, drifted.path("drift_count").asInt(),
                "3 availability + 3 restriction nights: " + drifted);
        assertEquals(true, drifted.path("refreshed").asBoolean());
        assertEquals(1, drifted.path("epoch").asLong(), "full refresh must bump the epoch");
        JsonNode sampleCell = drifted.path("drift_sample").path(0);
        assertEquals("AVAILABILITY", sampleCell.path("unit").asString());
        assertTrue(sampleCell.path("observed").isNull(),
                "the OTA holds nothing after the wipe: " + sampleCell);

        // The refresh re-pushes the window with the bumped epoch...
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .until(() -> pushCount(fx.propertyId()) > 0);
        JsonNode healPush = getOk("/api/v1/mock-ota/pushes?property_id=" + fx.propertyId()).get(0);
        assertEquals(1, healPush.path("epoch").asLong());

        // ...after which the next run is clean again.
        JsonNode healed = reconcile(fx.channelId());
        assertEquals(0, healed.path("drift_count").asInt(), healed.toString());
        assertEquals(false, healed.path("refreshed").asBoolean());

        // Audit trail: three runs, newest first.
        JsonNode runs = getOk("/api/v1/reconciliations?property_channel_id=" + fx.channelId());
        assertEquals(3, runs.size());
        assertEquals(0, runs.get(0).path("drift_count").asInt());
        assertEquals(6, runs.get(1).path("drift_count").asInt());
        assertTrue(runs.get(1).path("sample_drift").asString().contains("expected=4"),
                runs.get(1).toString());
        assertNull(runs.get(0).path("sample_drift").textValue(),
                "clean runs store no drift sample");

        // Outcome lands on /sync-status.
        JsonNode channelStatus = getOk("/api/v1/sync-status?property_id=" + fx.propertyId())
                .path("channels").path(0);
        assertEquals(0, channelStatus.path("last_drift_count").asInt());
        assertTrue(!channelStatus.path("last_reconciled_at").isNull(), channelStatus.toString());
    }

    @Test
    void invalidWindowIs400() {
        Fixture fx = seedSyncedChannel();
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/reconciliations", HttpMethod.POST,
                entity("""
                        {"property_channel_id":"%s","date_from":"2027-06-10","date_to":"2027-06-01"}"""
                        .formatted(fx.channelId())),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("date_to"));
    }

    @Test
    void unknownChannelIs404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/reconciliations", HttpMethod.POST,
                entity("""
                        {"property_channel_id":"00000000-0000-4000-8000-00000000dead",
                         "date_from":"2027-06-01","date_to":"2027-06-03"}"""),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ── fixture ─────────────────────────────────────────────────────────

    /** Content + channel + ARI written and confirmed delivered to the mock. */
    private Fixture seedSyncedChannel() {
        String propertyId = postCreated("/api/v1/properties", """
                {"title":"Reconcile Hotel %s","currency":"IDR","timezone":"Asia/Jakarta"}"""
                .formatted(UUID.randomUUID())).path("id").asString();
        String roomTypeId = postCreated("/api/v1/room-types", """
                {"property_id":"%s","title":"Std","count_of_rooms":5,
                 "occ_adults":2,"occ_children":0}""".formatted(propertyId)).path("id").asString();
        String ratePlanId = postCreated("/api/v1/rate-plans", """
                {"property_id":"%s","room_type_id":"%s","title":"BAR","currency":"IDR"}"""
                .formatted(propertyId, roomTypeId)).path("id").asString();
        String channelId = postCreated("/api/v1/property-channels", """
                {"property_id":"%s","ota_name":"mock"}""".formatted(propertyId))
                .path("id").asString();

        postOk("/api/v1/availability", """
                {"values":[{"property_id":"%s","room_type_id":"%s",
                  "date_from":"2027-06-01","date_to":"2027-06-03","availability":4}]}"""
                .formatted(propertyId, roomTypeId));
        postOk("/api/v1/restrictions", """
                {"values":[{"property_id":"%s","rate_plan_id":"%s",
                  "date_from":"2027-06-01","date_to":"2027-06-03","rate":650000,"min_stay":2}]}"""
                .formatted(propertyId, ratePlanId));
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .until(() -> pushCount(propertyId) > 0);
        return new Fixture(propertyId, roomTypeId, ratePlanId, channelId);
    }

    private JsonNode reconcile(String channelId) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/reconciliations", HttpMethod.POST,
                entity("""
                        {"property_channel_id":"%s","date_from":"2027-06-01","date_to":"2027-06-03"}"""
                        .formatted(channelId)),
                String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "POST /reconciliations → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private int pushCount(String propertyId) {
        return getOk("/api/v1/mock-ota/pushes?property_id=" + propertyId).size();
    }

    // ── helpers ─────────────────────────────────────────────────────────

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
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "POST " + path + " → " + response.getBody());
    }

    private void deleteOk(String path) {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.DELETE, entity(null), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "DELETE " + path + " → " + response.getBody());
    }

    private JsonNode getOk(String path) {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.GET, entity(null), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private HttpEntity<String> entity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ApiKeyAuthFilter.API_KEY_HEADER, DevDataSeeder.DEV_RAW_KEY);
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
        }
        return new HttpEntity<>(json, headers);
    }
}
