/*
 * End-to-end inbound booking loop: a booking injected on the mock OTA flows
 * through ingestion into the booking-events feed; a poller (played by this
 * test) reads it with a cursor; modification and cancellation append newer
 * revisions and adjust inventory; redeliveries and stale revisions are
 * no-ops; replay from any older cursor is stable and paged. Also proves the
 * optimistic decrement fans back out to the OTA via the outbox relay.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
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
import java.util.ArrayList;
import java.util.List;

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
class BookingLoopIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private record Content(String propertyId, String roomTypeId, String ratePlanId) {
    }

    @Test
    void bookingLifecycleFlowsThroughEventStreamAndAdjustsInventory() {
        Content content = seedContentAndChannel("Loop Hotel");
        seedAvailability(content, "2027-05-01", "2027-05-04", 5);
        // The stream is account-wide and tests share the dev account —
        // anchor every read to the cursor as it stood before this test.
        long base = latestSeq();

        // ── New booking: nights 05-01/05-02 ─────────────────────────────
        JsonNode injected = injectBooking(content, "RSV-100", 1, "new", "2027-05-01", "2027-05-03");
        assertEquals(false, injected.path("duplicate").asBoolean());
        String bookingId = injected.path("booking_id").asString();
        long cursor = injected.path("seq").asLong();

        JsonNode events = eventsAfter(base, 100);
        assertEquals(1, events.size(), "expected exactly one event: " + events);
        JsonNode event = events.get(0);
        assertEquals(cursor, event.path("seq").asLong());
        assertEquals("new", event.path("status").asString());
        assertEquals("mock", event.path("ota_name").asString());
        assertEquals("RSV-100", event.path("ota_reservation_code").asString());
        assertEquals(1, event.path("revision_seq").asInt());
        assertEquals(bookingId, event.path("booking_id").asString());
        assertEquals("Siti", event.path("customer").path("name").asString());
        assertEquals(2, event.path("rooms").get(0).path("occupancy").path("adults").asInt());

        // Optimistic decrement: booked nights drop, checkout day untouched.
        assertEquals(4, availabilityOn(content, "2027-05-01"));
        assertEquals(4, availabilityOn(content, "2027-05-02"));
        assertEquals(5, availabilityOn(content, "2027-05-03"));

        // The adjusted value fans back out to the OTA through the relay.
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .until(() -> mockOtaSawAvailability(content, "2027-05-01", 4));

        // ── Redelivery of the same revision: deduped everywhere ─────────
        JsonNode redelivered = injectBooking(content, "RSV-100", 1, "new", "2027-05-01", "2027-05-03");
        assertEquals(true, redelivered.path("duplicate").asBoolean());
        assertEquals(1, eventsAfter(base, 100).size());
        assertEquals(4, availabilityOn(content, "2027-05-01"));

        // ── Modification: moves to nights 05-02/05-03 ────────────────────
        JsonNode modified = injectBooking(content, "RSV-100", 2, "modified", "2027-05-02", "2027-05-04");
        assertEquals(false, modified.path("duplicate").asBoolean());
        assertEquals(bookingId, modified.path("booking_id").asString());
        assertEquals(5, availabilityOn(content, "2027-05-01"));
        assertEquals(4, availabilityOn(content, "2027-05-02"));
        assertEquals(4, availabilityOn(content, "2027-05-03"));

        JsonNode booking = getOk("/api/v1/bookings/" + bookingId);
        assertEquals("modified", booking.path("status").asString());
        assertEquals(2, booking.path("revision_seq").asInt());
        assertEquals("2027-05-02", booking.path("rooms").get(0).path("checkin_date").asString());

        // ── Stale revision arrives late: ignored, nothing changes ───────
        JsonNode stale = injectBooking(content, "RSV-100", 1, "new", "2027-05-01", "2027-05-03");
        assertEquals(true, stale.path("duplicate").asBoolean());
        assertEquals(2, eventsAfter(base, 100).size());
        assertEquals(5, availabilityOn(content, "2027-05-01"));

        // ── Cancellation: inventory released, third event appended ──────
        JsonNode cancelled = injectBooking(content, "RSV-100", 3, "cancelled", "2027-05-02", "2027-05-04");
        assertEquals(false, cancelled.path("duplicate").asBoolean());
        assertEquals(5, availabilityOn(content, "2027-05-02"));
        assertEquals(5, availabilityOn(content, "2027-05-03"));
        assertEquals("cancelled", getOk("/api/v1/bookings/" + bookingId).path("status").asString());

        JsonNode all = eventsAfter(base, 100);
        assertEquals(3, all.size());
        assertEquals("new", all.get(0).path("status").asString());
        assertEquals("modified", all.get(1).path("status").asString());
        assertEquals("cancelled", all.get(2).path("status").asString());
    }

    @Test
    void streamIsReplayableFromAnyCursorAndPaged() {
        Content content = seedContentAndChannel("Replay Hotel");
        seedAvailability(content, "2027-06-01", "2027-06-03", 3);
        long base = latestSeq();
        injectBooking(content, "RSV-200", 1, "new", "2027-06-01", "2027-06-02");
        injectBooking(content, "RSV-201", 1, "new", "2027-06-01", "2027-06-02");
        injectBooking(content, "RSV-200", 2, "cancelled", "2027-06-01", "2027-06-02");

        JsonNode all = eventsAfter(base, 100);
        assertEquals(3, all.size());

        // Page through with limit=1: cursors chain, order is stable.
        List<Long> paged = new ArrayList<>();
        long cursor = base;
        for (int i = 0; i < 3; i++) {
            JsonNode page = eventsAfter(cursor, 1);
            assertEquals(1, page.size());
            cursor = page.get(0).path("seq").asLong();
            paged.add(cursor);
        }
        assertEquals(0, eventsAfter(cursor, 1).size(), "stream must be drained");
        for (int i = 0; i < 3; i++) {
            assertEquals(all.get(i).path("seq").asLong(), paged.get(i));
        }

        // Replay from the middle: identical payloads, oldest-first.
        JsonNode replay = eventsAfter(paged.get(0), 100);
        assertEquals(2, replay.size());
        assertEquals(all.get(1), replay.get(0));
        assertEquals(all.get(2), replay.get(1));

        // Interleaved bookings: each keeps its own revision_seq ordering.
        assertEquals("RSV-200", all.get(0).path("ota_reservation_code").asString());
        assertEquals("RSV-201", all.get(1).path("ota_reservation_code").asString());
        assertEquals("RSV-200", all.get(2).path("ota_reservation_code").asString());
        assertEquals(2, all.get(2).path("revision_seq").asInt());
    }

    @Test
    void bookingForForeignPropertyIs404() {
        Content content = seedContentAndChannel("Isolation Hotel");
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/mock-ota/bookings", HttpMethod.POST,
                entity(bookingJson("00000000-0000-4000-8000-0000000000ff",
                        content.roomTypeId(), content.ratePlanId(),
                        "RSV-300", 1, "new", "2027-07-01", "2027-07-02")),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
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

    private JsonNode injectBooking(
            Content content, String code, int revisionSeq, String status,
            String checkin, String checkout) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/mock-ota/bookings", HttpMethod.POST,
                entity(bookingJson(content.propertyId(), content.roomTypeId(), content.ratePlanId(),
                        code, revisionSeq, status, checkin, checkout)),
                String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "POST /mock-ota/bookings → " + response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private static String bookingJson(
            String propertyId, String roomTypeId, String ratePlanId,
            String code, int revisionSeq, String status, String checkin, String checkout) {
        return """
                {"property_id":"%s","ota_reservation_code":"%s","revision_seq":%d,
                 "status":"%s",
                 "customer":{"name":"Siti","surname":"Rahma","mail":"siti@example.id",
                             "phone":"+62811111111","country":"ID"},
                 "rooms":[{"room_type_id":"%s","rate_plan_id":"%s",
                           "checkin_date":"%s","checkout_date":"%s",
                           "occupancy":{"adults":2,"children":0}}]}"""
                .formatted(propertyId, code, revisionSeq, status, roomTypeId, ratePlanId,
                        checkin, checkout);
    }

    private JsonNode eventsAfter(long after, int limit) {
        return getOk("/api/v1/booking-events?after=" + after + "&limit=" + limit);
    }

    /** Where the account-wide stream currently ends (0 when empty). */
    private long latestSeq() {
        long last = 0;
        for (JsonNode event : eventsAfter(0, 500)) {
            last = event.path("seq").asLong();
        }
        return last;
    }

    private int availabilityOn(Content content, String date) {
        JsonNode ranges = getOk("/api/v1/availability?property_id=" + content.propertyId()
                + "&date_gte=" + date + "&date_lte=" + date);
        assertEquals(1, ranges.size(), "expected one range for one day: " + ranges);
        return ranges.get(0).path("availability").asInt();
    }

    private boolean mockOtaSawAvailability(Content content, String date, int expected) {
        JsonNode pushes = getOk("/api/v1/mock-ota/pushes?property_id=" + content.propertyId());
        for (JsonNode push : pushes) {
            for (JsonNode range : push.path("availability")) {
                if (range.path("availability").asInt() == expected
                        && range.path("date_from").asString().compareTo(date) <= 0
                        && range.path("date_to").asString().compareTo(date) >= 0) {
                    return true;
                }
            }
        }
        return false;
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
