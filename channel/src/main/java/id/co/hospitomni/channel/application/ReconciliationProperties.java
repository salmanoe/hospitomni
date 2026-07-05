/*
 * Reconciler tuning knobs (hospitomni.reconciliation.*). The tick interval
 * lives on the @Scheduled annotation (hospitomni.reconciliation.fixed-delay,
 * default PT6H; initial-delay default PT10M keeps the sweep out of startup).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hospitomni.reconciliation")
public record ReconciliationProperties(int horizonDays) {

    public ReconciliationProperties {
        horizonDays = horizonDays > 0 ? horizonDays : 90;
    }
}
