/*
 * RatePlanRepository port implementation — upsert semantics like room types.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.infrastructure.persistence;

import id.co.hospitomni.property.domain.model.RatePlan;
import id.co.hospitomni.property.domain.port.out.RatePlanRepository;
import id.co.hospitomni.property.infrastructure.persistence.entity.RatePlanJpaEntity;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.List;
import java.util.Optional;

@Repository
public class RatePlanRepositoryAdapter implements RatePlanRepository {

    private final RatePlanJpaRepository jpa;

    public RatePlanRepositoryAdapter(RatePlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RatePlan save(RatePlan ratePlan) {
        RatePlanJpaEntity entity = jpa.findById(ratePlan.id().value())
                .map(existing -> {
                    existing.updateContent(ratePlan.roomTypeId().value(), ratePlan.title(),
                            ratePlan.currency().getCurrencyCode());
                    return existing;
                })
                .orElseGet(() -> new RatePlanJpaEntity(
                        ratePlan.id().value(), ratePlan.propertyId().value(),
                        ratePlan.roomTypeId().value(), ratePlan.title(),
                        ratePlan.currency().getCurrencyCode()));
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<RatePlan> findById(RatePlanId id) {
        return jpa.findById(id.value()).map(RatePlanRepositoryAdapter::toDomain);
    }

    @Override
    public List<RatePlan> findAllByProperty(PropertyId propertyId) {
        return jpa.findAllByPropertyIdOrderByTitle(propertyId.value()).stream()
                .map(RatePlanRepositoryAdapter::toDomain)
                .toList();
    }

    private static RatePlan toDomain(RatePlanJpaEntity entity) {
        return new RatePlan(
                RatePlanId.of(entity.id()),
                PropertyId.of(entity.propertyId()),
                RoomTypeId.of(entity.roomTypeId()),
                entity.title(),
                Currency.getInstance(entity.currency()));
    }
}
