/*
 * Typed identifier for a rate plan (HospitOmni owns these UUIDs).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import java.util.UUID;

public record RatePlanId(UUID value) implements DomainId {
    public RatePlanId {
        Guard.notNull(value, "value");
    }

    public static RatePlanId generate() {
        return new RatePlanId(UUID.randomUUID());
    }

    public static RatePlanId of(UUID value) {
        return new RatePlanId(value);
    }

    public static RatePlanId of(String value) {
        return new RatePlanId(UUID.fromString(value));
    }
}
