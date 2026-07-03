/*
 * RoomTypeRepository port implementation — upsert semantics: existing id is
 * loaded and mutated, unknown id is inserted (HospitOmni assigned it).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.infrastructure.persistence;

import id.co.hospitomni.property.domain.model.RoomType;
import id.co.hospitomni.property.domain.port.out.RoomTypeRepository;
import id.co.hospitomni.property.infrastructure.persistence.entity.RoomTypeJpaEntity;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RoomTypeRepositoryAdapter implements RoomTypeRepository {

    private final RoomTypeJpaRepository jpa;

    public RoomTypeRepositoryAdapter(RoomTypeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RoomType save(RoomType roomType) {
        RoomTypeJpaEntity entity = jpa.findById(roomType.id().value())
                .map(existing -> {
                    existing.updateContent(roomType.title(), roomType.countOfRooms(),
                            roomType.occAdults(), roomType.occChildren());
                    return existing;
                })
                .orElseGet(() -> new RoomTypeJpaEntity(
                        roomType.id().value(), roomType.propertyId().value(), roomType.title(),
                        roomType.countOfRooms(), roomType.occAdults(), roomType.occChildren()));
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<RoomType> findById(RoomTypeId id) {
        return jpa.findById(id.value()).map(RoomTypeRepositoryAdapter::toDomain);
    }

    @Override
    public List<RoomType> findAllByProperty(PropertyId propertyId) {
        return jpa.findAllByPropertyIdOrderByTitle(propertyId.value()).stream()
                .map(RoomTypeRepositoryAdapter::toDomain)
                .toList();
    }

    private static RoomType toDomain(RoomTypeJpaEntity entity) {
        return new RoomType(
                RoomTypeId.of(entity.id()),
                PropertyId.of(entity.propertyId()),
                entity.title(),
                entity.countOfRooms(),
                entity.occAdults(),
                entity.occChildren());
    }
}
