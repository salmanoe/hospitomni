/*
 * Typed identifier for a webhook subscription.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.shared;

import java.util.UUID;

public record WebhookId(UUID value) implements DomainId {
    public WebhookId {
        Guard.notNull(value, "value");
    }

    public static WebhookId generate() {
        return new WebhookId(UUID.randomUUID());
    }

    public static WebhookId of(UUID value) {
        return new WebhookId(value);
    }
}
