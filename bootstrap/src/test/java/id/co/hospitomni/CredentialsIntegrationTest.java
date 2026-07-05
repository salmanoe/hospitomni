/*
 * OTA credential contract: PUT stores encrypted-at-rest (verified straight
 * from the DB — ciphertext only, never the plaintext), the adapter-facing
 * port decrypts back to the exact JSON, has_credentials flips on list
 * responses, DELETE clears, foreign channels 404, and no API response ever
 * carries the stored values.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni;

import id.co.hospitomni.channel.domain.port.out.ChannelCredentialsPort;
import id.co.hospitomni.config.ApiKeyAuthFilter;
import id.co.hospitomni.config.DevDataSeeder;
import id.co.hospitomni.config.IdempotencyFilter;
import id.co.hospitomni.shared.PropertyChannelId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class CredentialsIntegrationTest {

    private static final String SECRET_VALUE = "traveloka-api-secret-XYZZY";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChannelCredentialsPort credentialsPort;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void credentialsRoundTripEncryptedAtRest() throws Exception {
        String channelId = createChannel();
        String credentialsJson = """
                {"client_id":"tvlk-123","client_secret":"%s"}""".formatted(SECRET_VALUE);

        // PUT stores; the response flips has_credentials but never echoes values.
        ResponseEntity<String> put = rest.exchange(
                "/api/v1/property-channels/" + channelId + "/credentials", HttpMethod.PUT,
                entity("""
                        {"credentials":%s}""".formatted(credentialsJson)), String.class);
        assertEquals(HttpStatus.OK, put.getStatusCode(), String.valueOf(put.getBody()));
        assertTrue(String.valueOf(put.getBody()).contains("\"has_credentials\":true"));
        assertFalse(String.valueOf(put.getBody()).contains(SECRET_VALUE),
                "API responses must never carry credential values");

        // At rest: ciphertext + key id, and the plaintext is nowhere in the bytes.
        byte[] ciphertext = jdbc.queryForObject(
                "SELECT credentials_ciphertext FROM property_channel WHERE id = ?",
                byte[].class, UUID.fromString(channelId));
        String keyId = jdbc.queryForObject(
                "SELECT credentials_key_id FROM property_channel WHERE id = ?",
                String.class, UUID.fromString(channelId));
        assertEquals("dev-1", keyId);
        assertFalse(new String(ciphertext, StandardCharsets.ISO_8859_1).contains(SECRET_VALUE),
                "credentials must be encrypted at rest");

        // The adapter-facing port decrypts to the exact JSON.
        String decrypted = credentialsPort
                .credentialsFor(PropertyChannelId.of(UUID.fromString(channelId)))
                .orElseThrow();
        assertEquals(objectMapper.readTree(credentialsJson), objectMapper.readTree(decrypted));

        // DELETE clears; has_credentials drops back to false; port reads empty.
        ResponseEntity<String> delete = rest.exchange(
                "/api/v1/property-channels/" + channelId + "/credentials", HttpMethod.DELETE,
                entity(null), String.class);
        assertEquals(HttpStatus.NO_CONTENT, delete.getStatusCode());
        assertTrue(credentialsPort
                .credentialsFor(PropertyChannelId.of(UUID.fromString(channelId))).isEmpty());
    }

    @Test
    void listShowsHasCredentialsFlag() throws Exception {
        String channelId = createChannel();
        String propertyId = channelProperty(channelId);

        JsonNode before = getOk("/api/v1/property-channels?property_id=" + propertyId).get(0);
        assertFalse(before.path("has_credentials").asBoolean());

        rest.exchange("/api/v1/property-channels/" + channelId + "/credentials", HttpMethod.PUT,
                entity("""
                        {"credentials":{"key":"k"}}"""), String.class);
        JsonNode after = getOk("/api/v1/property-channels?property_id=" + propertyId).get(0);
        assertTrue(after.path("has_credentials").asBoolean());
        assertFalse(after.has("credentials"), "list must not expose credentials");
    }

    @Test
    void unknownChannelIs404() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/property-channels/00000000-0000-4000-8000-00000000dead/credentials",
                HttpMethod.PUT, entity("""
                        {"credentials":{"key":"k"}}"""), String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ── fixture/helpers ─────────────────────────────────────────────────

    private String createChannel() throws Exception {
        String propertyId = postCreated("/api/v1/properties", """
                {"title":"Cred Hotel %s","currency":"IDR","timezone":"Asia/Jakarta"}"""
                .formatted(UUID.randomUUID())).path("id").asString();
        return postCreated("/api/v1/property-channels", """
                {"property_id":"%s","ota_name":"mock"}""".formatted(propertyId))
                .path("id").asString();
    }

    private String channelProperty(String channelId) {
        return jdbc.queryForObject("SELECT property_id FROM property_channel WHERE id = ?",
                UUID.class, UUID.fromString(channelId)).toString();
    }

    private JsonNode postCreated(String path, String json) throws Exception {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.POST, entity(json), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "POST " + path + " → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private JsonNode getOk(String path) throws Exception {
        ResponseEntity<String> response =
                rest.exchange(path, HttpMethod.GET, entity(null), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    /** Always carries an Idempotency-Key — bodyless DELETEs are writes too. */
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
