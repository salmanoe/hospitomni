/*
 * Typed identifier for a property (HospitOmni owns these UUIDs; clients store them).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import java.util.UUID;

public record PropertyId(UUID value) implements DomainId {
    public PropertyId {
        Guard.notNull(value, "value");
    }

    public static PropertyId generate() {
        return new PropertyId(UUID.randomUUID());
    }

    public static PropertyId of(UUID value) {
        return new PropertyId(value);
    }

    public static PropertyId of(String value) {
        return new PropertyId(UUID.fromString(value));
    }
}
