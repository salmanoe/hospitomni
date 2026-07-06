/*
 * One transactional unit of webhook delivery: claim the subscription row
 * (FOR UPDATE SKIP LOCKED), read the events past its cursor, serialize the
 * batch ONCE to bytes, sign those exact bytes, POST, and advance the
 * cursor — 2xx and cursor commit together or not at all, so the contract
 * is at-least-once and consumers dedupe by seq. Any failure rolls the
 * unit back; the relay records backoff in a fresh transaction.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.application;

import id.co.hospitomni.booking.domain.port.out.BookingEventStream;
import id.co.hospitomni.booking.domain.port.out.BookingEventStream.StoredBookingEvent;
import id.co.hospitomni.booking.domain.port.out.WebhookDeliveryException;
import id.co.hospitomni.booking.domain.port.out.WebhookDeliveryPort;
import id.co.hospitomni.booking.domain.port.out.WebhookSubscriptionStore;
import id.co.hospitomni.booking.domain.port.out.WebhookSubscriptionStore.ClaimedSubscription;
import id.co.hospitomni.shared.WebhookId;
import id.co.hospitomni.shared.crypto.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

@Component
public class WebhookDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);

    private final WebhookSubscriptionStore store;
    private final BookingEventStream eventStream;
    private final WebhookDeliveryPort deliveryPort;
    private final SecretCipher cipher;
    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;

    public WebhookDeliveryWorker(
            WebhookSubscriptionStore store,
            BookingEventStream eventStream,
            WebhookDeliveryPort deliveryPort,
            SecretCipher cipher,
            WebhookProperties properties,
            ObjectMapper objectMapper) {
        this.store = store;
        this.eventStream = eventStream;
        this.deliveryPort = deliveryPort;
        this.cipher = cipher;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void deliver(WebhookId id) {
        ClaimedSubscription claimed = store.claim(id).orElse(null);
        if (claimed == null || !claimed.subscription().active()) {
            return; // Another instance holds it, or it was deleted/paused since the tick.
        }
        var subscription = claimed.subscription();
        List<StoredBookingEvent> events = eventStream.readAfter(
                subscription.accountId(), subscription.lastDeliveredSeq(), properties.batchLimit());
        if (events.isEmpty()) {
            return;
        }

        byte[] body = batchBytes(events);
        String secret = cipher.decrypt(claimed.secretCiphertext(), claimed.secretKeyId());
        long timestamp = Instant.now().getEpochSecond();
        String signature = WebhookSigner.signature(secret, timestamp, body);

        int status = deliveryPort.post(subscription.url(), timestamp, signature, body);
        if (status < 200 || status >= 300) {
            throw new WebhookDeliveryException("HTTP " + status + " from webhook endpoint");
        }
        store.recordSuccess(id, events.getLast().seq());
        log.debug("Delivered {} booking event(s) to webhook {} (cursor → {})",
                events.size(), id.value(), events.getLast().seq());
    }

    /** Same node shape as the polled feed: seq first, then the stored snapshot. */
    private byte[] batchBytes(List<StoredBookingEvent> events) {
        ArrayNode array = objectMapper.createArrayNode();
        for (StoredBookingEvent event : events) {
            ObjectNode node = array.addObject();
            node.put("seq", event.seq());
            node.setAll((ObjectNode) objectMapper.readTree(event.payloadJson()));
        }
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.set("events", array);
        return objectMapper.writeValueAsBytes(envelope);
    }
}
