/*
 * One internal unit's identity on one OTA channel: the property, a room
 * type, or a rate plan, each with the code the OTA lists it under. Real
 * adapters translate outbound unit UUIDs to these codes and inbound OTA
 * payloads back.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.domain.model;

import id.co.hospitomni.shared.Guard;

import java.util.UUID;

public record UnitMapping(UnitType unitType, UUID unitId, String otaCode) {

    public enum UnitType {PROPERTY, ROOM_TYPE, RATE_PLAN}

    public UnitMapping {
        Guard.notNull(unitType, "unitType");
        Guard.notNull(unitId, "unitId");
        Guard.notBlank(otaCode, "otaCode");
    }
}
