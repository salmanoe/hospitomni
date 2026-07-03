/*
 * Relay tuning knobs (hospitomni.relay.*). The tick interval lives on the
 * @Scheduled annotation (hospitomni.relay.fixed-delay, default PT2S).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hospitomni.relay")
public record RelayProperties(long backoffBaseSeconds, int maxAttempts) {

    public RelayProperties {
        backoffBaseSeconds = backoffBaseSeconds > 0 ? backoffBaseSeconds : 30;
        maxAttempts = maxAttempts > 0 ? maxAttempts : 8;
    }
}
