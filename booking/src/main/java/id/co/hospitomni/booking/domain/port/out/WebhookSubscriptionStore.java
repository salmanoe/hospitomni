/*
 * Persistence for webhook subscriptions and their inline delivery state.
 * claim() locks the row FOR UPDATE SKIP LOCKED — one delivery per
 * subscription across instances, and success updates hit the row the
 * claim already locked. recordFailure runs in a FRESH transaction after
 * the worker's rolled back (same discipline as the ARI relay).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.domain.port.out;

import id.co.hospitomni.booking.domain.model.WebhookSubscription;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.WebhookId;

import java.util.List;
import java.util.Optional;

public interface WebhookSubscriptionStore {

    /** Subscription plus the live count of events it has not delivered yet. */
    record SubscriptionWithPending(WebhookSubscription subscription, long pendingEvents) {
    }

    /** Claimed row with the sealed secret — decrypted only by the worker. */
    record ClaimedSubscription(
            WebhookSubscription subscription, byte[] secretCiphertext, String secretKeyId) {
    }

    /** Creates with the cursor at the current stream tip. */
    WebhookSubscription create(
            AccountId accountId, String url, byte[] secretCiphertext, String secretKeyId);

    List<SubscriptionWithPending> listForAccount(AccountId accountId);

    Optional<SubscriptionWithPending> findWithPending(AccountId accountId, WebhookId id);

    void replaceSecret(WebhookId id, byte[] secretCiphertext, String secretKeyId);

    /** Resume (active=true) also resets failures and pulls next_attempt_at forward. */
    void setActive(WebhookId id, boolean active);

    void delete(WebhookId id);

    /** Active subscriptions whose next_attempt_at is due (no lock). */
    List<WebhookId> dueSubscriptionIds();

    /** FOR UPDATE SKIP LOCKED; empty when another instance holds it or it's gone. */
    Optional<ClaimedSubscription> claim(WebhookId id);

    /** Same transaction as the claim: cursor forward, failure state cleared. */
    void recordSuccess(WebhookId id, long lastDeliveredSeq);

    /** Fresh transaction: failures+1, capped exponential backoff, sanitized error. */
    void recordFailure(WebhookId id, String error, long backoffBaseSeconds, long backoffCapSeconds);
}
