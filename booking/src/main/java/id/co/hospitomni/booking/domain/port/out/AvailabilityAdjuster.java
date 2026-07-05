/*
 * Optimistic inventory adjustment on booking ingestion: the moment an OTA
 * booking lands, availability is decremented here and fanned out to the
 * other channels — closing the overbooking window to seconds instead of
 * the PMS's polling latency. The PMS's next authoritative ARI push
 * reconciles; on conflict the PMS value wins.
 *
 * Adjusts only day-cells that already exist (never below zero); dates the
 * hotel never published have no inventory to adjust.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.domain.port.out;

import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;

import java.time.LocalDate;

public interface AvailabilityAdjuster {

    /** Adds {@code delta} (may be negative) to every cell in [from, to], floored at 0. */
    void adjust(PropertyId propertyId, RoomTypeId roomTypeId, LocalDate from, LocalDate to, int delta);
}
