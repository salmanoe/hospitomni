/*
 * The delivery signature: HMAC-SHA256 over `<timestamp> "." <raw body
 * bytes>`, keyed with the subscription secret string verbatim, rendered
 * as `v1=<lowercase hex>`. Consumers recompute over the raw bytes they
 * received (before JSON parsing), compare constant-time, and reject
 * timestamps outside their replay window; on any verification failure
 * they fall back to polling — webhook loss is never data loss.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

public final class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-Hospitomni-Signature";
    public static final String TIMESTAMP_HEADER = "X-Hospitomni-Timestamp";

    private WebhookSigner() {} // Utility class — no instantiation

    public static String signature(String secret, long timestampSeconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(Long.toString(timestampSeconds).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return "v1=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable — broken JRE", e);
        }
    }
}
