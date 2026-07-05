/*
 * Lifecycle of an inbound OTA booking, mirrored 1:1 on the event stream:
 * every revision carries the status it set. Wire values are lowercase.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.domain.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BookingStatus {
    NEW("new"),
    MODIFIED("modified"),
    CANCELLED("cancelled");

    private final String wire;

    BookingStatus(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public static BookingStatus fromWire(String value) {
        for (BookingStatus status : values()) {
            if (status.wire.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown booking status: " + value);
    }
}
