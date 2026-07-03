/*
 * Property-channel endpoints: connect a property to an OTA, list, and
 * pause/resume (the per-channel kill switch honored by the relay).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.adapter.web;

import id.co.hospitomni.channel.application.PropertyChannelService;
import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/property-channels")
public class PropertyChannelController {

    public record ConnectRequest(@NotNull UUID propertyId, @NotBlank String otaName) {
    }

    public record PauseRequest(@NotNull Boolean paused) {
    }

    public record ChannelResponse(
            PropertyChannelId id, PropertyId propertyId, String otaName, boolean paused, long epoch) {

        static ChannelResponse from(PropertyChannel channel) {
            return new ChannelResponse(channel.id(), channel.propertyId(),
                    channel.otaName(), channel.paused(), channel.epoch());
        }
    }

    private final PropertyChannelService service;

    public PropertyChannelController(PropertyChannelService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelResponse> connect(@Valid @RequestBody ConnectRequest request) {
        return ApiResponse.created(ChannelResponse.from(
                service.connect(PropertyId.of(request.propertyId()), request.otaName())));
    }

    @GetMapping
    public ApiResponse<List<ChannelResponse>> list(@RequestParam("property_id") UUID propertyId) {
        return ApiResponse.ok(service.list(PropertyId.of(propertyId)).stream()
                .map(ChannelResponse::from)
                .toList());
    }

    @PutMapping("/{id}")
    public ApiResponse<ChannelResponse> setPaused(
            @PathVariable UUID id, @Valid @RequestBody PauseRequest request) {
        return ApiResponse.ok(ChannelResponse.from(
                service.setPaused(PropertyChannelId.of(id), request.paused())));
    }
}
