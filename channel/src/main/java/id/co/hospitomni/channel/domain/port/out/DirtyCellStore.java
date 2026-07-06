/*
 * The dirty-cell outbox. mark() runs inside the ARI write transaction;
 * claimDue() locks rows FOR UPDATE SKIP LOCKED inside the relay worker's
 * transaction so parallel relays never double-push.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.port.out;

import id.co.hospitomni.channel.domain.model.DirtyCell;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.event.AriChangedEvent;

import java.time.LocalDate;
import java.util.List;

public interface DirtyCellStore {

    void mark(List<PropertyChannelId> channelIds, AriChangedEvent.Unit unit,
              java.util.UUID unitId, LocalDate dateFrom, LocalDate dateTo);

    /** Channels with due cells whose mapping is not paused. */
    List<PropertyChannelId> dueChannelIds();

    /** Locks and returns this channel's due cells (SKIP LOCKED). */
    List<DirtyCell> claimDue(PropertyChannelId channelId);

    void delete(List<DirtyCell> cells);

    /**
     * After a failed push: bump attempts + exponential backoff on the
     * channel's due cells; rows at/over maxAttempts move to ari_dead_letter.
     */
    void recordFailure(PropertyChannelId channelId, String error,
                       long backoffBaseSeconds, int maxAttempts);

    /**
     * After a rate-limited push: defer the channel's due cells by the OTA's
     * retry-after WITHOUT bumping attempts — throttling is normal operation,
     * never a step toward the dead letter.
     */
    void recordRateLimited(PropertyChannelId channelId, long retryAfterSeconds);
}
