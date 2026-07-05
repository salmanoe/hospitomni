/*
 * Scheduled drift sweep: every tick, reconcile each unpaused channel over
 * the configured horizon from today. One channel's failure (OTA down,
 * adapter error) is logged and skipped — the next tick retries; the rest
 * of the fleet still gets checked.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;
    private final PropertyChannelRepository channelRepository;
    private final ReconciliationProperties properties;

    public ReconciliationScheduler(
            ReconciliationService reconciliationService,
            PropertyChannelRepository channelRepository,
            ReconciliationProperties properties) {
        this.reconciliationService = reconciliationService;
        this.channelRepository = channelRepository;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${hospitomni.reconciliation.fixed-delay:PT6H}",
            initialDelayString = "${hospitomni.reconciliation.initial-delay:PT10M}")
    public void reconcileTick() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(properties.horizonDays());
        for (PropertyChannel channel : channelRepository.findAllUnpaused()) {
            try {
                var result = reconciliationService.reconcile(channel, from, to);
                if (result.run().driftCount() > 0) {
                    log.warn("Reconciliation found {} drifted night(s) on {} for property {} — "
                                    + "full refresh fenced at epoch {}",
                            result.run().driftCount(), channel.otaName(),
                            channel.propertyId().value(), result.run().epoch());
                }
            } catch (Exception e) {
                log.warn("Reconciliation failed for channel {} ({}): {}",
                        channel.id().value(), channel.otaName(), e.getMessage());
            }
        }
    }
}
