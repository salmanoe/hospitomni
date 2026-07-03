/*
 * Persistence port for rate plans.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.domain.port.out;

import id.co.hospitomni.property.domain.model.RatePlan;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;

import java.util.List;
import java.util.Optional;

public interface RatePlanRepository {

    RatePlan save(RatePlan ratePlan);

    Optional<RatePlan> findById(RatePlanId id);

    List<RatePlan> findAllByProperty(PropertyId propertyId);
}
