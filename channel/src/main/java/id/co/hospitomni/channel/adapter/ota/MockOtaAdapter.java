/*
 * Simulates an OTA: accepts ARI pushes and stores them in memory so a
 * readback can verify receipt, with hooks for the failure modes real OTAs
 * exhibit — hard failures (fail-next → retry/backoff/dead-letter),
 * 429-shaped throttling (rate-limit-next → defer without an attempt), and
 * slow responses (latency). The inbound half of the simulation — booking
 * injection — lives on MockOtaController and feeds the booking module's
 * ingestion use case.
 *
 * Epoch fencing is enforced here the way our own delivery layer must for
 * real OTAs: a push whose epoch is older than the highest already seen for
 * the property is dropped, so a stale in-flight delta can't land after a
 * reconciler-triggered full refresh. fetchAri folds the accepted pushes in
 * arrival order (last write wins) — the "observed state" the reconciler
 * diffs against.
 *
 * @author Salman
 * @version 1.3
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.adapter.ota;

import id.co.hospitomni.channel.domain.model.AriPush;
import id.co.hospitomni.channel.domain.model.OtaAriState;
import id.co.hospitomni.channel.domain.model.RestrictionValues;
import id.co.hospitomni.channel.domain.port.out.OtaAdapterPort;
import id.co.hospitomni.channel.domain.port.out.OtaPushException;
import id.co.hospitomni.channel.domain.port.out.OtaRateLimitException;
import id.co.hospitomni.shared.PropertyId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MockOtaAdapter implements OtaAdapterPort {

    public static final String OTA_NAME = "mock";

    private static final Logger log = LoggerFactory.getLogger(MockOtaAdapter.class);

    private final ConcurrentLinkedQueue<AriPush> received = new ConcurrentLinkedQueue<>();
    private final Map<PropertyId, Long> maxEpochSeen = new ConcurrentHashMap<>();
    private final AtomicInteger failNext = new AtomicInteger();
    private final AtomicInteger rateLimitNext = new AtomicInteger();
    private final AtomicLong rateLimitRetryAfterSeconds = new AtomicLong();
    private final AtomicLong latencyMillis = new AtomicLong();

    @Override
    public String otaName() {
        return OTA_NAME;
    }

    @Override
    public void pushAri(AriPush push) {
        simulateLatency();
        if (rateLimitNext.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new OtaRateLimitException(rateLimitRetryAfterSeconds.get());
        }
        if (failNext.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new OtaPushException("Injected mock failure");
        }
        long fence = maxEpochSeen.merge(push.propertyId(), push.epoch(), Math::max);
        if (push.epoch() < fence) {
            log.info("Fenced stale push for property {} (epoch {} < {})",
                    push.propertyId().value(), push.epoch(), fence);
            return;
        }
        received.add(push);
    }

    private void simulateLatency() {
        long millis = latencyMillis.get();
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis); // Virtual threads: a parked push costs nothing.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OtaPushException("Interrupted while simulating latency", e);
        }
    }

    @Override
    public OtaAriState fetchAri(PropertyId propertyId, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, Map<LocalDate, Integer>> availability = new HashMap<>();
        Map<UUID, Map<LocalDate, RestrictionValues>> restrictions = new HashMap<>();
        for (AriPush push : pushesFor(propertyId)) {
            for (AriPush.AvailabilityRange range : push.availability()) {
                eachNightInWindow(range.dateFrom(), range.dateTo(), dateFrom, dateTo, date ->
                        availability.computeIfAbsent(range.roomTypeId(), k -> new HashMap<>())
                                .put(date, range.availability()));
            }
            for (AriPush.RestrictionRange range : push.restrictions()) {
                eachNightInWindow(range.dateFrom(), range.dateTo(), dateFrom, dateTo, date ->
                        restrictions.computeIfAbsent(range.ratePlanId(), k -> new HashMap<>())
                                .put(date, new RestrictionValues(
                                        range.rate(), range.minStay(), range.maxStay(),
                                        range.closedToArrival(), range.closedToDeparture(),
                                        range.stopSell())));
            }
        }
        return new OtaAriState(availability, restrictions);
    }

    private static void eachNightInWindow(
            LocalDate from, LocalDate to, LocalDate windowFrom, LocalDate windowTo,
            java.util.function.Consumer<LocalDate> apply) {
        LocalDate start = from.isBefore(windowFrom) ? windowFrom : from;
        LocalDate end = to.isAfter(windowTo) ? windowTo : to;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            apply.accept(date);
        }
    }

    /** Readback — what this "OTA" has accepted for one property. */
    public List<AriPush> pushesFor(PropertyId propertyId) {
        return received.stream()
                .filter(push -> push.propertyId().equals(propertyId))
                .toList();
    }

    /** Test hook: the next {@code count} pushes throw. */
    public void failNext(int count) {
        failNext.set(count);
    }

    /** Test hook: the next {@code count} pushes answer 429-shaped throttling. */
    public void rateLimitNext(int count, long retryAfterSeconds) {
        rateLimitRetryAfterSeconds.set(retryAfterSeconds);
        rateLimitNext.set(count);
    }

    /** Test hook: every push takes this long — a slow OTA, not a broken one. */
    public void latency(long millis) {
        latencyMillis.set(millis);
    }

    public void clear() {
        received.clear();
        maxEpochSeen.clear();
        failNext.set(0);
        rateLimitNext.set(0);
        rateLimitRetryAfterSeconds.set(0);
        latencyMillis.set(0);
    }
}
