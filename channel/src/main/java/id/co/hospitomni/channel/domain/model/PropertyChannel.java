/*
 * One property's connection to one OTA. paused is the per-channel kill
 * switch honored by the relay; epoch is the fencing generation stamped on
 * every push (a full refresh bumps it so stale in-flight deltas can't land).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;

public record PropertyChannel(
        PropertyChannelId id,
        PropertyId propertyId,
        String otaName,
        boolean paused,
        long epoch,
        boolean hasCredentials) {

    public PropertyChannel {
        Guard.notNull(id, "id");
        Guard.notNull(propertyId, "propertyId");
        Guard.notBlank(otaName, "otaName");
        Guard.isTrue(epoch >= 0, "epoch must be >= 0");
    }
}
