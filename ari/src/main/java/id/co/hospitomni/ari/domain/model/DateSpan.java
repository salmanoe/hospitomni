/*
 * An inclusive date range — write payloads carry either a single date or
 * date_from/date_to; both normalize to a span. Capped so a bad payload
 * can't fan out into millions of day-cells.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.domain.model;

import id.co.hospitomni.shared.Guard;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

public record DateSpan(LocalDate from, LocalDate to) {

    /** Two years — matches how far ahead OTAs accept ARI. */
    public static final int MAX_DAYS = 731;

    public DateSpan {
        Guard.notNull(from, "from");
        Guard.notNull(to, "to");
        Guard.isTrue(!to.isBefore(from), "date_to must not be before date_from");
        Guard.isTrue(ChronoUnit.DAYS.between(from, to) < MAX_DAYS,
                "date range must span fewer than " + MAX_DAYS + " days");
    }

    public static DateSpan single(LocalDate date) {
        return new DateSpan(date, date);
    }

    public List<LocalDate> days() {
        return Stream.iterate(from, d -> d.plusDays(1))
                .limit(ChronoUnit.DAYS.between(from, to) + 1)
                .toList();
    }
}
