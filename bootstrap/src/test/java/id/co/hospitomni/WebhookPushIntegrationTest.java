/*
 * Signed webhook fast path, end to end against an in-JVM HTTP receiver:
 * registration returns the secret exactly once and the cursor starts at
 * the stream tip; deliveries verify by independent HMAC recomputation
 * over the captured raw bytes; a 500 endpoint accrues failures with a
 * frozen cursor and PII-free last_error, then recovers by redelivering
 * the same seqs (at-least-once); the polled feed remains the full-history
 * backstop; rotation re-keys the next delivery; pause/resume drains.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni;

import com.sun.net.httpserver.HttpServer;
import id.co.hospitomni.config.ApiKeyAuthFilter;
import id.co.hospitomni.config.DevDataSeeder;
import id.co.hospitomni.config.IdempotencyFilter;
import org.junit.jupiter.api.AfterEach;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "hospitomni.webhook.fixed-delay=PT0.2S",
                "hospitomni.webhook.backoff-base-seconds=1",
                "hospitomni.webhook.backoff-cap-seconds=2",
                "hospitomni.webhook.require-https=false",
        })
@AutoConfigureTestRestTemplate
@ActiveProfiles("local")
class WebhookPushIntegrationTest {

    private record Delivery(String timestamp, String signature, byte[] body) {
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer receiver;
    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    private final AtomicInteger respondWith = new AtomicInteger(200);

    private record Content(String propertyId, String roomTypeId, String ratePlanId) {
    }

    @BeforeEach
    void startReceiver() throws Exception {
        deliveries.clear();
        respondWith.set(200);
        receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            deliveries.add(new Delivery(
                    exchange.getRequestHeaders().getFirst("X-Hospitomni-Timestamp"),
                    exchange.getRequestHeaders().getFirst("X-Hospitomni-Signature"),
                    body));
            exchange.sendResponseHeaders(respondWith.get(), -1);
            exchange.close();
        });
        receiver.start();
    }

    @AfterEach
    void stopReceiver() {
        receiver.stop(0);
    }

    private String receiverUrl() {
        return "http://127.0.0.1:" + receiver.getAddress().getPort() + "/hook";
    }

    @Test
    void registerDeliverAndVerifySignature() throws Exception {
        Content content = seedContent("Webhook Hotel " + UUID.randomUUID());
        injectBooking(content, "RSV-BEFORE", 1);

        // Register AFTER the first booking: the cursor starts at the tip.
        JsonNode registered = postCreated("/api/v1/webhooks", """
                {"url":"%s"}""".formatted(receiverUrl()));
        String webhookId = registered.path("id").asString();
        String secret = registered.path("secret").asString();
        assertTrue(secret.startsWith("whsec_"), registered.toString());

        JsonNode listed = getOk("/api/v1/webhooks");
        assertEquals(1, listed.valueStream()
                .filter(w -> w.path("id").asString().equals(webhookId)).count());
        assertFalse(listed.toString().contains("whsec_"),
                "the secret must never appear after registration");

        injectBooking(content, "RSV-AFTER", 1);
        await().atMost(Duration.ofSeconds(10)).until(() -> !deliveries.isEmpty());
        Delivery delivery = deliveries.get(0);

        // Independent HMAC recomputation over the captured raw bytes.
        assertEquals("v1=" + hmacHex(secret, delivery.timestamp(), delivery.body()),
                delivery.signature());

        // Tip-start: only the post-registration booking is delivered.
        JsonNode events = objectMapper.readTree(delivery.body()).path("events");
        assertEquals(1, events.size(), new String(delivery.body(), StandardCharsets.UTF_8));
        assertEquals("RSV-AFTER", events.path(0).path("ota_reservation_code").asString());

        // The delivered node matches the polled feed byte-for-byte semantics.
        long seq = events.path(0).path("seq").asLong();
        JsonNode polled = getOk("/api/v1/booking-events?after=" + (seq - 1) + "&limit=1").path(0);
        assertEquals(polled, events.path(0));

        // Delivery state on the list surface.
        await().atMost(Duration.ofSeconds(5)).until(() -> findWebhook(webhookId)
                .path("last_delivered_seq").asLong() >= seq);
        JsonNode state = findWebhook(webhookId);
        assertEquals(0, state.path("pending_events").asLong());
        assertFalse(state.path("last_success_at").isNull());

        deleteWebhook(webhookId);
    }

    @Test
    void failingEndpointRetriesWithFrozenCursorThenRecovers() throws Exception {
        Content content = seedContent("Retry Hotel " + UUID.randomUUID());
        respondWith.set(500);

        JsonNode registered = postCreated("/api/v1/webhooks", """
                {"url":"%s"}""".formatted(receiverUrl()));
        String webhookId = registered.path("id").asString();
        long cursorAtStart = findWebhook(webhookId).path("last_delivered_seq").asLong();

        injectBooking(content, "RSV-RETRY", 1);
        await().atMost(Duration.ofSeconds(10)).until(() ->
                findWebhook(webhookId).path("consecutive_failures").asInt() >= 1);

        JsonNode failing = findWebhook(webhookId);
        assertTrue(failing.path("last_error").asString().contains("HTTP 500"),
                failing.toString());
        assertFalse(failing.path("last_error").asString().contains("Siti"),
                "delivery errors must never carry guest data");
        assertEquals(cursorAtStart, failing.path("last_delivered_seq").asLong(),
                "a failed delivery must not advance the cursor");
        long failedAttempts = deliveries.size();

        // Endpoint heals: the SAME events are redelivered (at-least-once).
        respondWith.set(200);
        await().atMost(Duration.ofSeconds(10)).until(() ->
                findWebhook(webhookId).path("last_delivered_seq").asLong() > cursorAtStart);
        assertTrue(deliveries.size() > failedAttempts);
        JsonNode redelivered = objectMapper
                .readTree(deliveries.get(deliveries.size() - 1).body()).path("events");
        assertEquals("RSV-RETRY",
                redelivered.path(0).path("ota_reservation_code").asString());

        // Backstop: the polled feed still replays everything from zero.
        JsonNode replay = getOk("/api/v1/booking-events?after=0&limit=500");
        assertTrue(replay.valueStream().anyMatch(e ->
                        "RSV-RETRY".equals(e.path("ota_reservation_code").asString())),
                "the cursor stream must retain all webhook-delivered events");

        deleteWebhook(webhookId);
    }

    @Test
    void rotationRekeysAndPauseResumeDrains() throws Exception {
        Content content = seedContent("Rotate Hotel " + UUID.randomUUID());
        JsonNode registered = postCreated("/api/v1/webhooks", """
                {"url":"%s"}""".formatted(receiverUrl()));
        String webhookId = registered.path("id").asString();
        String oldSecret = registered.path("secret").asString();

        String newSecret = objectMapper.readTree(rest.exchange(
                        "/api/v1/webhooks/" + webhookId + "/rotate-secret", HttpMethod.POST,
                        entity("{}"), String.class).getBody())
                .path("data").path("secret").asString();
        assertTrue(newSecret.startsWith("whsec_"));
        assertFalse(newSecret.equals(oldSecret));

        // Pause, produce an event, verify silence, resume, verify drain + new key.
        ResponseEntity<String> pause = rest.exchange(
                "/api/v1/webhooks/" + webhookId, HttpMethod.PATCH,
                entity("""
                        {"active":false}"""), String.class);
        assertEquals(HttpStatus.OK, pause.getStatusCode(), String.valueOf(pause.getBody()));

        injectBooking(content, "RSV-PAUSED", 1);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4))
                .until(deliveries::isEmpty);

        rest.exchange("/api/v1/webhooks/" + webhookId, HttpMethod.PATCH,
                entity("""
                        {"active":true}"""), String.class);
        await().atMost(Duration.ofSeconds(10)).until(() -> !deliveries.isEmpty());
        Delivery delivery = deliveries.get(0);
        assertEquals("v1=" + hmacHex(newSecret, delivery.timestamp(), delivery.body()),
                delivery.signature(), "post-rotation deliveries must sign with the new secret");

        deleteWebhook(webhookId);
    }

    @Test
    void invalidUrlIs400() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/webhooks", HttpMethod.POST,
                entity("""
                        {"url":"ftp://example.com/hook"}"""), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── fixture/helpers ─────────────────────────────────────────────────

    private static String hmacHex(String secret, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(body);
        return HexFormat.of().formatHex(mac.doFinal());
    }

    private Content seedContent(String title) throws Exception {
        String propertyId = postCreated("/api/v1/properties", """
                {"title":"%s","currency":"IDR","timezone":"Asia/Jakarta"}""".formatted(title))
                .path("id").asString();
        String roomTypeId = postCreated("/api/v1/room-types", """
                {"property_id":"%s","title":"Std","count_of_rooms":5,
                 "occ_adults":2,"occ_children":0}""".formatted(propertyId)).path("id").asString();
        String ratePlanId = postCreated("/api/v1/rate-plans", """
                {"property_id":"%s","room_type_id":"%s","title":"BAR","currency":"IDR"}"""
                .formatted(propertyId, roomTypeId)).path("id").asString();
        postCreated("/api/v1/property-channels", """
                {"property_id":"%s","ota_name":"mock"}""".formatted(propertyId));
        ResponseEntity<String> availability = rest.exchange(
                "/api/v1/availability", HttpMethod.POST,
                entity("""
                        {"values":[{"property_id":"%s","room_type_id":"%s",
                          "date_from":"2027-09-01","date_to":"2027-09-10","availability":5}]}"""
                        .formatted(propertyId, roomTypeId)), String.class);
        assertTrue(availability.getStatusCode().is2xxSuccessful());
        return new Content(propertyId, roomTypeId, ratePlanId);
    }

    private void injectBooking(Content content, String code, int revisionSeq) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/mock-ota/bookings", HttpMethod.POST,
                entity("""
                        {"property_id":"%s","ota_reservation_code":"%s","revision_seq":%d,
                         "status":"new",
                         "customer":{"name":"Siti","surname":"Rahma","mail":"siti@example.id",
                                     "phone":"+62811111111","country":"ID"},
                         "rooms":[{"room_type_id":"%s","rate_plan_id":"%s",
                                   "checkin_date":"2027-09-02","checkout_date":"2027-09-04",
                                   "occupancy":{"adults":2,"children":0}}]}"""
                        .formatted(content.propertyId(), code, revisionSeq,
                                content.roomTypeId(), content.ratePlanId())),
                String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "POST /mock-ota/bookings → " + response.getBody());
    }

    private JsonNode findWebhook(String webhookId) {
        try {
            return getOk("/api/v1/webhooks").valueStream()
                    .filter(w -> w.path("id").asString().equals(webhookId))
                    .findFirst().orElseThrow();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteWebhook(String webhookId) {
        rest.exchange("/api/v1/webhooks/" + webhookId, HttpMethod.DELETE,
                entity(null), String.class);
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
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "GET " + path + " → " + response.getBody());
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
