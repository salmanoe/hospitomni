/*
 * RangeCompressor unit tests — runs break on value changes and date gaps.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeCompressorTest {

    private static final LocalDate D1 = LocalDate.of(2026, 8, 1);

    private static List<RangeCompressor.Run<Integer>> compress(List<Map.Entry<LocalDate, Integer>> cells) {
        return RangeCompressor.compress(cells, Map.Entry::getKey, Map.Entry::getValue);
    }

    @Test
    void emptyInputYieldsNoRuns() {
        assertTrue(compress(List.of()).isEmpty());
    }

    @Test
    void consecutiveEqualValuesCollapseToOneRun() {
        var runs = compress(List.of(
                entry(D1, 5), entry(D1.plusDays(1), 5), entry(D1.plusDays(2), 5)));
        assertEquals(List.of(new RangeCompressor.Run<>(D1, D1.plusDays(2), 5)), runs);
    }

    @Test
    void valueChangeBreaksTheRun() {
        var runs = compress(List.of(
                entry(D1, 5), entry(D1.plusDays(1), 3), entry(D1.plusDays(2), 3)));
        assertEquals(List.of(
                new RangeCompressor.Run<>(D1, D1, 5),
                new RangeCompressor.Run<>(D1.plusDays(1), D1.plusDays(2), 3)), runs);
    }

    @Test
    void dateGapBreaksTheRunEvenWithEqualValues() {
        var runs = compress(List.of(
                entry(D1, 5), entry(D1.plusDays(1), 5), entry(D1.plusDays(3), 5)));
        assertEquals(List.of(
                new RangeCompressor.Run<>(D1, D1.plusDays(1), 5),
                new RangeCompressor.Run<>(D1.plusDays(3), D1.plusDays(3), 5)), runs);
    }
}
