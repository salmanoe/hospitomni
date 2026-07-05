/*
 * UnitMappingRepository over channel_unit_mapping (V8). Replace is
 * delete-then-batch-insert inside the caller's transaction; existence
 * lookups query the property module's tables directly (same pattern as
 * the relay's ARI reads).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.infrastructure.persistence;

import id.co.hospitomni.channel.domain.model.UnitMapping;
import id.co.hospitomni.channel.domain.port.out.UnitMappingRepository;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class UnitMappingJdbcRepository implements UnitMappingRepository {

    private final JdbcTemplate jdbc;

    public UnitMappingJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replaceAll(PropertyChannelId channelId, List<UnitMapping> mappings) {
        jdbc.update("DELETE FROM channel_unit_mapping WHERE property_channel_id = ?",
                channelId.value());
        jdbc.batchUpdate("""
                        INSERT INTO channel_unit_mapping
                            (property_channel_id, unit_type, unit_id, ota_code)
                        VALUES (?, ?, ?, ?)""",
                mappings, mappings.size(),
                (PreparedStatement ps, UnitMapping mapping) -> {
                    ps.setObject(1, channelId.value());
                    ps.setString(2, mapping.unitType().name());
                    ps.setObject(3, mapping.unitId());
                    ps.setString(4, mapping.otaCode());
                });
    }

    @Override
    public List<UnitMapping> findAll(PropertyChannelId channelId) {
        return jdbc.query("""
                        SELECT unit_type, unit_id, ota_code FROM channel_unit_mapping
                        WHERE property_channel_id = ? ORDER BY unit_type, ota_code""",
                (rs, i) -> new UnitMapping(
                        UnitMapping.UnitType.valueOf(rs.getString("unit_type")),
                        rs.getObject("unit_id", UUID.class),
                        rs.getString("ota_code")),
                channelId.value());
    }

    @Override
    public Set<UUID> existingRoomTypeIds(PropertyId propertyId, Collection<UUID> ids) {
        return existingIds("room_type", propertyId, ids);
    }

    @Override
    public Set<UUID> existingRatePlanIds(PropertyId propertyId, Collection<UUID> ids) {
        return existingIds("rate_plan", propertyId, ids);
    }

    private Set<UUID> existingIds(String table, PropertyId propertyId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        // Table name comes from the two fixed call sites above, never input.
        List<UUID> found = jdbc.query(
                "SELECT id FROM " + table + " WHERE property_id = ? AND id = ANY(?)",
                ps -> {
                    ps.setObject(1, propertyId.value());
                    ps.setArray(2, ps.getConnection().createArrayOf("uuid", ids.toArray()));
                },
                (rs, i) -> rs.getObject("id", UUID.class));
        return new HashSet<>(found);
    }
}
