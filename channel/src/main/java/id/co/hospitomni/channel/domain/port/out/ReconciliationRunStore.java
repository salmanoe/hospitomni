/*
 * Persistence for reconciliation runs — the audit trail behind
 * GET /reconciliations and the migration-parity evidence for cutovers.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.domain.port.out;

import id.co.hospitomni.channel.domain.model.ReconciliationRun;
import id.co.hospitomni.shared.PropertyChannelId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

public interface ReconciliationRunStore {

    ReconciliationRun record(
            PropertyChannelId channelId, LocalDate dateFrom, LocalDate dateTo,
            int driftCount, boolean refreshed, long epoch, @Nullable String sampleDrift);

    /** Most recent runs first. */
    List<ReconciliationRun> recentRuns(PropertyChannelId channelId, int limit);
}
