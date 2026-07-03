/*
 * A push to an OTA failed — the relay retries with exponential backoff and
 * dead-letters after max attempts.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.port.out;

public class OtaPushException extends RuntimeException {

    public OtaPushException(String message) {
        super(message);
    }

    public OtaPushException(String message, Throwable cause) {
        super(message, cause);
    }
}
