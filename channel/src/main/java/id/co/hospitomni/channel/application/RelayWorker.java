/*
 * One transactional unit of relay work: claim the channel's due dirty cells
 * (FOR UPDATE SKIP LOCKED), re-read their CURRENT values, coalesce into a
 * range-compressed AriPush, deliver via the adapter registry, delete the
 * claimed rows. Any push failure rolls the whole unit back (claims release,
 * relay records backoff in a fresh transaction).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.application;

import id.co.hospitomni.channel.adapter.ota.OtaAdapterRegistry;
import id.co.hospitomni.channel.domain.model.AriPush;
import id.co.hospitomni.channel.domain.model.DirtyCell;
import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.channel.domain.model.RestrictionValues;
import id.co.hospitomni.channel.domain.port.out.AriValueReader;
import id.co.hospitomni.channel.domain.port.out.DirtyCellStore;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.RangeCompressor;
import id.co.hospitomni.shared.event.AriChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RelayWorker {

    private static final Logger log = LoggerFactory.getLogger(RelayWorker.class);

    private final DirtyCellStore dirtyCellStore;
    private final PropertyChannelRepository channelRepository;
    private final AriValueReader ariValueReader;
    private final OtaAdapterRegistry adapterRegistry;

    public RelayWorker(
            DirtyCellStore dirtyCellStore,
            PropertyChannelRepository channelRepository,
            AriValueReader ariValueReader,
            OtaAdapterRegistry adapterRegistry) {
        this.dirtyCellStore = dirtyCellStore;
        this.channelRepository = channelRepository;
        this.ariValueReader = ariValueReader;
        this.adapterRegistry = adapterRegistry;
    }

    @Transactional
    public void processChannel(PropertyChannelId channelId) {
        PropertyChannel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null || channel.paused()) {
            return; // Deleted or paused since the tick selected it.
        }
        List<DirtyCell> claimed = dirtyCellStore.claimDue(channelId);
        if (claimed.isEmpty()) {
            return; // Another relay instance grabbed them (SKIP LOCKED).
        }

        AriPush push = buildPush(channel, claimed);
        if (!push.isEmpty()) {
            adapterRegistry.byName(channel.otaName()).pushAri(push);
        }
        dirtyCellStore.delete(claimed);
        log.debug("Pushed {} availability + {} restriction range(s) to {} for property {}",
                push.availability().size(), push.restrictions().size(),
                channel.otaName(), channel.propertyId().value());
    }

    private AriPush buildPush(PropertyChannel channel, List<DirtyCell> claimed) {
        Map<UUID, List<LocalDate>> availabilityUnits = new LinkedHashMap<>();
        Map<UUID, List<LocalDate>> restrictionUnits = new LinkedHashMap<>();
        for (DirtyCell cell : claimed) {
            var target = cell.unit() == AriChangedEvent.Unit.AVAILABILITY
                    ? availabilityUnits : restrictionUnits;
            target.computeIfAbsent(cell.unitId(), k -> new ArrayList<>()).add(cell.date());
        }

        List<AriPush.AvailabilityRange> availabilityRanges = new ArrayList<>();
        availabilityUnits.forEach((roomTypeId, dates) -> {
            dates.sort(null);
            Map<LocalDate, Integer> values = ariValueReader.availabilityFor(roomTypeId, dates);
            List<Map.Entry<LocalDate, Integer>> present = dates.stream()
                    .filter(values::containsKey)
                    .map(d -> Map.entry(d, values.get(d)))
                    .toList();
            RangeCompressor.compress(present, Map.Entry::getKey, Map.Entry::getValue)
                    .forEach(run -> availabilityRanges.add(new AriPush.AvailabilityRange(
                            roomTypeId, run.from(), run.to(), run.value())));
        });

        List<AriPush.RestrictionRange> restrictionRanges = new ArrayList<>();
        restrictionUnits.forEach((ratePlanId, dates) -> {
            dates.sort(null);
            Map<LocalDate, RestrictionValues> values = ariValueReader.restrictionsFor(ratePlanId, dates);
            List<Map.Entry<LocalDate, RestrictionValues>> present = dates.stream()
                    .filter(values::containsKey)
                    .map(d -> Map.entry(d, values.get(d)))
                    .toList();
            RangeCompressor.compress(present, Map.Entry::getKey, Map.Entry::getValue)
                    .forEach(run -> restrictionRanges.add(new AriPush.RestrictionRange(
                            ratePlanId, run.from(), run.to(),
                            run.value().rate(), run.value().minStay(), run.value().maxStay(),
                            run.value().closedToArrival(), run.value().closedToDeparture(),
                            run.value().stopSell())));
        });

        return new AriPush(channel.propertyId(), channel.otaName(), channel.epoch(),
                availabilityRanges, restrictionRanges);
    }
}
