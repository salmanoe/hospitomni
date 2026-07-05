/*
 * Property-channel management: connect a property to an OTA, pause/resume
 * (the relay kill switch), and set/clear the channel's OTA credentials
 * (encrypted at rest by the port; plaintext never returned or logged).
 * Tenant-scoped like every north-side use case.
 *
 * @author Salman
 * @version 1.1
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.adapter.ota.OtaAdapterRegistry;
import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.channel.domain.port.out.ChannelCredentialsPort;
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
    private final ChannelCredentialsPort credentialsPort;

    public PropertyChannelService(
            PropertyChannelRepository channelRepository,
            OtaAdapterRegistry adapterRegistry,
            ChannelCredentialsPort credentialsPort) {
        this.channelRepository = channelRepository;
        this.adapterRegistry = adapterRegistry;
        this.credentialsPort = credentialsPort;
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
        PropertyChannel channel = requireOwnedChannel(id);
        channelRepository.setPaused(id, paused);
        return new PropertyChannel(channel.id(), channel.propertyId(), channel.otaName(),
                paused, channel.epoch(), channel.hasCredentials());
    }

    @Transactional
    public PropertyChannel setCredentials(PropertyChannelId id, String credentialsJson) {
        PropertyChannel channel = requireOwnedChannel(id);
        credentialsPort.store(id, credentialsJson);
        return new PropertyChannel(channel.id(), channel.propertyId(), channel.otaName(),
                channel.paused(), channel.epoch(), true);
    }

    @Transactional
    public void clearCredentials(PropertyChannelId id) {
        requireOwnedChannel(id);
        credentialsPort.clear(id);
    }

    private PropertyChannel requireOwnedChannel(PropertyChannelId id) {
        PropertyChannel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PropertyChannel", id.value()));
        requireOwnedProperty(channel.propertyId());
        return channel;
    }

    private void requireOwnedProperty(PropertyId propertyId) {
        if (!channelRepository.propertyOwnedBy(propertyId, AccountContext.current())) {
            throw new ResourceNotFoundException("Property", propertyId.value());
        }
    }
}
