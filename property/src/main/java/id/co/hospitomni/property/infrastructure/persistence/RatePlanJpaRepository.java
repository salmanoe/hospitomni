/*
 * Spring Data JPA repository for rate_plan rows.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.infrastructure.persistence;

import id.co.hospitomni.property.infrastructure.persistence.entity.RatePlanJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RatePlanJpaRepository extends JpaRepository<RatePlanJpaEntity, UUID> {

    List<RatePlanJpaEntity> findAllByPropertyIdOrderByTitle(UUID propertyId);
}
