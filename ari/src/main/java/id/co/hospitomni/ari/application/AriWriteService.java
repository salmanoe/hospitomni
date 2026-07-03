/*
 * Bulk ARI writes: validate every referenced property/room-type/rate-plan
 * against the bound account, expand spans to day-cells, batch-upsert.
 * Cross-tenant references are a 404 (no existence oracle).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.application;

import id.co.hospitomni.ari.application.command.AvailabilityValueCommand;
import id.co.hospitomni.ari.application.command.RestrictionValueCommand;
import id.co.hospitomni.ari.domain.model.AvailabilityCell;
import id.co.hospitomni.ari.domain.model.RestrictionCell;
import id.co.hospitomni.ari.domain.port.out.AvailabilityStore;
import id.co.hospitomni.ari.domain.port.out.ContentCatalog;
import id.co.hospitomni.ari.domain.port.out.RestrictionStore;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.event.AriChangedEvent;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AriWriteService {

    private final AvailabilityStore availabilityStore;
    private final RestrictionStore restrictionStore;
    private final ContentCatalog contentCatalog;
    private final ApplicationEventPublisher eventPublisher;

    public AriWriteService(
            AvailabilityStore availabilityStore,
            RestrictionStore restrictionStore,
            ContentCatalog contentCatalog,
            ApplicationEventPublisher eventPublisher) {
        this.availabilityStore = availabilityStore;
        this.restrictionStore = restrictionStore;
        this.contentCatalog = contentCatalog;
        this.eventPublisher = eventPublisher;
    }

    /** Returns the number of day-cells written. */
    @Transactional
    public int applyAvailability(List<AvailabilityValueCommand> values) {
        Guard.isTrue(!values.isEmpty(), "values must not be empty");
        AccountId account = AccountContext.current();
        Set<PropertyId> checkedProperties = new HashSet<>();
        Set<String> checkedPairs = new HashSet<>();
        for (AvailabilityValueCommand value : values) {
            requireOwnedProperty(value.propertyId(), account, checkedProperties);
            if (checkedPairs.add(value.roomTypeId().value() + "@" + value.propertyId().value())
                    && !contentCatalog.roomTypeInProperty(value.roomTypeId(), value.propertyId())) {
                throw new ResourceNotFoundException("RoomType", value.roomTypeId().value());
            }
        }

        List<AvailabilityCell> cells = values.stream()
                .flatMap(value -> value.span().days().stream()
                        .map(day -> new AvailabilityCell(
                                value.propertyId(), value.roomTypeId(), day, value.availability())))
                .toList();
        availabilityStore.upsert(cells);
        // Same-transaction events: the channel module marks dirty outbox
        // cells atomically with this write.
        values.forEach(value -> eventPublisher.publishEvent(new AriChangedEvent(
                value.propertyId(), AriChangedEvent.Unit.AVAILABILITY,
                value.roomTypeId().value(), value.span().from(), value.span().to())));
        return cells.size();
    }

    /** Returns the number of day-cells written (merged per field). */
    @Transactional
    public int applyRestrictions(List<RestrictionValueCommand> values) {
        Guard.isTrue(!values.isEmpty(), "values must not be empty");
        AccountId account = AccountContext.current();
        Set<PropertyId> checkedProperties = new HashSet<>();
        Set<String> checkedPairs = new HashSet<>();
        for (RestrictionValueCommand value : values) {
            Guard.isTrue(!value.fields().isEmpty(),
                    "each restrictions value must carry at least one field");
            requireOwnedProperty(value.propertyId(), account, checkedProperties);
            if (checkedPairs.add(value.ratePlanId().value() + "@" + value.propertyId().value())
                    && !contentCatalog.ratePlanInProperty(value.ratePlanId(), value.propertyId())) {
                throw new ResourceNotFoundException("RatePlan", value.ratePlanId().value());
            }
        }

        List<RestrictionCell> cells = values.stream()
                .flatMap(value -> value.span().days().stream()
                        .map(day -> new RestrictionCell(
                                value.propertyId(), value.ratePlanId(), day, value.fields())))
                .toList();
        restrictionStore.upsertMerge(cells);
        values.forEach(value -> eventPublisher.publishEvent(new AriChangedEvent(
                value.propertyId(), AriChangedEvent.Unit.RESTRICTION,
                value.ratePlanId().value(), value.span().from(), value.span().to())));
        return cells.size();
    }

    private void requireOwnedProperty(
            PropertyId propertyId, AccountId account, Set<PropertyId> alreadyChecked) {
        if (alreadyChecked.add(propertyId) && !contentCatalog.propertyOwnedBy(propertyId, account)) {
            throw new ResourceNotFoundException("Property", propertyId.value());
        }
    }
}
