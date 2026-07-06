/*
 * Webhook delivery tuning knobs (hospitomni.webhook.*). The tick interval
 * lives on the @Scheduled annotation (hospitomni.webhook.fixed-delay,
 * default PT1S — the "fast path" latency budget).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hospitomni.webhook")
public record WebhookProperties(
        int batchLimit,
        long backoffBaseSeconds,
        long backoffCapSeconds,
        long connectTimeoutMillis,
        long readTimeoutMillis,
        @Nullable Boolean requireHttps) {

    public WebhookProperties {
        batchLimit = batchLimit > 0 ? batchLimit : 100;
        backoffBaseSeconds = backoffBaseSeconds > 0 ? backoffBaseSeconds : 30;
        backoffCapSeconds = backoffCapSeconds > 0 ? backoffCapSeconds : 900;
        connectTimeoutMillis = connectTimeoutMillis > 0 ? connectTimeoutMillis : 2_000;
        readTimeoutMillis = readTimeoutMillis > 0 ? readTimeoutMillis : 10_000;
        requireHttps = requireHttps == null || requireHttps;
    }

    public boolean httpsRequired() {
        return Boolean.TRUE.equals(requireHttps);
    }
}
