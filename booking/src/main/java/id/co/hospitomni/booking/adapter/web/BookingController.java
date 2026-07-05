/*
 * Single-booking lookup — what the PMS calls when an event needs its full
 * current state re-fetched.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.adapter.web;

import id.co.hospitomni.booking.adapter.web.response.BookingResponse;
import id.co.hospitomni.booking.application.BookingQueryService;
import id.co.hospitomni.shared.BookingId;
import id.co.hospitomni.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingQueryService queryService;

    public BookingController(BookingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingResponse> get(@PathVariable("id") UUID id) {
        return ApiResponse.ok(BookingResponse.from(queryService.get(BookingId.of(id))));
    }
}
