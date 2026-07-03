/*
 * Spring Data JPA repository for room_type rows.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.infrastructure.persistence;

import id.co.hospitomni.property.infrastructure.persistence.entity.RoomTypeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomTypeJpaRepository extends JpaRepository<RoomTypeJpaEntity, UUID> {

    List<RoomTypeJpaEntity> findAllByPropertyIdOrderByTitle(UUID propertyId);
}
