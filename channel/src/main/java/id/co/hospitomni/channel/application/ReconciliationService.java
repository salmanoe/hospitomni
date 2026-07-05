/*
 * The drift-correction engine: diff HospitOmni's intended ARI against what
 * the OTA actually holds, and self-heal through a fenced full refresh —
 * bump the channel epoch (older in-flight deltas can't land after it) and
 * re-mark the whole window dirty so the relay re-pushes current values.
 *
 * Drift is asymmetric by design: only nights HospitOmni has set are
 * compared. A value the OTA holds for a night we never set can't be healed
 * by a push and is the OTA's business (matches the "PMS value wins on
 * conflict" rule — for values the PMS actually holds).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.adapter.ota.OtaAdapterRegistry;
import id.co.hospitomni.channel.domain.model.DriftCell;
import id.co.hospitomni.channel.domain.model.OtaAriState;
import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.channel.domain.model.ReconciliationRun;
import id.co.hospitomni.channel.domain.model.RestrictionValues;
import id.co.hospitomni.channel.domain.port.out.AriValueReader;
import id.co.hospitomni.channel.domain.port.out.DirtyCellStore;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.channel.domain.port.out.ReconciliationRunStore;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.event.AriChangedEvent.Unit;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    /** Drift cells carried on the API response / stored sample. */
    static final int SAMPLE_LIMIT = 20;
    static final int MAX_WINDOW_DAYS = 400;

    public record Result(ReconciliationRun run, List<DriftCell> sample) {
    }

    private final PropertyChannelRepository channelRepository;
    private final AriValueReader ariValueReader;
    private final OtaAdapterRegistry adapterRegistry;
    private final DirtyCellStore dirtyCellStore;
    private final ReconciliationRunStore runStore;

    public ReconciliationService(
            PropertyChannelRepository channelRepository,
            AriValueReader ariValueReader,
            OtaAdapterRegistry adapterRegistry,
            DirtyCellStore dirtyCellStore,
            ReconciliationRunStore runStore) {
        this.channelRepository = channelRepository;
        this.ariValueReader = ariValueReader;
        this.adapterRegistry = adapterRegistry;
        this.dirtyCellStore = dirtyCellStore;
        this.runStore = runStore;
    }

    /** API entry — tenant-scoped. */
    @Transactional
    public Result reconcileOwned(PropertyChannelId channelId, LocalDate from, LocalDate to) {
        return reconcile(requireOwnedChannel(channelId), from, to);
    }

    /** API readback — tenant-scoped. */
    @Transactional(readOnly = true)
    public List<ReconciliationRun> recentRunsOwned(PropertyChannelId channelId, int limit) {
        requireOwnedChannel(channelId);
        return runStore.recentRuns(channelId, limit);
    }

    /** Core engine — also driven per channel by the scheduler (no tenant scope). */
    @Transactional
    public Result reconcile(PropertyChannel channel, LocalDate from, LocalDate to) {
        Guard.isTrue(!to.isBefore(from), "date_to must not be before date_from");
        Guard.isTrue(ChronoUnit.DAYS.between(from, to) < MAX_WINDOW_DAYS,
                "reconciliation window must span fewer than " + MAX_WINDOW_DAYS + " days");

        List<LocalDate> dates = from.datesUntil(to.plusDays(1)).toList();
        List<UUID> roomTypeIds = ariValueReader.roomTypeIdsOf(channel.propertyId());
        List<UUID> ratePlanIds = ariValueReader.ratePlanIdsOf(channel.propertyId());
        OtaAriState observed = adapterRegistry.byName(channel.otaName())
                .fetchAri(channel.propertyId(), from, to);

        List<DriftCell> drift = new ArrayList<>();
        for (UUID roomTypeId : roomTypeIds) {
            Map<LocalDate, Integer> otaSide =
                    observed.availability().getOrDefault(roomTypeId, Map.of());
            ariValueReader.availabilityFor(roomTypeId, dates).forEach((date, intended) -> {
                Integer seen = otaSide.get(date);
                if (!intended.equals(seen)) {
                    drift.add(new DriftCell(Unit.AVAILABILITY, roomTypeId, date,
                            String.valueOf(intended), seen == null ? null : String.valueOf(seen)));
                }
            });
        }
        for (UUID ratePlanId : ratePlanIds) {
            Map<LocalDate, RestrictionValues> otaSide =
                    observed.restrictions().getOrDefault(ratePlanId, Map.of());
            ariValueReader.restrictionsFor(ratePlanId, dates).forEach((date, intended) -> {
                RestrictionValues seen = otaSide.get(date);
                if (!intended.equals(seen)) {
                    drift.add(new DriftCell(Unit.RESTRICTION, ratePlanId, date,
                            intended.toString(), seen == null ? null : seen.toString()));
                }
            });
        }
        drift.sort(Comparator.comparing(DriftCell::unit)
                .thenComparing(DriftCell::unitId)
                .thenComparing(DriftCell::date));

        boolean refreshed = !drift.isEmpty();
        long epoch = channel.epoch();
        if (refreshed) {
            epoch = channelRepository.bumpEpoch(channel.id());
            for (UUID roomTypeId : roomTypeIds) {
                dirtyCellStore.mark(List.of(channel.id()), Unit.AVAILABILITY, roomTypeId, from, to);
            }
            for (UUID ratePlanId : ratePlanIds) {
                dirtyCellStore.mark(List.of(channel.id()), Unit.RESTRICTION, ratePlanId, from, to);
            }
        }
        channelRepository.recordReconciliation(channel.id(), drift.size());

        List<DriftCell> sample = List.copyOf(
                drift.subList(0, Math.min(SAMPLE_LIMIT, drift.size())));
        ReconciliationRun run = runStore.record(
                channel.id(), from, to, drift.size(), refreshed, epoch, renderSample(sample));
        return new Result(run, sample);
    }

    private PropertyChannel requireOwnedChannel(PropertyChannelId channelId) {
        PropertyChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("PropertyChannel", channelId.value()));
        if (!channelRepository.propertyOwnedBy(channel.propertyId(), AccountContext.current())) {
            throw new ResourceNotFoundException("PropertyChannel", channelId.value());
        }
        return channel;
    }

    private static @Nullable String renderSample(List<DriftCell> sample) {
        if (sample.isEmpty()) {
            return null;
        }
        return sample.stream()
                .map(cell -> "%s %s %s expected=%s observed=%s".formatted(
                        cell.unit(), cell.unitId(), cell.date(), cell.expected(), cell.observed()))
                .collect(Collectors.joining("\n"));
    }
}
