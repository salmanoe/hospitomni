/*
 * Uniform success envelope: {success, message, data, timestamp}
 * (mirrors HospitOps). Errors use RFC 9457 Problem Details instead.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared.web;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record ApiResponse<T>(boolean success, @Nullable String message, @Nullable T data, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, null, data, Instant.now());
    }
}
