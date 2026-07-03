/*
 * Collapses per-day cells into inclusive date ranges: a run continues while
 * dates are consecutive and the value is equal. Input must be sorted by date
 * within one key (room type / rate plan) — the caller groups per key.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class RangeCompressor {

    private RangeCompressor() {} // Utility class — no instantiation

    /** One compressed run: [from, to] all carrying the same value. */
    public record Run<V>(LocalDate from, LocalDate to, V value) {
    }

    public static <C, V> List<Run<V>> compress(
            List<C> cells, Function<C, LocalDate> dateOf, Function<C, V> valueOf) {
        List<Run<V>> runs = new ArrayList<>();
        LocalDate runFrom = null;
        LocalDate previous = null;
        V runValue = null;

        for (C cell : cells) {
            LocalDate date = dateOf.apply(cell);
            V value = valueOf.apply(cell);
            if (runFrom == null) {
                runFrom = date;
            } else if (!date.equals(previous.plusDays(1)) || !value.equals(runValue)) {
                runs.add(new Run<>(runFrom, previous, runValue));
                runFrom = date;
            }
            previous = date;
            runValue = value;
        }
        if (runFrom != null) {
            runs.add(new Run<>(runFrom, previous, runValue));
        }
        return runs;
    }
}
