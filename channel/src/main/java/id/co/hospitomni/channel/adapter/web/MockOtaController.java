/*
 * Readback + failure-injection hooks on the mock OTA — the verification
 * surface for PLAN.md step 4 ("readback on the mock confirms receipt").
 * Profile-gated: exists only in local/test, never in production.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.adapter.web;

import id.co.hospitomni.channel.adapter.ota.MockOtaAdapter;
import id.co.hospitomni.channel.domain.model.AriPush;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public MockOtaController(MockOtaAdapter mockOtaAdapter, PropertyChannelRepository channelRepository) {
        this.mockOtaAdapter = mockOtaAdapter;
        this.channelRepository = channelRepository;
    }

    @GetMapping("/pushes")
    public ApiResponse<List<AriPush>> pushes(@RequestParam("property_id") UUID propertyId) {
        PropertyId id = PropertyId.of(propertyId);
        if (!channelRepository.propertyOwnedBy(id, AccountContext.current())) {
            throw new ResourceNotFoundException("Property", propertyId);
        }
        return ApiResponse.ok(mockOtaAdapter.pushesFor(id));
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
