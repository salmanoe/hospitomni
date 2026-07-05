/*
 * Property-channel endpoints: connect a property to an OTA, list,
 * pause/resume (the per-channel kill switch honored by the relay), and
 * set/clear OTA credentials. Credentials are write-only: responses carry
 * only has_credentials, never the stored values.
 *
 * @author Salman
 * @version 1.1
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/property-channels")
public class PropertyChannelController {

    public record ConnectRequest(@NotNull UUID propertyId, @NotBlank String otaName) {
    }

    public record PauseRequest(@NotNull Boolean paused) {
    }

    public record SetCredentialsRequest(@NotNull JsonNode credentials) {
    }

    public record ChannelResponse(
            PropertyChannelId id, PropertyId propertyId, String otaName, boolean paused,
            long epoch, boolean hasCredentials) {

        static ChannelResponse from(PropertyChannel channel) {
            return new ChannelResponse(channel.id(), channel.propertyId(),
                    channel.otaName(), channel.paused(), channel.epoch(),
                    channel.hasCredentials());
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

    /** Write-only: the stored credentials are never readable through the API. */
    @PutMapping("/{id}/credentials")
    public ApiResponse<ChannelResponse> setCredentials(
            @PathVariable UUID id, @Valid @RequestBody SetCredentialsRequest request) {
        return ApiResponse.ok(ChannelResponse.from(service.setCredentials(
                PropertyChannelId.of(id), request.credentials().toString())));
    }

    @DeleteMapping("/{id}/credentials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCredentials(@PathVariable UUID id) {
        service.clearCredentials(PropertyChannelId.of(id));
    }
}
