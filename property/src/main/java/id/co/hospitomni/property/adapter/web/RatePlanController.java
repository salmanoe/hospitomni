/*
 * Rate-plan content endpoints (Channex-shaped): list filtered by property_id.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web;

import id.co.hospitomni.property.adapter.web.request.UpsertRatePlanRequest;
import id.co.hospitomni.property.adapter.web.response.RatePlanResponse;
import id.co.hospitomni.property.application.RatePlanService;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
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
@RequestMapping("/api/v1/rate-plans")
public class RatePlanController {

    private final RatePlanService ratePlanService;

    public RatePlanController(RatePlanService ratePlanService) {
        this.ratePlanService = ratePlanService;
    }

    @GetMapping
    public ApiResponse<List<RatePlanResponse>> list(@RequestParam("property_id") UUID propertyId) {
        return ApiResponse.ok(ratePlanService.listRatePlans(PropertyId.of(propertyId)).stream()
                .map(RatePlanResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<RatePlanResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(RatePlanResponse.from(ratePlanService.getRatePlan(RatePlanId.of(id))));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RatePlanResponse> create(@Valid @RequestBody UpsertRatePlanRequest request) {
        return ApiResponse.created(
                RatePlanResponse.from(ratePlanService.createRatePlan(request.toCommand())));
    }

    @PutMapping("/{id}")
    public ApiResponse<RatePlanResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpsertRatePlanRequest request) {
        return ApiResponse.ok(RatePlanResponse.from(
                ratePlanService.updateRatePlan(RatePlanId.of(id), request.toCommand())));
    }
}
