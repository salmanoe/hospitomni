/*
 * Listens to AriChangedEvent SYNCHRONOUSLY (same thread, same transaction as
 * the ARI write) and marks dirty cells for every channel of the property —
 * paused ones included, so unpausing resumes where sync left off. This is
 * the outbox guarantee: the write and its dirty marks commit atomically.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.domain.port.out.DirtyCellStore;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.event.AriChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DirtyCellWriter {

    private final PropertyChannelRepository channelRepository;
    private final DirtyCellStore dirtyCellStore;

    public DirtyCellWriter(PropertyChannelRepository channelRepository, DirtyCellStore dirtyCellStore) {
        this.channelRepository = channelRepository;
        this.dirtyCellStore = dirtyCellStore;
    }

    @EventListener
    public void onAriChanged(AriChangedEvent event) {
        List<PropertyChannelId> channels =
                channelRepository.findChannelIdsByProperty(event.propertyId());
        if (channels.isEmpty()) {
            return; // Property not connected to any OTA yet — nothing to sync.
        }
        dirtyCellStore.mark(channels, event.unit(), event.unitId(), event.dateFrom(), event.dateTo());
    }
}
