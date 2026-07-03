/*
 * POST /api/v1/availability (bulk write) and GET /api/v1/availability
 * (range-compressed read) — PLAN.md build step 3.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.adapter.web;

import id.co.hospitomni.ari.adapter.web.request.AvailabilityWriteRequest;
import id.co.hospitomni.ari.adapter.web.response.AvailabilityRangeResponse;
import id.co.hospitomni.ari.application.AriReadService;
import id.co.hospitomni.ari.application.AriWriteService;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {

    private final AriWriteService writeService;
    private final AriReadService readService;

    public AvailabilityController(AriWriteService writeService, AriReadService readService) {
        this.writeService = writeService;
        this.readService = readService;
    }

    @PostMapping
    public ApiResponse<Map<String, Integer>> write(
            @Valid @RequestBody AvailabilityWriteRequest request) {
        int written = writeService.applyAvailability(request.toCommands());
        return ApiResponse.ok(Map.of("updated", written));
    }

    @GetMapping
    public ApiResponse<List<AvailabilityRangeResponse>> read(
            @RequestParam("property_id") UUID propertyId,
            @RequestParam(value = "date_gte", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateGte,
            @RequestParam(value = "date_lte", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateLte) {
        return ApiResponse.ok(
                readService.readAvailability(PropertyId.of(propertyId), dateGte, dateLte).stream()
                        .map(AvailabilityRangeResponse::from)
                        .toList());
    }
}
