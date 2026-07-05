/*
 * Reconciliation endpoints: POST runs a drift check for one channel and
 * window (self-healing — drift triggers the fenced full refresh) and
 * returns the outcome with a capped drift sample; GET lists recent runs,
 * the audit trail migration cutovers lean on.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.adapter.web;

import id.co.hospitomni.channel.application.ReconciliationService;
import id.co.hospitomni.channel.domain.model.DriftCell;
import id.co.hospitomni.channel.domain.model.ReconciliationRun;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliations")
public class ReconciliationController {

    public record RunRequest(
            @NotNull UUID propertyChannelId,
            @NotNull LocalDate dateFrom,
            @NotNull LocalDate dateTo) {
    }

    public record RunResponse(
            UUID id,
            PropertyChannelId propertyChannelId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int driftCount,
            boolean refreshed,
            long epoch,
            List<DriftCell> driftSample,
            Instant createdAt) {

        static RunResponse from(ReconciliationRun run, List<DriftCell> sample) {
            return new RunResponse(run.id(), run.channelId(), run.dateFrom(), run.dateTo(),
                    run.driftCount(), run.refreshed(), run.epoch(), sample, run.createdAt());
        }
    }

    public record RunListItem(
            UUID id,
            LocalDate dateFrom,
            LocalDate dateTo,
            int driftCount,
            boolean refreshed,
            long epoch,
            @Nullable String sampleDrift,
            Instant createdAt) {

        static RunListItem from(ReconciliationRun run) {
            return new RunListItem(run.id(), run.dateFrom(), run.dateTo(), run.driftCount(),
                    run.refreshed(), run.epoch(), run.sampleDrift(), run.createdAt());
        }
    }

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RunResponse> run(@Valid @RequestBody RunRequest request) {
        ReconciliationService.Result result = reconciliationService.reconcileOwned(
                PropertyChannelId.of(request.propertyChannelId()),
                request.dateFrom(), request.dateTo());
        return ApiResponse.created(RunResponse.from(result.run(), result.sample()));
    }

    @GetMapping
    public ApiResponse<List<RunListItem>> list(
            @RequestParam("property_channel_id") UUID channelId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ApiResponse.ok(reconciliationService
                .recentRunsOwned(PropertyChannelId.of(channelId), Math.clamp(limit, 1, 100))
                .stream().map(RunListItem::from).toList());
    }
}
