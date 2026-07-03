/*
 * Room-type content endpoints (Channex-shaped): list filtered by property_id.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web;

import id.co.hospitomni.property.adapter.web.request.UpsertRoomTypeRequest;
import id.co.hospitomni.property.adapter.web.response.RoomTypeResponse;
import id.co.hospitomni.property.application.RoomTypeService;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/room-types")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    public RoomTypeController(RoomTypeService roomTypeService) {
        this.roomTypeService = roomTypeService;
    }

    @GetMapping
    public ApiResponse<List<RoomTypeResponse>> list(@RequestParam("property_id") UUID propertyId) {
        return ApiResponse.ok(roomTypeService.listRoomTypes(PropertyId.of(propertyId)).stream()
                .map(RoomTypeResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomTypeResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(RoomTypeResponse.from(roomTypeService.getRoomType(RoomTypeId.of(id))));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomTypeResponse> create(@Valid @RequestBody UpsertRoomTypeRequest request) {
        return ApiResponse.created(
                RoomTypeResponse.from(roomTypeService.createRoomType(request.toCommand())));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomTypeResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpsertRoomTypeRequest request) {
        return ApiResponse.ok(RoomTypeResponse.from(
                roomTypeService.updateRoomType(RoomTypeId.of(id), request.toCommand())));
    }
}
