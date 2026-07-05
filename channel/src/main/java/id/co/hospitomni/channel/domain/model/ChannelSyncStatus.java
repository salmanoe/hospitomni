/*
 * Read model behind /sync-status: one property-channel's push health —
 * last push outcome plus live backlog/dead-letter counts. pendingCells > 0
 * with an old oldestPendingMarkedAt means the channel is drifting behind.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record ChannelSyncStatus(
        PropertyChannelId channelId,
        PropertyId propertyId,
        String otaName,
        boolean paused,
        long epoch,
        @Nullable Instant lastPushAt,
        @Nullable String lastPushError,
        long pendingCells,
        @Nullable Instant oldestPendingMarkedAt,
        long deadLetters) {

    public ChannelSyncStatus {
        Guard.notNull(channelId, "channelId");
        Guard.notNull(propertyId, "propertyId");
        Guard.notBlank(otaName, "otaName");
    }
}
