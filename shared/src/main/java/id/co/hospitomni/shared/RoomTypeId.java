/*
 * Typed identifier for a room type (HospitOmni owns these UUIDs).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import java.util.UUID;

public record RoomTypeId(UUID value) implements DomainId {
    public RoomTypeId {
        Guard.notNull(value, "value");
    }

    public static RoomTypeId generate() {
        return new RoomTypeId(UUID.randomUUID());
    }

    public static RoomTypeId of(UUID value) {
        return new RoomTypeId(value);
    }

    public static RoomTypeId of(String value) {
        return new RoomTypeId(UUID.fromString(value));
    }
}
