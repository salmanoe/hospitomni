/*
 * Typed identifier for a property-channel mapping (one OTA connection of
 * one property).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import java.util.UUID;

public record PropertyChannelId(UUID value) implements DomainId {
    public PropertyChannelId {
        Guard.notNull(value, "value");
    }

    public static PropertyChannelId generate() {
        return new PropertyChannelId(UUID.randomUUID());
    }

    public static PropertyChannelId of(UUID value) {
        return new PropertyChannelId(value);
    }
}
