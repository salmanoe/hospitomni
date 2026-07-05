/*
 * Sync-status use case: the account's per-channel push health plus the
 * booking-events stream tip, optionally narrowed to one property (which is
 * tenant-checked — cross-tenant property ids read as 404, no existence
 * oracle).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.domain.model.ChannelSyncStatus;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.channel.domain.port.out.SyncStatusReader;
import id.co.hospitomni.channel.domain.port.out.SyncStatusReader.StreamStatus;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SyncStatusService {

    /** Everything /sync-status reports, gathered in one call. */
    public record SyncStatus(List<ChannelSyncStatus> channels, StreamStatus bookingEvents) {
    }

    private final SyncStatusReader reader;
    private final PropertyChannelRepository channelRepository;

    public SyncStatusService(SyncStatusReader reader, PropertyChannelRepository channelRepository) {
        this.reader = reader;
        this.channelRepository = channelRepository;
    }

    public SyncStatus status(@Nullable PropertyId propertyId) {
        AccountId account = AccountContext.current();
        if (propertyId != null && !channelRepository.propertyOwnedBy(propertyId, account)) {
            throw new ResourceNotFoundException("Property", propertyId.value());
        }
        return new SyncStatus(
                reader.channelStatuses(account, propertyId),
                reader.bookingStream(account));
    }
}
