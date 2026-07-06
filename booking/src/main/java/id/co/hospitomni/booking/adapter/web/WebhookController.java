/*
 * Webhook subscription endpoints — the management surface for the signed
 * booking-events fast path. The secret appears exactly once, in the
 * registration (or rotation) response; every other read serves delivery
 * state only. Signature contract per delivery:
 * X-Hospitomni-Signature: v1=<hex HMAC-SHA256 over "<timestamp>.<body>">
 * with the secret string as key and X-Hospitomni-Timestamp as the
 * timestamp; consumers verify constant-time, bound |now-ts|, and fall
 * back to GET /booking-events on any failure.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.adapter.web;

import id.co.hospitomni.booking.application.WebhookService;
import id.co.hospitomni.booking.application.WebhookService.RegisteredWebhook;
import id.co.hospitomni.booking.domain.model.WebhookSubscription;
import id.co.hospitomni.booking.domain.port.out.WebhookSubscriptionStore.SubscriptionWithPending;
import id.co.hospitomni.shared.WebhookId;
import id.co.hospitomni.shared.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    public record RegisterRequest(@NotBlank @Size(max = 2048) String url) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    /** Registration/rotation response — the only place the secret ever appears. */
    public record RegisteredResponse(
            WebhookId id, String url, boolean active, String secret, Instant createdAt) {

        static RegisteredResponse from(RegisteredWebhook registered) {
            WebhookSubscription subscription = registered.subscription();
            return new RegisteredResponse(subscription.id(), subscription.url(),
                    subscription.active(), registered.secret(), subscription.createdAt());
        }
    }

    public record WebhookResponse(
            WebhookId id,
            String url,
            boolean active,
            long lastDeliveredSeq,
            long pendingEvents,
            int consecutiveFailures,
            @Nullable String lastError,
            @Nullable Instant lastSuccessAt,
            Instant createdAt) {

        static WebhookResponse from(SubscriptionWithPending withPending) {
            WebhookSubscription s = withPending.subscription();
            return new WebhookResponse(s.id(), s.url(), s.active(), s.lastDeliveredSeq(),
                    withPending.pendingEvents(), s.consecutiveFailures(), s.lastError(),
                    s.lastSuccessAt(), s.createdAt());
        }
    }

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisteredResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.created(RegisteredResponse.from(webhookService.register(request.url())));
    }

    @GetMapping
    public ApiResponse<List<WebhookResponse>> list() {
        return ApiResponse.ok(
                webhookService.list().stream().map(WebhookResponse::from).toList());
    }

    @PostMapping("/{id}/rotate-secret")
    public ApiResponse<RegisteredResponse> rotateSecret(@PathVariable UUID id) {
        return ApiResponse.ok(
                RegisteredResponse.from(webhookService.rotateSecret(WebhookId.of(id))));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WebhookResponse> setActive(
            @PathVariable UUID id, @Valid @RequestBody ActiveRequest request) {
        return ApiResponse.ok(WebhookResponse.from(
                webhookService.setActive(WebhookId.of(id), request.active())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        webhookService.delete(WebhookId.of(id));
    }
}
