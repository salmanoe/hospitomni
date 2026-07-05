/*
 * App-level AES-256-GCM for secrets at rest (OTA credentials, webhook
 * signing keys). The key lives outside the database — supplied at boot —
 * and every ciphertext is stamped with the key id that produced it, so a
 * future rotation can decrypt old rows with the old key while writing new
 * ones with the new. Wire format: 12-byte random IV || GCM ciphertext+tag.
 *
 * Pure JCA, no Spring — bean wiring happens in bootstrap.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.shared.crypto;

import id.co.hospitomni.shared.Guard;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public final class SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;
    private final String keyId;

    public SecretCipher(byte[] keyBytes, String keyId) {
        Guard.notNull(keyBytes, "keyBytes");
        Guard.isTrue(keyBytes.length == 32, "secret key must be exactly 32 bytes (AES-256)");
        Guard.notBlank(keyId, "keyId");
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.keyId = keyId;
    }

    /** The id stored beside every ciphertext this cipher produces. */
    public String keyId() {
        return keyId;
    }

    public byte[] encrypt(String plaintext) {
        Guard.notNull(plaintext, "plaintext");
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public String decrypt(byte[] ciphertext, String storedKeyId) {
        Guard.notNull(ciphertext, "ciphertext");
        Guard.isTrue(ciphertext.length > IV_BYTES, "ciphertext too short to contain an IV");
        if (!keyId.equals(storedKeyId)) {
            throw new IllegalStateException(
                    "Ciphertext was sealed with key '%s' but the active key is '%s' — old-key "
                            + "decryption support is required before rotating"
                            .formatted(storedKeyId, keyId));
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(ciphertext, IV_BYTES, ciphertext.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed (wrong key or tampered data)", e);
        }
    }
}
