/*
 * Range-compressed ARI reads: per-day cells are grouped per room type /
 * rate plan and collapsed into runs of consecutive dates with equal values.
 * The restrictions read supports a field projection (?fields=rate,min_stay):
 * unselected fields are nulled BEFORE compression so runs merge on the
 * selected fields only.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.application;

import id.co.hospitomni.ari.domain.model.AvailabilityCell;
import id.co.hospitomni.shared.RangeCompressor;
import id.co.hospitomni.ari.domain.model.RestrictionCell;
import id.co.hospitomni.ari.domain.model.RestrictionFields;
import id.co.hospitomni.ari.domain.port.out.AvailabilityStore;
import id.co.hospitomni.ari.domain.port.out.ContentCatalog;
import id.co.hospitomni.ari.domain.port.out.RestrictionStore;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AriReadService {

    /** One compressed availability run. */
    public record AvailabilityRange(
            PropertyId propertyId, RoomTypeId roomTypeId,
            LocalDate dateFrom, LocalDate dateTo, int availability) {
    }

    /** One compressed restriction run (fields already projected). */
    public record RestrictionRange(
            PropertyId propertyId, RatePlanId ratePlanId,
            LocalDate dateFrom, LocalDate dateTo, RestrictionFields fields) {
    }

    public static final Set<String> RESTRICTION_FIELDS = Set.of(
            "rate", "min_stay", "max_stay", "closed_to_arrival", "closed_to_departure", "stop_sell");

    private final AvailabilityStore availabilityStore;
    private final RestrictionStore restrictionStore;
    private final ContentCatalog contentCatalog;

    public AriReadService(
            AvailabilityStore availabilityStore,
            RestrictionStore restrictionStore,
            ContentCatalog contentCatalog) {
        this.availabilityStore = availabilityStore;
        this.restrictionStore = restrictionStore;
        this.contentCatalog = contentCatalog;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityRange> readAvailability(
            PropertyId propertyId, @Nullable LocalDate dateGte, @Nullable LocalDate dateLte) {
        requireOwnedProperty(propertyId);
        List<AvailabilityCell> cells = availabilityStore.read(propertyId, dateGte, dateLte);

        Map<RoomTypeId, List<AvailabilityCell>> byRoomType = groupPreservingOrder(
                cells, AvailabilityCell::roomTypeId);
        List<AvailabilityRange> ranges = new ArrayList<>();
        byRoomType.forEach((roomTypeId, group) ->
                RangeCompressor.compress(group, AvailabilityCell::date, AvailabilityCell::availability)
                        .forEach(run -> ranges.add(new AvailabilityRange(
                                propertyId, roomTypeId, run.from(), run.to(), run.value()))));
        return ranges;
    }

    @Transactional(readOnly = true)
    public List<RestrictionRange> readRestrictions(
            PropertyId propertyId, @Nullable LocalDate dateGte, @Nullable LocalDate dateLte,
            @Nullable Set<String> fields) {
        requireOwnedProperty(propertyId);
        List<RestrictionCell> cells = restrictionStore.read(propertyId, dateGte, dateLte);

        Map<RatePlanId, List<RestrictionCell>> byRatePlan = groupPreservingOrder(
                cells, RestrictionCell::ratePlanId);
        List<RestrictionRange> ranges = new ArrayList<>();
        byRatePlan.forEach((ratePlanId, group) ->
                RangeCompressor.compress(group, RestrictionCell::date,
                                cell -> project(cell.fields(), fields))
                        .forEach(run -> ranges.add(new RestrictionRange(
                                propertyId, ratePlanId, run.from(), run.to(), run.value()))));
        return ranges;
    }

    private static RestrictionFields project(RestrictionFields all, @Nullable Set<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return all;
        }
        return new RestrictionFields(
                fields.contains("rate") ? all.rate() : null,
                fields.contains("min_stay") ? all.minStay() : null,
                fields.contains("max_stay") ? all.maxStay() : null,
                fields.contains("closed_to_arrival") ? all.closedToArrival() : null,
                fields.contains("closed_to_departure") ? all.closedToDeparture() : null,
                fields.contains("stop_sell") ? all.stopSell() : null);
    }

    private void requireOwnedProperty(PropertyId propertyId) {
        if (!contentCatalog.propertyOwnedBy(propertyId, AccountContext.current())) {
            throw new ResourceNotFoundException("Property", propertyId.value());
        }
    }

    private static <C, K> Map<K, List<C>> groupPreservingOrder(
            List<C> cells, java.util.function.Function<C, K> keyOf) {
        Map<K, List<C>> grouped = new LinkedHashMap<>();
        for (C cell : cells) {
            grouped.computeIfAbsent(keyOf.apply(cell), k -> new ArrayList<>()).add(cell);
        }
        return grouped;
    }
}
