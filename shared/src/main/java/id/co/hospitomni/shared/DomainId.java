/*
 * Marker for typed UUID identifiers — serialized as the bare UUID.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

public interface DomainId {
    @JsonValue
    UUID value();

    static UUID generate() {
        return UUID.randomUUID();
    }
}
