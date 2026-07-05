/*
 * Unit-mapping use cases: replace a channel's whole mapping set (PUT
 * semantics — anything not in the payload is unmapped) and read it back.
 * Replace validates that every referenced room type / rate plan belongs
 * to the channel's property, and that no OTA code is claimed by two
 * units of the same type — clean 400s instead of constraint 409s.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.channel.domain.model.UnitMapping;
import id.co.hospitomni.channel.domain.model.UnitMapping.UnitType;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.channel.domain.port.out.UnitMappingRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class UnitMappingService {

    /**
     * The desired mapping set. Maps keep payload order and are keyed by
     * internal unit id (duplicate ids are rejected at the web layer).
     */
    public record ReplaceMappingsCommand(
            String propertyCode,
            SequencedMap<UUID, String> roomTypeCodes,
            SequencedMap<UUID, String> ratePlanCodes) {
    }

    private final PropertyChannelRepository channelRepository;
    private final UnitMappingRepository mappingRepository;

    public UnitMappingService(
            PropertyChannelRepository channelRepository, UnitMappingRepository mappingRepository) {
        this.channelRepository = channelRepository;
        this.mappingRepository = mappingRepository;
    }

    @Transactional
    public List<UnitMapping> replace(PropertyChannelId channelId, ReplaceMappingsCommand command) {
        PropertyChannel channel = requireOwnedChannel(channelId);

        requireAllExist("room_type", command.roomTypeCodes().keySet(),
                mappingRepository.existingRoomTypeIds(
                        channel.propertyId(), command.roomTypeCodes().keySet()));
        requireAllExist("rate_plan", command.ratePlanCodes().keySet(),
                mappingRepository.existingRatePlanIds(
                        channel.propertyId(), command.ratePlanCodes().keySet()));
        requireUniqueCodes("room_type", command.roomTypeCodes());
        requireUniqueCodes("rate_plan", command.ratePlanCodes());

        List<UnitMapping> mappings = new ArrayList<>();
        mappings.add(new UnitMapping(
                UnitType.PROPERTY, channel.propertyId().value(), command.propertyCode()));
        command.roomTypeCodes().forEach((id, code) ->
                mappings.add(new UnitMapping(UnitType.ROOM_TYPE, id, code)));
        command.ratePlanCodes().forEach((id, code) ->
                mappings.add(new UnitMapping(UnitType.RATE_PLAN, id, code)));

        mappingRepository.replaceAll(channelId, mappings);
        return mappingRepository.findAll(channelId);
    }

    @Transactional(readOnly = true)
    public List<UnitMapping> get(PropertyChannelId channelId) {
        requireOwnedChannel(channelId);
        return mappingRepository.findAll(channelId);
    }

    private PropertyChannel requireOwnedChannel(PropertyChannelId channelId) {
        PropertyChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("PropertyChannel", channelId.value()));
        if (!channelRepository.propertyOwnedBy(channel.propertyId(), AccountContext.current())) {
            throw new ResourceNotFoundException("PropertyChannel", channelId.value());
        }
        return channel;
    }

    private static void requireAllExist(String kind, Set<UUID> requested, Set<UUID> existing) {
        Set<UUID> unknown = new HashSet<>(requested);
        unknown.removeAll(existing);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "%s ids not found under this channel's property: %s".formatted(kind, unknown));
        }
    }

    private static void requireUniqueCodes(String kind, Map<UUID, String> codes) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicated = new TreeSet<>();
        for (String code : codes.values()) {
            if (!seen.add(code)) {
                duplicated.add(code);
            }
        }
        if (!duplicated.isEmpty()) {
            throw new IllegalArgumentException(
                    "duplicate %s ota_code values: %s".formatted(kind, duplicated));
        }
    }
}
