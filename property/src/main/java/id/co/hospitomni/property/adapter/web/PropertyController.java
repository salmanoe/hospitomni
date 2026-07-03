/*
 * Property content endpoints (Channex-shaped). GET list doubles as the
 * "verify the key" call (PLAN.md step 1 DoD); POST/PUT assign and update
 * HospitOmni-owned UUIDs (step 2).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web;

import id.co.hospitomni.property.adapter.web.request.UpsertPropertyRequest;
import id.co.hospitomni.property.adapter.web.response.PropertyResponse;
import id.co.hospitomni.property.application.PropertyService;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ApiResponse<List<PropertyResponse>> list() {
        return ApiResponse.ok(
                propertyService.listProperties().stream().map(PropertyResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<PropertyResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(PropertyResponse.from(propertyService.getProperty(PropertyId.of(id))));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PropertyResponse> create(@Valid @RequestBody UpsertPropertyRequest request) {
        return ApiResponse.created(
                PropertyResponse.from(propertyService.createProperty(request.toCommand())));
    }

    @PutMapping("/{id}")
    public ApiResponse<PropertyResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpsertPropertyRequest request) {
        return ApiResponse.ok(PropertyResponse.from(
                propertyService.updateProperty(PropertyId.of(id), request.toCommand())));
    }
}
