/*
 * Typed identifier for an inbound OTA booking (HospitOmni owns these UUIDs).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.shared;

import java.util.UUID;

public record BookingId(UUID value) implements DomainId {
    public BookingId {
        Guard.notNull(value, "value");
    }

    public static BookingId generate() {
        return new BookingId(UUID.randomUUID());
    }

    public static BookingId of(UUID value) {
        return new BookingId(value);
    }

    public static BookingId of(String value) {
        return new BookingId(UUID.fromString(value));
    }
}
