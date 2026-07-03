/*
 * Persistence port for room types.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.domain.port.out;

import id.co.hospitomni.property.domain.model.RoomType;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository {

    RoomType save(RoomType roomType);

    Optional<RoomType> findById(RoomTypeId id);

    List<RoomType> findAllByProperty(PropertyId propertyId);
}
