/*
 * Binds the relay and reconciler tuning properties.
 *
 * @author Salman
 * @version 1.1
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.infrastructure.config;

import id.co.hospitomni.channel.application.ReconciliationProperties;
import id.co.hospitomni.channel.application.RelayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RelayProperties.class, ReconciliationProperties.class})
public class ChannelConfig {
}
