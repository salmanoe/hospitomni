/*
 * One completed reconciliation: the window diffed, how many nights
 * drifted, whether a full refresh was fenced in, and the channel epoch
 * after the run. sampleDrift is a capped JSON rendering of drifted
 * cells for ops eyeballs (ARI numbers only, never guest data).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyChannelId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationRun(
        UUID id,
        PropertyChannelId channelId,
        LocalDate dateFrom,
        LocalDate dateTo,
        int driftCount,
        boolean refreshed,
        long epoch,
        @Nullable String sampleDrift,
        Instant createdAt) {

    public ReconciliationRun {
        Guard.notNull(id, "id");
        Guard.notNull(channelId, "channelId");
        Guard.notNull(dateFrom, "dateFrom");
        Guard.notNull(dateTo, "dateTo");
        Guard.isTrue(driftCount >= 0, "driftCount must be >= 0");
    }
}
