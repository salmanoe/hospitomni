/*
 * Read port behind /sync-status: per-channel push health and the booking
 * event stream's tip. The stream tip is read via SQL over the booking
 * module's table — schema-level coupling accepted on this read-only path,
 * same rationale as AriValueReader.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.domain.port.out;

import id.co.hospitomni.channel.domain.model.ChannelSyncStatus;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public interface SyncStatusReader {

    /** All the account's channels, optionally narrowed to one property. */
    List<ChannelSyncStatus> channelStatuses(AccountId accountId, @Nullable PropertyId propertyId);

    /** Where the account's booking-events stream currently ends. */
    StreamStatus bookingStream(AccountId accountId);

    /** tipSeq is 0 and lastEventAt null while the stream is empty. */
    record StreamStatus(long tipSeq, @Nullable Instant lastEventAt) {
    }
}
