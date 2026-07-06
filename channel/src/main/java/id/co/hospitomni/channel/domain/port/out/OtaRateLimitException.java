/*
 * The OTA said "slow down" (429-shaped). Not a fault: the relay defers
 * the channel's cells by retryAfterSeconds WITHOUT counting an attempt —
 * rate limits must never dead-letter work or surface as push errors.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.domain.port.out;

public class OtaRateLimitException extends OtaPushException {

    private final long retryAfterSeconds;

    public OtaRateLimitException(long retryAfterSeconds) {
        super("OTA rate limit — retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
