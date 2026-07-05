/*
 * GET /api/v1/sync-status — per property-channel push health (last push,
 * error, backlog, dead letters) plus the booking-events stream tip. The
 * ops surface the doctor command reads; also what a dashboard would poll.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.adapter.web;

import id.co.hospitomni.channel.application.SyncStatusService;
import id.co.hospitomni.channel.application.SyncStatusService.SyncStatus;
import id.co.hospitomni.channel.domain.model.ChannelSyncStatus;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.web.ApiResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sync-status")
public class SyncStatusController {

    private final SyncStatusService service;

    public SyncStatusController(SyncStatusService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SyncStatusResponse> status(
            @RequestParam(value = "property_id", required = false) @Nullable UUID propertyId) {
        SyncStatus status = service.status(propertyId == null ? null : PropertyId.of(propertyId));
        return ApiResponse.ok(new SyncStatusResponse(
                status.channels().stream().map(ChannelStatusBody::from).toList(),
                new BookingEventsBody(
                        status.bookingEvents().tipSeq(), status.bookingEvents().lastEventAt())));
    }

    public record SyncStatusResponse(List<ChannelStatusBody> channels, BookingEventsBody bookingEvents) {
    }

    public record ChannelStatusBody(
            PropertyChannelId channelId,
            PropertyId propertyId,
            String otaName,
            boolean paused,
            long epoch,
            @Nullable Instant lastPushAt,
            @Nullable String lastPushError,
            long pendingCells,
            @Nullable Instant oldestPendingMarkedAt,
            long deadLetters) {

        static ChannelStatusBody from(ChannelSyncStatus status) {
            return new ChannelStatusBody(
                    status.channelId(), status.propertyId(), status.otaName(),
                    status.paused(), status.epoch(),
                    status.lastPushAt(), status.lastPushError(),
                    status.pendingCells(), status.oldestPendingMarkedAt(), status.deadLetters());
        }
    }

    public record BookingEventsBody(long tipSeq, @Nullable Instant lastEventAt) {
    }
}
