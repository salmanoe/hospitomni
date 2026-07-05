/*
 * Content CRUD with HospitOmni-assigned UUIDs,
 * verified by readback equality — create property → room type → rate plan,
 * read each back and compare. Runs against Testcontainers Postgres 18 with
 * the local-profile seeded key.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni;

// Jackson 3 (Boot 4): tools.jackson, not com.fasterxml.
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class ContentSyncIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contentCrudRoundTripReadsBackMatching() throws Exception {
        // Property: create → read back matching.
        JsonNode property = postOk("/api/v1/properties", """
                {"title":"Roundtrip Hotel","currency":"IDR","timezone":"Asia/Jakarta"}""");
        String propertyId = property.path("id").asString();
        assertFalse(propertyId.isBlank(), "HospitOmni must assign the property UUID");

        JsonNode propertyBack = getOk("/api/v1/properties/" + propertyId);
        assertEquals("Roundtrip Hotel", propertyBack.path("title").asString());
        assertEquals("IDR", propertyBack.path("currency").asString());
        assertEquals("Asia/Jakarta", propertyBack.path("timezone").asString());

        // Room type under it.
        JsonNode roomType = postOk("/api/v1/room-types", """
                {"property_id":"%s","title":"Deluxe","count_of_rooms":10,
                 "occ_adults":2,"occ_children":1}""".formatted(propertyId));
        String roomTypeId = roomType.path("id").asString();

        JsonNode roomTypeBack = getOk("/api/v1/room-types/" + roomTypeId);
        assertEquals("Deluxe", roomTypeBack.path("title").asString());
        assertEquals(10, roomTypeBack.path("count_of_rooms").asInt());
        assertEquals(propertyId, roomTypeBack.path("property_id").asString());

        // Rate plan on the room type.
        JsonNode ratePlan = postOk("/api/v1/rate-plans", """
                {"property_id":"%s","room_type_id":"%s","title":"BAR","currency":"IDR"}"""
                .formatted(propertyId, roomTypeId));
        String ratePlanId = ratePlan.path("id").asString();

        JsonNode ratePlanBack = getOk("/api/v1/rate-plans/" + ratePlanId);
        assertEquals("BAR", ratePlanBack.path("title").asString());
        assertEquals(roomTypeId, ratePlanBack.path("room_type_id").asString());

        // Update round-trips too.
        exchangeOk(HttpMethod.PUT, "/api/v1/room-types/" + roomTypeId, """
                {"property_id":"%s","title":"Deluxe Twin","count_of_rooms":12,
                 "occ_adults":2,"occ_children":2}""".formatted(propertyId));
        assertEquals("Deluxe Twin", getOk("/api/v1/room-types/" + roomTypeId).path("title").asString());

        // Lists filtered by property_id contain what we created.
        JsonNode roomTypes = getOk("/api/v1/room-types?property_id=" + propertyId);
        assertEquals(1, roomTypes.size());
        JsonNode ratePlans = getOk("/api/v1/rate-plans?property_id=" + propertyId);
        assertEquals(1, ratePlans.size());
    }

    @Test
    void unknownPropertyIs404ProblemDetail() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/properties/00000000-0000-4000-8000-00000000dead",
                HttpMethod.GET, entity(null), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("\"status\":404"));
    }

    @Test
    void validationFailureIs400ProblemDetailNot401() {
        // Regression pin: MethodArgumentNotValidException used to re-dispatch
        // to the protected /error route and surface as a misleading 401.
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/properties", HttpMethod.POST,
                entity("""
                        {"title":"","currency":"IDR","timezone":"Asia/Jakarta"}"""),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("title"));
    }

    @Test
    void openApiSpecIsPublishedAndCoversContentEndpoints() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode spec = objectMapper.readTree(response.getBody());
        assertTrue(spec.path("paths").has("/api/v1/properties"));
        assertTrue(spec.path("paths").has("/api/v1/room-types"));
        assertTrue(spec.path("paths").has("/api/v1/rate-plans"));
        assertTrue(spec.path("components").path("securitySchemes").has("userApiKey"));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private JsonNode postOk(String path, String json) throws Exception {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "POST " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private JsonNode exchangeOk(HttpMethod method, String path, String json) throws Exception {
        ResponseEntity<String> response = rest.exchange(path, method, entity(json), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                method + " " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private JsonNode getOk(String path) throws Exception {
        return exchangeOk(HttpMethod.GET, path, null);
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
