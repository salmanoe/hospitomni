/*
 * Readback + failure-injection + booking-injection hooks on the mock OTA —
 * the verification surface that proves pushes were received, and the far
 * end of the inbound loop: real OTAs have no test-booking API, so this is
 * how a "guest books on the OTA" moment is simulated. An injected booking
 * flows through ingestion into the booking-events feed exactly like a real
 * OTA notification would.
 * Profile-gated: exists only in local/test, never in production.
 *
 * @author Salman
 * @version 1.1
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.adapter.web;

import id.co.hospitomni.booking.application.BookingIngestionService;
import id.co.hospitomni.booking.application.BookingIngestionService.IngestResult;
import id.co.hospitomni.booking.application.command.OtaBookingCommand;
import id.co.hospitomni.booking.domain.model.BookingStatus;
import id.co.hospitomni.booking.domain.model.Customer;
import id.co.hospitomni.booking.domain.model.RoomStay;
import id.co.hospitomni.channel.adapter.ota.MockOtaAdapter;
import id.co.hospitomni.channel.domain.model.AriPush;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.BookingId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@Profile({"local", "test"})
@RestController
@RequestMapping("/api/v1/mock-ota")
public class MockOtaController {

    public record FailNextRequest(@NotNull @Min(1) Integer count) {
    }

    private final MockOtaAdapter mockOtaAdapter;
    private final PropertyChannelRepository channelRepository;
    private final BookingIngestionService bookingIngestion;

    public MockOtaController(
            MockOtaAdapter mockOtaAdapter,
            PropertyChannelRepository channelRepository,
            BookingIngestionService bookingIngestion) {
        this.mockOtaAdapter = mockOtaAdapter;
        this.channelRepository = channelRepository;
        this.bookingIngestion = bookingIngestion;
    }

    @GetMapping("/pushes")
    public ApiResponse<List<AriPush>> pushes(@RequestParam("property_id") UUID propertyId) {
        PropertyId id = PropertyId.of(propertyId);
        if (!channelRepository.propertyOwnedBy(id, AccountContext.current())) {
            throw new ResourceNotFoundException("Property", propertyId);
        }
        return ApiResponse.ok(mockOtaAdapter.pushesFor(id));
    }

    /**
     * Simulates "the OTA notifies us of a booking". Repeat with a higher
     * {@code revision_seq} to modify or cancel; redeliver the same
     * {@code revision_seq} to exercise dedupe ({@code duplicate: true}).
     */
    @PostMapping("/bookings")
    public ApiResponse<InjectedBooking> injectBooking(@Valid @RequestBody InjectBookingRequest request) {
        IngestResult result = bookingIngestion.ingest(new OtaBookingCommand(
                PropertyId.of(request.propertyId()),
                MockOtaAdapter.OTA_NAME,
                request.otaReservationCode(),
                request.revisionSeq(),
                BookingStatus.fromWire(request.status()),
                new Customer(request.customer().name(), request.customer().surname(),
                        request.customer().mail(), request.customer().phone(),
                        request.customer().country()),
                request.rooms().stream()
                        .map(room -> new RoomStay(
                                RoomTypeId.of(room.roomTypeId()), RatePlanId.of(room.ratePlanId()),
                                room.checkinDate(), room.checkoutDate(),
                                room.occupancy().adults(),
                                room.occupancy().children() == null ? 0 : room.occupancy().children()))
                        .toList()));
        return ApiResponse.ok(new InjectedBooking(result.bookingId(), result.seq(), result.duplicate()));
    }

    public record InjectBookingRequest(
            @NotNull UUID propertyId,
            @NotBlank String otaReservationCode,
            @NotNull @Min(1) Integer revisionSeq,
            @NotBlank String status,
            @NotNull @Valid CustomerBody customer,
            @NotEmpty @Valid List<RoomBody> rooms) {

        public record CustomerBody(
                @NotBlank String name,
                @Nullable String surname,
                @Nullable String mail,
                @Nullable String phone,
                @Nullable String country) {
        }

        public record RoomBody(
                @NotNull UUID roomTypeId,
                @NotNull UUID ratePlanId,
                @NotNull LocalDate checkinDate,
                @NotNull LocalDate checkoutDate,
                @NotNull @Valid OccupancyBody occupancy) {
        }

        public record OccupancyBody(@NotNull @Min(1) Integer adults, @Nullable @Min(0) Integer children) {
        }
    }

    public record InjectedBooking(BookingId bookingId, @Nullable Long seq, boolean duplicate) {
    }

    @PostMapping("/fail-next")
    public ApiResponse<Map<String, Integer>> failNext(@Valid @RequestBody FailNextRequest request) {
        mockOtaAdapter.failNext(request.count());
        return ApiResponse.ok(Map.of("failing_next", request.count()));
    }

    @DeleteMapping("/pushes")
    public ApiResponse<Map<String, String>> clear() {
        mockOtaAdapter.clear();
        return ApiResponse.ok(Map.of("status", "cleared"));
    }
}
