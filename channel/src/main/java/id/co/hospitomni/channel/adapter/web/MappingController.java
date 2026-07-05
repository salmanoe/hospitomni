/*
 * Unit-mapping endpoints: PUT /mappings declares one channel's complete
 * unit ↔ OTA-code set (full replace — omitted units are unmapped); GET
 * reads it back. This is how an operator wires a connected channel to
 * the OTA's listing/rate ids before real pushes can be addressed.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.adapter.web;

import id.co.hospitomni.channel.application.UnitMappingService;
import id.co.hospitomni.channel.application.UnitMappingService.ReplaceMappingsCommand;
import id.co.hospitomni.channel.domain.model.UnitMapping;
import id.co.hospitomni.channel.domain.model.UnitMapping.UnitType;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mappings")
public class MappingController {

    public record UnitCodeRequest(
            @NotNull UUID id, @NotBlank @Size(max = 128) String otaCode) {
    }

    public record ReplaceMappingsRequest(
            @NotNull UUID propertyChannelId,
            @NotBlank @Size(max = 128) String propertyCode,
            @Valid @Nullable List<UnitCodeRequest> roomTypes,
            @Valid @Nullable List<UnitCodeRequest> ratePlans) {
    }

    public record UnitCodeResponse(UUID id, String otaCode) {
    }

    public record MappingsResponse(
            PropertyChannelId propertyChannelId,
            @Nullable String propertyCode,
            List<UnitCodeResponse> roomTypes,
            List<UnitCodeResponse> ratePlans) {

        static MappingsResponse from(PropertyChannelId channelId, List<UnitMapping> mappings) {
            return new MappingsResponse(
                    channelId,
                    mappings.stream()
                            .filter(m -> m.unitType() == UnitType.PROPERTY)
                            .map(UnitMapping::otaCode)
                            .findFirst().orElse(null),
                    codesOf(mappings, UnitType.ROOM_TYPE),
                    codesOf(mappings, UnitType.RATE_PLAN));
        }

        private static List<UnitCodeResponse> codesOf(List<UnitMapping> mappings, UnitType type) {
            return mappings.stream()
                    .filter(m -> m.unitType() == type)
                    .map(m -> new UnitCodeResponse(m.unitId(), m.otaCode()))
                    .toList();
        }
    }

    private final UnitMappingService mappingService;

    public MappingController(UnitMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @PutMapping
    public ApiResponse<MappingsResponse> replace(@Valid @RequestBody ReplaceMappingsRequest request) {
        PropertyChannelId channelId = PropertyChannelId.of(request.propertyChannelId());
        ReplaceMappingsCommand command = new ReplaceMappingsCommand(
                request.propertyCode(),
                toUniqueMap("room_types", request.roomTypes()),
                toUniqueMap("rate_plans", request.ratePlans()));
        return ApiResponse.ok(
                MappingsResponse.from(channelId, mappingService.replace(channelId, command)));
    }

    @GetMapping
    public ApiResponse<MappingsResponse> get(@RequestParam("property_channel_id") UUID id) {
        PropertyChannelId channelId = PropertyChannelId.of(id);
        return ApiResponse.ok(MappingsResponse.from(channelId, mappingService.get(channelId)));
    }

    private static SequencedMap<UUID, String> toUniqueMap(
            String field, @Nullable List<UnitCodeRequest> units) {
        SequencedMap<UUID, String> byId = new LinkedHashMap<>();
        for (UnitCodeRequest unit : units == null ? List.<UnitCodeRequest>of() : units) {
            if (byId.putIfAbsent(unit.id(), unit.otaCode()) != null) {
                throw new IllegalArgumentException(
                        "duplicate id in %s: %s".formatted(field, unit.id()));
            }
        }
        return byId;
    }
}
