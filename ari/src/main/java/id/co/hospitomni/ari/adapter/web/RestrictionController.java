/*
 * POST /api/v1/restrictions (bulk partial-update write) and
 * GET /api/v1/restrictions (range-compressed read with ?fields= projection).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.adapter.web;

import id.co.hospitomni.ari.adapter.web.request.RestrictionWriteRequest;
import id.co.hospitomni.ari.adapter.web.response.RestrictionRangeResponse;
import id.co.hospitomni.ari.application.AriReadService;
import id.co.hospitomni.ari.application.AriWriteService;
import id.co.hospitomni.shared.Guard;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/restrictions")
public class RestrictionController {

    private final AriWriteService writeService;
    private final AriReadService readService;

    public RestrictionController(AriWriteService writeService, AriReadService readService) {
        this.writeService = writeService;
        this.readService = readService;
    }

    @PostMapping
    public ApiResponse<Map<String, Integer>> write(
            @Valid @RequestBody RestrictionWriteRequest request) {
        int written = writeService.applyRestrictions(request.toCommands());
        return ApiResponse.ok(Map.of("updated", written));
    }

    @GetMapping
    public ApiResponse<List<RestrictionRangeResponse>> read(
            @RequestParam("property_id") UUID propertyId,
            @RequestParam(value = "date_gte", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateGte,
            @RequestParam(value = "date_lte", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateLte,
            @RequestParam(value = "fields", required = false) @Nullable String fields) {
        return ApiResponse.ok(
                readService.readRestrictions(
                                PropertyId.of(propertyId), dateGte, dateLte, parseFields(fields))
                        .stream()
                        .map(RestrictionRangeResponse::from)
                        .toList());
    }

    private static @Nullable Set<String> parseFields(@Nullable String fields) {
        if (fields == null || fields.isBlank()) {
            return null;
        }
        Set<String> parsed = Arrays.stream(fields.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        parsed.forEach(field -> Guard.isTrue(AriReadService.RESTRICTION_FIELDS.contains(field),
                "unknown restrictions field: " + field));
        return parsed;
    }
}
