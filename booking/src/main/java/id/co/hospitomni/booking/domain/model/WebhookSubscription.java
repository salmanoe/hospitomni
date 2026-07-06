/*
 * One account's webhook endpoint plus its delivery state. The cursor
 * (lastDeliveredSeq) tracks the booking-events stream; consecutive
 * failures drive the capped backoff. Secret material never appears on
 * the domain model — it stays sealed in the store and is decrypted only
 * inside the delivery worker.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.domain.model;

import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.WebhookId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record WebhookSubscription(
        WebhookId id,
        AccountId accountId,
        String url,
        boolean active,
        long lastDeliveredSeq,
        int consecutiveFailures,
        @Nullable String lastError,
        @Nullable Instant lastSuccessAt,
        Instant createdAt) {

    public WebhookSubscription {
        Guard.notNull(id, "id");
        Guard.notNull(accountId, "accountId");
        Guard.notBlank(url, "url");
        Guard.isTrue(lastDeliveredSeq >= 0, "lastDeliveredSeq must be >= 0");
        Guard.isTrue(consecutiveFailures >= 0, "consecutiveFailures must be >= 0");
    }
}
