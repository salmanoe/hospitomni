/*
 * PLAN.md build step 3 DoD: /availability + /restrictions write/read with
 * range compression + partial updates, verified by readback equality against
 * Testcontainers Postgres 18.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni;

import id.co.hospitomni.config.ApiKeyAuthFilter;
import id.co.hospitomni.config.DevDataSeeder;
import org.junit.jupiter.api.BeforeAll;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class AriIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private static String propertyId;
    private static String roomTypeId;
    private static String ratePlanId;
    private static boolean seeded;

    @BeforeAll
    static void resetSeedFlag() {
        seeded = false;
    }

    private void seedContent() {
        if (seeded) return;
        propertyId = postCreated("/api/v1/properties", """
                {"title":"ARI Hotel","currency":"IDR","timezone":"Asia/Jakarta"}""")
                .path("id").asString();
        roomTypeId = postCreated("/api/v1/room-types", """
                {"property_id":"%s","title":"ARI Deluxe","count_of_rooms":10,
                 "occ_adults":2,"occ_children":0}""".formatted(propertyId))
                .path("id").asString();
        ratePlanId = postCreated("/api/v1/rate-plans", """
                {"property_id":"%s","room_type_id":"%s","title":"ARI BAR","currency":"IDR"}"""
                .formatted(propertyId, roomTypeId))
                .path("id").asString();
        seeded = true;
    }

    @Test
    void availabilityWriteReadsBackRangeCompressed() {
        seedContent();
        // Range 2026-09-01..04 = 7, single 2026-09-05 = 7 (extends run),
        // 2026-09-06 = 3 (breaks run).
        JsonNode write = postOk("/api/v1/availability", """
                {"values":[
                  {"property_id":"%s","room_type_id":"%s",
                   "date_from":"2026-09-01","date_to":"2026-09-04","availability":7},
                  {"property_id":"%s","room_type_id":"%s","date":"2026-09-05","availability":7},
                  {"property_id":"%s","room_type_id":"%s","date":"2026-09-06","availability":3}
                ]}""".formatted(propertyId, roomTypeId, propertyId, roomTypeId, propertyId, roomTypeId));
        assertEquals(6, write.path("updated").asInt());

        JsonNode ranges = getOk("/api/v1/availability?property_id=" + propertyId
                + "&date_gte=2026-09-01&date_lte=2026-09-30");
        assertEquals(2, ranges.size(), "expected two compressed runs: " + ranges);
        assertEquals("2026-09-01", ranges.get(0).path("date_from").asString());
        assertEquals("2026-09-05", ranges.get(0).path("date_to").asString());
        assertEquals(7, ranges.get(0).path("availability").asInt());
        assertEquals("2026-09-06", ranges.get(1).path("date_from").asString());
        assertEquals(3, ranges.get(1).path("availability").asInt());
    }

    @Test
    void restrictionsPartialUpdateKeepsUnsentFields() {
        seedContent();
        // First write: rate + min_stay over a range.
        postOk("/api/v1/restrictions", """
                {"values":[{"property_id":"%s","rate_plan_id":"%s",
                  "date_from":"2026-10-01","date_to":"2026-10-03",
                  "rate":750000,"min_stay":2}]}""".formatted(propertyId, ratePlanId));

        // Partial update: only rate changes on 10-02; min_stay must survive.
        postOk("/api/v1/restrictions", """
                {"values":[{"property_id":"%s","rate_plan_id":"%s",
                  "date":"2026-10-02","rate":800000}]}""".formatted(propertyId, ratePlanId));

        JsonNode ranges = getOk("/api/v1/restrictions?property_id=" + propertyId
                + "&date_gte=2026-10-01&date_lte=2026-10-03");
        // Rate change on 10-02 splits into three runs; min_stay=2 everywhere.
        assertEquals(3, ranges.size(), "expected three runs after mid-range rate change: " + ranges);
        assertEquals(750000, ranges.get(0).path("rate").asLong());
        assertEquals(800000, ranges.get(1).path("rate").asLong());
        assertEquals(750000, ranges.get(2).path("rate").asLong());
        for (JsonNode range : ranges) {
            assertEquals(2, range.path("min_stay").asInt(), "min_stay lost in " + range);
        }
    }

    @Test
    void restrictionsFieldsProjectionMergesRuns() {
        seedContent();
        // Different rates but identical min_stay across a range.
        postOk("/api/v1/restrictions", """
                {"values":[
                  {"property_id":"%s","rate_plan_id":"%s","date":"2026-11-01","rate":500000,"min_stay":1},
                  {"property_id":"%s","rate_plan_id":"%s","date":"2026-11-02","rate":600000,"min_stay":1}
                ]}""".formatted(propertyId, ratePlanId, propertyId, ratePlanId));

        // Unprojected: two runs (rate differs).
        JsonNode all = getOk("/api/v1/restrictions?property_id=" + propertyId
                + "&date_gte=2026-11-01&date_lte=2026-11-02");
        assertEquals(2, all.size());

        // Projected to min_stay only: one merged run, no rate field on the wire.
        JsonNode projected = getOk("/api/v1/restrictions?property_id=" + propertyId
                + "&date_gte=2026-11-01&date_lte=2026-11-02&fields=min_stay");
        assertEquals(1, projected.size(), "projection should merge runs: " + projected);
        assertEquals(1, projected.get(0).path("min_stay").asInt());
        assertFalse(projected.get(0).has("rate"), "unselected field must be omitted");
    }

    @Test
    void crossTenantRoomTypeIs404AndBadSpanIs400() {
        seedContent();
        // Unknown room type under an owned property → 404.
        ResponseEntity<String> notFound = rest.exchange("/api/v1/availability", HttpMethod.POST,
                entity("""
                        {"values":[{"property_id":"%s",
                          "room_type_id":"00000000-0000-4000-8000-00000000dead",
                          "date":"2026-09-01","availability":1}]}""".formatted(propertyId)),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());

        // date_to before date_from → 400.
        ResponseEntity<String> badSpan = rest.exchange("/api/v1/availability", HttpMethod.POST,
                entity("""
                        {"values":[{"property_id":"%s","room_type_id":"%s",
                          "date_from":"2026-09-10","date_to":"2026-09-01","availability":1}]}"""
                        .formatted(propertyId, roomTypeId)),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, badSpan.getStatusCode());
        assertTrue(String.valueOf(badSpan.getBody()).contains("date_to"));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private JsonNode postCreated(String path, String json) {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "POST " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private JsonNode postOk(String path, String json) {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "POST " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
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
        }
        return new HttpEntity<>(json, headers);
    }
}
