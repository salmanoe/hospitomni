/*
 * SHA-256 digest of a raw API key, lowercase hex — the only form ever stored
 * or compared. Plain hash (not bcrypt) is deliberate: keys are 256-bit random
 * strings, not passwords, so brute-force resistance comes from entropy and
 * lookups must be indexable.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.domain.model;

import id.co.hospitomni.shared.Guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ApiKeyDigest {

    private ApiKeyDigest() {} // Utility class — no instantiation

    public static String sha256Hex(String rawKey) {
        Guard.notBlank(rawKey, "rawKey");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable — broken JRE", e);
        }
    }
}
