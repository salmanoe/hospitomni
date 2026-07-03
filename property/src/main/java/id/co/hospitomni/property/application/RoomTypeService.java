/*
 * Room-type content use cases. Every operation resolves the parent property
 * through the tenant-scoped lookup first, so cross-account access is a 404
 * by construction.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.application;

import id.co.hospitomni.property.application.command.UpsertRoomTypeCommand;
import id.co.hospitomni.property.domain.model.RoomType;
import id.co.hospitomni.property.domain.port.out.PropertyRepository;
import id.co.hospitomni.property.domain.port.out.RoomTypeRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final PropertyRepository propertyRepository;

    public RoomTypeService(RoomTypeRepository roomTypeRepository, PropertyRepository propertyRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomType> listRoomTypes(PropertyId propertyId) {
        requireOwnedProperty(propertyId);
        return roomTypeRepository.findAllByProperty(propertyId);
    }

    @Transactional(readOnly = true)
    public RoomType getRoomType(RoomTypeId id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", id.value()));
        requireOwnedProperty(roomType.propertyId());
        return roomType;
    }

    @Transactional
    public RoomType createRoomType(UpsertRoomTypeCommand command) {
        requireOwnedProperty(command.propertyId());
        return roomTypeRepository.save(new RoomType(
                RoomTypeId.generate(),
                command.propertyId(),
                command.title(),
                command.countOfRooms(),
                command.occAdults(),
                command.occChildren()));
    }

    @Transactional
    public RoomType updateRoomType(RoomTypeId id, UpsertRoomTypeCommand command) {
        RoomType existing = getRoomType(id);
        // The parent property is immutable; only content fields change.
        return roomTypeRepository.save(new RoomType(
                existing.id(),
                existing.propertyId(),
                command.title(),
                command.countOfRooms(),
                command.occAdults(),
                command.occChildren()));
    }

    private void requireOwnedProperty(PropertyId propertyId) {
        propertyRepository.findByIdAndAccount(propertyId, AccountContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId.value()));
    }
}
