/*
 * End-to-end proof of the doctor CLI and the /sync-status endpoint behind
 * it: a healthy, fully-synced property passes with exit 0; a rejected key,
 * an unmapped property, and a dead-lettered push each fail with exit 1.
 * max-attempts=1 so a single injected failure dead-letters immediately.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni;

import id.co.hospitomni.config.ApiKeyAuthFilter;
import id.co.hospitomni.config.DevDataSeeder;
import id.co.hospitomni.doctor.Doctor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
                "hospitomni.relay.max-attempts=1",
        })
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class DoctorIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    private record Content(String propertyId, String roomTypeId, String ratePlanId) {
    }

    private record DoctorRun(int exitCode, String output) {
    }

    @Test
    void healthySyncedPropertyPassesWithExitZero() {
        Content content = seedContentAndChannel("Doctor Healthy Hotel");
        seedAvailability(content, "2027-09-01", "2027-09-05", 4);
        awaitChannelSettled(content.propertyId(), status ->
                status.path("pending_cells").asLong() == 0
                        && !status.path("last_push_at").isNull());

        DoctorRun run = doctor("--property-id=" + content.propertyId());
        assertEquals(0, run.exitCode(), run.output());
        assertTrue(run.output().contains("[PASS] api-key"), run.output());
        assertTrue(run.output().contains("[PASS] mappings"), run.output());
        assertTrue(run.output().contains("in sync"), run.output());
        assertTrue(run.output().contains("0 failed"), run.output());
    }

    @Test
    void rejectedKeyFailsWithExitOne() {
        DoctorRun run = doctor("--api-key=homni_not_a_real_key");
        assertEquals(1, run.exitCode(), run.output());
        assertTrue(run.output().contains("API key rejected"), run.output());
    }

    @Test
    void unreachableServiceFailsWithExitOne() {
        // Nothing listens on the neighbouring port.
        DoctorRun run = doctorAt("http://localhost:" + (port == 1 ? 2 : port - 1));
        assertEquals(1, run.exitCode(), run.output());
        assertTrue(run.output().contains("cannot reach"), run.output());
    }

    @Test
    void unmappedPropertyFailsWithExitOne() {
        String propertyId = postCreated("/api/v1/properties", """
                {"title":"Doctor Unmapped Hotel","currency":"IDR","timezone":"Asia/Jakarta"}""")
                .path("id").asString();

        DoctorRun run = doctor("--property-id=" + propertyId);
        assertEquals(1, run.exitCode(), run.output());
        assertTrue(run.output().contains("no OTA channel mapped"), run.output());
    }

    @Test
    void deadLetteredPushFailsWithExitOne() {
        Content content = seedContentAndChannel("Doctor Dead Letter Hotel");
        postOk("/api/v1/mock-ota/fail-next", "{\"count\":1}");
        seedAvailability(content, "2027-10-01", "2027-10-02", 3);
        // max-attempts=1: the injected failure dead-letters on first delivery.
        awaitChannelSettled(content.propertyId(), status ->
                status.path("dead_letters").asLong() > 0);

        DoctorRun run = doctor("--property-id=" + content.propertyId());
        assertEquals(1, run.exitCode(), run.output());
        assertTrue(run.output().contains("dead-letter"), run.output());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private DoctorRun doctor(String... extraArgs) {
        String[] args = new String[extraArgs.length + 2];
        args[0] = "--base-url=http://localhost:" + port;
        args[1] = "--api-key=" + DevDataSeeder.DEV_RAW_KEY;
        System.arraycopy(extraArgs, 0, args, 2, extraArgs.length);
        return runDoctor(args);
    }

    private DoctorRun doctorAt(String baseUrl) {
        return runDoctor(new String[]{
                "--base-url=" + baseUrl, "--api-key=" + DevDataSeeder.DEV_RAW_KEY});
    }

    private static DoctorRun runDoctor(String[] args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exitCode = Doctor.run(args, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return new DoctorRun(exitCode, buffer.toString(StandardCharsets.UTF_8));
    }

    private void awaitChannelSettled(
            String propertyId, java.util.function.Predicate<JsonNode> condition) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250)).until(() -> {
            JsonNode channels = getOk("/api/v1/sync-status?property_id=" + propertyId)
                    .path("channels");
            return channels.size() == 1 && condition.test(channels.get(0));
        });
    }

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
        postCreated("/api/v1/property-channels", """
                {"property_id":"%s","ota_name":"mock"}""".formatted(propertyId));
        return new Content(propertyId, roomTypeId, ratePlanId);
    }

    private void seedAvailability(Content content, String from, String to, int value) {
        postOk("/api/v1/availability", """
                {"values":[{"property_id":"%s","room_type_id":"%s",
                  "date_from":"%s","date_to":"%s","availability":%d}]}"""
                .formatted(content.propertyId(), content.roomTypeId(), from, to, value));
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
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "POST " + path + " → " + response.getBody());
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
