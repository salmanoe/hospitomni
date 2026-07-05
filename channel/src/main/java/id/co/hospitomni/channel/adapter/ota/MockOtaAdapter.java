/*
 * Simulates an OTA: accepts ARI pushes and stores them in memory so a
 * readback can verify receipt, with a fail-next hook to exercise the
 * relay's retry/backoff path. The inbound half of the simulation — booking
 * injection — lives on MockOtaController and feeds the booking module's
 * ingestion use case.
 *
 * @author Salman
 * @version 1.1
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.adapter.ota;

import id.co.hospitomni.channel.domain.model.AriPush;
import id.co.hospitomni.channel.domain.port.out.OtaAdapterPort;
import id.co.hospitomni.channel.domain.port.out.OtaPushException;
import id.co.hospitomni.shared.PropertyId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockOtaAdapter implements OtaAdapterPort {

    public static final String OTA_NAME = "mock";

    private final ConcurrentLinkedQueue<AriPush> received = new ConcurrentLinkedQueue<>();
    private final AtomicInteger failNext = new AtomicInteger();

    @Override
    public String otaName() {
        return OTA_NAME;
    }

    @Override
    public void pushAri(AriPush push) {
        if (failNext.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new OtaPushException("Injected mock failure");
        }
        received.add(push);
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

    public void clear() {
        received.clear();
        failNext.set(0);
    }
}
