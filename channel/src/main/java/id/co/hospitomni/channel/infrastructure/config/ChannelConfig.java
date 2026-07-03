/*
 * Binds the relay tuning properties.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.infrastructure.config;

import id.co.hospitomni.channel.application.RelayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RelayProperties.class)
public class ChannelConfig {
}
