/*
 * Guard clause utility — expressive, self-documenting pre-condition assertions
 * (mirrors HospitOps's shared kernel).
 *
 * All methods throw IllegalArgumentException — domain pre-condition failures.
 * Do not use for business rule violations (use domain exceptions for those).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import org.jspecify.annotations.Nullable;

public final class Guard {

    private Guard() {} // Utility class — no instantiation

    public static <T> T notNull(@Nullable T value, String fieldName) {
        if (value == null)
            throw new IllegalArgumentException(fieldName + " must not be null");
        return value;
    }

    public static String notBlank(@Nullable String value, String fieldName) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(fieldName + " must not be blank");
        return value;
    }

    public static String maxLength(@Nullable String value, int max, String fieldName) {
        notNull(value, fieldName);
        if (value.length() > max)
            throw new IllegalArgumentException(
                fieldName + " must not exceed " + max + " characters (was " + value.length() + ")");
        return value;
    }

    public static int positive(int value, String fieldName) {
        if (value <= 0)
            throw new IllegalArgumentException(fieldName + " must be positive (was " + value + ")");
        return value;
    }

    public static int nonNegative(int value, String fieldName) {
        if (value < 0)
            throw new IllegalArgumentException(fieldName + " must be >= 0 (was " + value + ")");
        return value;
    }

    public static long positive(long value, String fieldName) {
        if (value <= 0)
            throw new IllegalArgumentException(fieldName + " must be positive (was " + value + ")");
        return value;
    }

    public static <T extends Comparable<T>> T min(@Nullable T value, T minimum, String fieldName) {
        notNull(value, fieldName);
        if (value.compareTo(minimum) < 0)
            throw new IllegalArgumentException(
                fieldName + " must be >= " + minimum + " (was " + value + ")");
        return value;
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
