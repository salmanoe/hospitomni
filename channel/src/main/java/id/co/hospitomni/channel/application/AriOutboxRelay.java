/*
 * The outbox relay (mirrors HospitOps's ChannelOutboxProcessor): every tick,
 * find channels with due dirty cells and fan out one worker per channel on
 * virtual threads. Failures are recorded (backoff/dead-letter) in a fresh
 * transaction after the worker's transaction rolled back.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.domain.port.out.DirtyCellStore;
import id.co.hospitomni.channel.domain.port.out.OtaRateLimitException;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.shared.PropertyChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class AriOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(AriOutboxRelay.class);

    private final RelayWorker worker;
    private final DirtyCellStore dirtyCellStore;
    private final PropertyChannelRepository channelRepository;
    private final RelayProperties properties;
    // Plain virtual-thread fan-out — StructuredTaskScope is still preview on
    // Java 25, and preview APIs stay out of production code.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AriOutboxRelay(
            RelayWorker worker,
            DirtyCellStore dirtyCellStore,
            PropertyChannelRepository channelRepository,
            RelayProperties properties) {
        this.worker = worker;
        this.dirtyCellStore = dirtyCellStore;
        this.channelRepository = channelRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${hospitomni.relay.fixed-delay:PT2S}")
    public void relayTick() {
        List<PropertyChannelId> due = dirtyCellStore.dueChannelIds();
        if (due.isEmpty()) {
            return;
        }
        List<? extends java.util.concurrent.Future<?>> futures = due.stream()
                .map(channelId -> executor.submit(() -> relayChannel(channelId)))
                .toList();
        futures.forEach(future -> {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Relay task failed unexpectedly", e);
            }
        });
    }

    private void relayChannel(PropertyChannelId channelId) {
        try {
            worker.processChannel(channelId);
        } catch (OtaRateLimitException e) {
            // Throttling is normal operation: defer without an attempt, and
            // keep last_push_error clean — nothing is wrong with the channel.
            log.debug("Rate limited on channel {} — deferring {}s",
                    channelId.value(), e.retryAfterSeconds());
            dirtyCellStore.recordRateLimited(channelId, e.retryAfterSeconds());
        } catch (Exception e) {
            log.warn("Push failed for channel {}: {}", channelId.value(), e.getMessage());
            String error = e.getMessage() == null ? e.toString() : e.getMessage();
            dirtyCellStore.recordFailure(channelId, error,
                    properties.backoffBaseSeconds(), properties.maxAttempts());
            // Surface the failure on /sync-status until the next success.
            channelRepository.recordPushError(channelId, error);
        }
    }
}
