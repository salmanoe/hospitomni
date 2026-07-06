/*
 * The webhook delivery relay (mirrors the ARI outbox relay): every tick,
 * find due subscriptions and fan out one worker per subscription on
 * virtual threads. Failures are recorded (capped exponential backoff) in
 * a fresh transaction after the worker's rolled back. No dead-letter and
 * no auto-disable: the stream is replayable, so a dead endpoint just
 * retries at the cap until an operator pauses or deletes it.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.application;

import id.co.hospitomni.booking.domain.port.out.WebhookDeliveryException;
import id.co.hospitomni.booking.domain.port.out.WebhookSubscriptionStore;
import id.co.hospitomni.shared.WebhookId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class WebhookRelay {

    private static final Logger log = LoggerFactory.getLogger(WebhookRelay.class);

    private final WebhookDeliveryWorker worker;
    private final WebhookSubscriptionStore store;
    private final WebhookProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public WebhookRelay(
            WebhookDeliveryWorker worker,
            WebhookSubscriptionStore store,
            WebhookProperties properties) {
        this.worker = worker;
        this.store = store;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${hospitomni.webhook.fixed-delay:PT1S}")
    public void deliveryTick() {
        List<WebhookId> due = store.dueSubscriptionIds();
        if (due.isEmpty()) {
            return;
        }
        List<? extends Future<?>> futures = due.stream()
                .map(id -> executor.submit(() -> deliverOne(id)))
                .toList();
        futures.forEach(future -> {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Webhook delivery task failed unexpectedly", e);
            }
        });
    }

    private void deliverOne(WebhookId id) {
        try {
            worker.deliver(id);
        } catch (Exception e) {
            // Delivery bodies carry guest data — only status/class reaches logs
            // and the last_error column, never payloads.
            String error = e instanceof WebhookDeliveryException
                    ? String.valueOf(e.getMessage())
                    : e.getClass().getSimpleName();
            log.warn("Webhook delivery failed for subscription {}: {}", id.value(), error);
            store.recordFailure(id, error,
                    properties.backoffBaseSeconds(), properties.backoffCapSeconds());
        }
    }
}
