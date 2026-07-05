/*
 * Store for unit ↔ OTA code mappings plus the existence lookups the
 * replace validation needs (room types / rate plans are owned by other
 * modules; reads go over SQL, same as the relay's ARI reads).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.domain.port.out;

import id.co.hospitomni.channel.domain.model.UnitMapping;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UnitMappingRepository {

    /** Replaces the channel's whole mapping set (PUT semantics). */
    void replaceAll(PropertyChannelId channelId, List<UnitMapping> mappings);

    /** All mappings for the channel, ordered unit_type then ota_code. */
    List<UnitMapping> findAll(PropertyChannelId channelId);

    /** Which of the given room-type ids exist under the property. */
    Set<UUID> existingRoomTypeIds(PropertyId propertyId, Collection<UUID> ids);

    /** Which of the given rate-plan ids exist under the property. */
    Set<UUID> existingRatePlanIds(PropertyId propertyId, Collection<UUID> ids);
}
