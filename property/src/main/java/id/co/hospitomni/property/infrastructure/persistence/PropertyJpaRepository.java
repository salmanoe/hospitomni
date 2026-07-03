/*
 * Spring Data JPA repository for property rows.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.infrastructure.persistence;

import id.co.hospitomni.property.infrastructure.persistence.entity.PropertyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyJpaRepository extends JpaRepository<PropertyJpaEntity, UUID> {

    List<PropertyJpaEntity> findAllByAccountIdOrderByTitle(UUID accountId);

    Optional<PropertyJpaEntity> findByIdAndAccountId(UUID id, UUID accountId);
}
