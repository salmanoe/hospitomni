/*
 * Property-channel management: connect a property to an OTA, pause/resume
 * (the relay kill switch). Tenant-scoped like every north-side use case.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.adapter.ota.OtaAdapterRegistry;
import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PropertyChannelService {

    private final PropertyChannelRepository channelRepository;
    private final OtaAdapterRegistry adapterRegistry;

    public PropertyChannelService(
            PropertyChannelRepository channelRepository, OtaAdapterRegistry adapterRegistry) {
        this.channelRepository = channelRepository;
        this.adapterRegistry = adapterRegistry;
    }

    @Transactional
    public PropertyChannel connect(PropertyId propertyId, String otaName) {
        requireOwnedProperty(propertyId);
        Guard.isTrue(adapterRegistry.knows(otaName), "unknown OTA adapter: " + otaName);
        return channelRepository.create(propertyId, otaName);
    }

    @Transactional(readOnly = true)
    public List<PropertyChannel> list(PropertyId propertyId) {
        requireOwnedProperty(propertyId);
        return channelRepository.findAllByProperty(propertyId);
    }

    @Transactional
    public PropertyChannel setPaused(PropertyChannelId id, boolean paused) {
        PropertyChannel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PropertyChannel", id.value()));
        requireOwnedProperty(channel.propertyId());
        channelRepository.setPaused(id, paused);
        return new PropertyChannel(
                channel.id(), channel.propertyId(), channel.otaName(), paused, channel.epoch());
    }

    private void requireOwnedProperty(PropertyId propertyId) {
        if (!channelRepository.propertyOwnedBy(propertyId, AccountContext.current())) {
            throw new ResourceNotFoundException("Property", propertyId.value());
        }
    }
}
