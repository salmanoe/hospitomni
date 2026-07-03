/*
 * GET /api/v1/properties — the "verify the key" endpoint (PLAN.md step 1 DoD):
 * a valid user-api-key gets 200 with the account's properties.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web;

import id.co.hospitomni.property.adapter.web.response.PropertyResponse;
import id.co.hospitomni.property.application.PropertyService;
import id.co.hospitomni.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
