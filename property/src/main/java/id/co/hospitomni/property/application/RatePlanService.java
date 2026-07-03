/*
 * Rate-plan content use cases — tenant-scoped via the parent property, and
 * the room type must belong to the same property.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.application;

import id.co.hospitomni.property.application.command.UpsertRatePlanCommand;
import id.co.hospitomni.property.domain.model.RatePlan;
import id.co.hospitomni.property.domain.model.RoomType;
import id.co.hospitomni.property.domain.port.out.PropertyRepository;
import id.co.hospitomni.property.domain.port.out.RatePlanRepository;
import id.co.hospitomni.property.domain.port.out.RoomTypeRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;

@Service
public class RatePlanService {

    private final RatePlanRepository ratePlanRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final PropertyRepository propertyRepository;

    public RatePlanService(
            RatePlanRepository ratePlanRepository,
            RoomTypeRepository roomTypeRepository,
            PropertyRepository propertyRepository) {
        this.ratePlanRepository = ratePlanRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional(readOnly = true)
    public List<RatePlan> listRatePlans(PropertyId propertyId) {
        requireOwnedProperty(propertyId);
        return ratePlanRepository.findAllByProperty(propertyId);
    }

    @Transactional(readOnly = true)
    public RatePlan getRatePlan(RatePlanId id) {
        RatePlan ratePlan = ratePlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RatePlan", id.value()));
        requireOwnedProperty(ratePlan.propertyId());
        return ratePlan;
    }

    @Transactional
    public RatePlan createRatePlan(UpsertRatePlanCommand command) {
        requireOwnedProperty(command.propertyId());
        requireRoomTypeInProperty(command.roomTypeId(), command.propertyId());
        return ratePlanRepository.save(new RatePlan(
                RatePlanId.generate(),
                command.propertyId(),
                command.roomTypeId(),
                command.title(),
                Currency.getInstance(command.currency())));
    }

    @Transactional
    public RatePlan updateRatePlan(RatePlanId id, UpsertRatePlanCommand command) {
        RatePlan existing = getRatePlan(id);
        requireRoomTypeInProperty(command.roomTypeId(), existing.propertyId());
        // The parent property is immutable; the room type may be repointed
        // within the same property.
        return ratePlanRepository.save(new RatePlan(
                existing.id(),
                existing.propertyId(),
                command.roomTypeId(),
                command.title(),
                Currency.getInstance(command.currency())));
    }

    private void requireOwnedProperty(PropertyId propertyId) {
        propertyRepository.findByIdAndAccount(propertyId, AccountContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId.value()));
    }

    private void requireRoomTypeInProperty(RoomTypeId roomTypeId, PropertyId propertyId) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", roomTypeId.value()));
        Guard.isTrue(roomType.propertyId().equals(propertyId),
                "roomTypeId must belong to property " + propertyId.value());
    }
}
