/*
 * The booking-events feed: cursor-based, oldest-first, replayable from any
 * position. The client owns its cursor — pass the last `seq` it processed
 * as `after`; nothing expires and there is no ack. Events are served
 * exactly as appended (stored snapshots), so replays are byte-stable.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.adapter.web;

import id.co.hospitomni.booking.application.BookingQueryService;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

@RestController
@RequestMapping("/api/v1/booking-events")
public class BookingEventController {

    static final int MAX_LIMIT = 500;

    private final BookingQueryService queryService;
    private final ObjectMapper objectMapper;

    public BookingEventController(BookingQueryService queryService, ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<ObjectNode>> events(
            @RequestParam(name = "after", defaultValue = "0") long after,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        Guard.isTrue(limit <= MAX_LIMIT, "limit must be <= " + MAX_LIMIT);
        List<ObjectNode> events = queryService.eventsAfter(after, limit).stream()
                .map(event -> {
                    // seq first, then the stored snapshot — seq IS the cursor.
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("seq", event.seq());
                    node.setAll((ObjectNode) objectMapper.readTree(event.payloadJson()));
                    return node;
                })
                .toList();
        return ApiResponse.ok(events);
    }
}
