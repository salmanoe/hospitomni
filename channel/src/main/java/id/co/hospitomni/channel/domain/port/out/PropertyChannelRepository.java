/*
 * Persistence port for property-channel mappings.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.port.out;

import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;

import java.util.List;
import java.util.Optional;

public interface PropertyChannelRepository {

    PropertyChannel create(PropertyId propertyId, String otaName);

    Optional<PropertyChannel> findById(PropertyChannelId id);

    List<PropertyChannel> findAllByProperty(PropertyId propertyId);

    /** All channel ids of a property, paused included (relay skips paused). */
    List<PropertyChannelId> findChannelIdsByProperty(PropertyId propertyId);

    void setPaused(PropertyChannelId id, boolean paused);

    /** Successful push unit: stamp last_push_at, clear any previous error. */
    void recordPushSuccess(PropertyChannelId id);

    /** Failed push unit: record the message; last_push_at stays as it was. */
    void recordPushError(PropertyChannelId id, String error);

    /** Tenant guard — channel web endpoints validate through this. */
    boolean propertyOwnedBy(PropertyId propertyId, AccountId accountId);

    /** All unpaused channels — the scheduled reconciler's work list. */
    List<PropertyChannel> findAllUnpaused();

    /**
     * Fences a full refresh: increments the channel epoch and returns the
     * new value. Pushes built before the bump carry the old epoch and are
     * dropped at delivery.
     */
    long bumpEpoch(PropertyChannelId id);

    /** Stamp the reconciliation outcome shown on /sync-status. */
    void recordReconciliation(PropertyChannelId id, int driftCount);
}
