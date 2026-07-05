/**
 * Booking aggregate: current state per OTA reservation plus the revision
 * fencing rules that keep out-of-order deliveries from regressing it.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
@NullMarked
package id.co.hospitomni.booking.domain.model;

import org.jspecify.annotations.NullMarked;
