/*
 * Typed identifier for an API key record (the key itself is never stored, only its hash).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import java.util.UUID;

public record ApiKeyId(UUID value) implements DomainId {
    public ApiKeyId {
        Guard.notNull(value, "value");
    }

    public static ApiKeyId generate() {
        return new ApiKeyId(UUID.randomUUID());
    }

    public static ApiKeyId of(UUID value) {
        return new ApiKeyId(value);
    }
}
