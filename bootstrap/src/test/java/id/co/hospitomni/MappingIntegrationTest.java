/*
 * Unit-mapping contract: PUT /mappings declares a channel's complete
 * unit ↔ OTA-code set and reads back identically; a second PUT fully
 * replaces the first (dropped units are unmapped); foreign or unknown
 * unit ids, duplicate ids, and duplicate OTA codes are clean 400s;
 * an unknown channel is 404.
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
class MappingIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void putThenReadBackAndReplaceSemantics() throws Exception {
        Fixture fx = createMappedFixture();

        // Full set: property + room type + rate plan.
        JsonNode put = putOk("""
                {"property_channel_id":"%s","property_code":"OTA-HTL-1",
                 "room_types":[{"id":"%s","ota_code":"OTA-RT-1"}],
                 "rate_plans":[{"id":"%s","ota_code":"OTA-RP-1"}]}"""
                .formatted(fx.channelId, fx.roomTypeId, fx.ratePlanId));
        assertEquals("OTA-HTL-1", put.path("property_code").asString());

        JsonNode back = getOk("/api/v1/mappings?property_channel_id=" + fx.channelId);
        assertEquals("OTA-HTL-1", back.path("property_code").asString());
        assertEquals(1, back.path("room_types").size());
        assertEquals(fx.roomTypeId, back.path("room_types").path(0).path("id").asString());
        assertEquals("OTA-RT-1", back.path("room_types").path(0).path("ota_code").asString());
        assertEquals("OTA-RP-1", back.path("rate_plans").path(0).path("ota_code").asString());

        // Full replace: new room-type code, rate plan dropped → unmapped.
        putOk("""
                {"property_channel_id":"%s","property_code":"OTA-HTL-1",
                 "room_types":[{"id":"%s","ota_code":"OTA-RT-CHANGED"}]}"""
                .formatted(fx.channelId, fx.roomTypeId));
        JsonNode replaced = getOk("/api/v1/mappings?property_channel_id=" + fx.channelId);
        assertEquals("OTA-RT-CHANGED",
                replaced.path("room_types").path(0).path("ota_code").asString());
        assertEquals(0, replaced.path("rate_plans").size(),
                "dropped units must be unmapped by the replace");
    }

    @Test
    void foreignRoomTypeIdIs400() throws Exception {
        Fixture fx = createMappedFixture();
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/mappings", HttpMethod.PUT,
                entity("""
                        {"property_channel_id":"%s","property_code":"OTA-HTL-X",
                         "room_types":[{"id":"%s","ota_code":"OTA-RT-X"}]}"""
                        .formatted(fx.channelId, UUID.randomUUID())),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("room_type"));
    }

    @Test
    void duplicateOtaCodeIs400() throws Exception {
        Fixture fx = createMappedFixture();
        String secondRoomType = createRoomType(fx.propertyId, "Suite " + UUID.randomUUID());
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/mappings", HttpMethod.PUT,
                entity("""
                        {"property_channel_id":"%s","property_code":"OTA-HTL-X",
                         "room_types":[{"id":"%s","ota_code":"SAME"},{"id":"%s","ota_code":"SAME"}]}"""
                        .formatted(fx.channelId, fx.roomTypeId, secondRoomType)),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("duplicate"));
    }

    @Test
    void duplicateUnitIdIs400() throws Exception {
        Fixture fx = createMappedFixture();
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/mappings", HttpMethod.PUT,
                entity("""
                        {"property_channel_id":"%s","property_code":"OTA-HTL-X",
                         "room_types":[{"id":"%s","ota_code":"A"},{"id":"%s","ota_code":"B"}]}"""
                        .formatted(fx.channelId, fx.roomTypeId, fx.roomTypeId)),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(String.valueOf(response.getBody()).contains("duplicate id"));
    }

    @Test
    void unknownChannelIs404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/mappings?property_channel_id=00000000-0000-4000-8000-00000000dead",
                HttpMethod.GET, entity(null), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ── fixture ─────────────────────────────────────────────────────────

    private record Fixture(String propertyId, String roomTypeId, String ratePlanId, String channelId) {
    }

    private Fixture createMappedFixture() throws Exception {
        String propertyId = postOk("/api/v1/properties", """
                {"title":"Mapping Hotel %s","currency":"IDR","timezone":"Asia/Jakarta"}"""
                .formatted(UUID.randomUUID())).path("id").asString();
        String roomTypeId = createRoomType(propertyId, "Deluxe");
        String ratePlanId = postOk("/api/v1/rate-plans", """
                {"property_id":"%s","room_type_id":"%s","title":"BAR","currency":"IDR"}"""
                .formatted(propertyId, roomTypeId)).path("id").asString();
        String channelId = postOk("/api/v1/property-channels", """
                {"property_id":"%s","ota_name":"mock"}""".formatted(propertyId))
                .path("id").asString();
        return new Fixture(propertyId, roomTypeId, ratePlanId, channelId);
    }

    private String createRoomType(String propertyId, String title) throws Exception {
        return postOk("/api/v1/room-types", """
                {"property_id":"%s","title":"%s","count_of_rooms":5,
                 "occ_adults":2,"occ_children":0}""".formatted(propertyId, title))
                .path("id").asString();
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private JsonNode postOk(String path, String json) throws Exception {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "POST " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private JsonNode putOk(String json) throws Exception {
        ResponseEntity<String> response =
                rest.exchange("/api/v1/mappings", HttpMethod.PUT, entity(json), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "PUT /api/v1/mappings → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
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
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
        }
        return new HttpEntity<>(json, headers);
    }
}
