/*
 * One claimed outbox row: a (channel, unit, date) cell whose current ARI
 * value must be re-read and pushed.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.model;

import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.event.AriChangedEvent;

import java.time.LocalDate;
import java.util.UUID;

public record DirtyCell(
        PropertyChannelId channelId,
        AriChangedEvent.Unit unit,
        UUID unitId,
        LocalDate date,
        int attempts) {
}
