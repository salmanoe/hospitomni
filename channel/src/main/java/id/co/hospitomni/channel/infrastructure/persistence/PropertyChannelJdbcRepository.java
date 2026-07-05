/*
 * PropertyChannelRepository over the property_channel table (V1 schema +
 * V4 epoch column).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.infrastructure.persistence;

import id.co.hospitomni.channel.domain.model.PropertyChannel;
import id.co.hospitomni.channel.domain.port.out.PropertyChannelRepository;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PropertyChannelJdbcRepository implements PropertyChannelRepository {

    private static final String CHANNEL_COLUMNS =
            "id, property_id, ota_name, paused, epoch, "
                    + "credentials_ciphertext IS NOT NULL AS has_credentials";

    private static final RowMapper<PropertyChannel> MAPPER = (rs, i) -> new PropertyChannel(
            PropertyChannelId.of(rs.getObject("id", UUID.class)),
            PropertyId.of(rs.getObject("property_id", UUID.class)),
            rs.getString("ota_name"),
            rs.getBoolean("paused"),
            rs.getLong("epoch"),
            rs.getBoolean("has_credentials"));

    private final JdbcTemplate jdbc;

    public PropertyChannelJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PropertyChannel create(PropertyId propertyId, String otaName) {
        PropertyChannelId id = PropertyChannelId.generate();
        jdbc.update("INSERT INTO property_channel (id, property_id, ota_name) VALUES (?, ?, ?)",
                id.value(), propertyId.value(), otaName);
        return new PropertyChannel(id, propertyId, otaName, false, 0, false);
    }

    @Override
    public Optional<PropertyChannel> findById(PropertyChannelId id) {
        return jdbc.query(
                        "SELECT " + CHANNEL_COLUMNS + " FROM property_channel WHERE id = ?",
                        MAPPER, id.value())
                .stream().findFirst();
    }

    @Override
    public List<PropertyChannel> findAllByProperty(PropertyId propertyId) {
        return jdbc.query(
                "SELECT " + CHANNEL_COLUMNS + " FROM property_channel "
                        + "WHERE property_id = ? ORDER BY ota_name",
                MAPPER, propertyId.value());
    }

    @Override
    public List<PropertyChannelId> findChannelIdsByProperty(PropertyId propertyId) {
        return jdbc.query(
                "SELECT id FROM property_channel WHERE property_id = ?",
                (rs, i) -> PropertyChannelId.of(rs.getObject("id", UUID.class)),
                propertyId.value());
    }

    @Override
    public void setPaused(PropertyChannelId id, boolean paused) {
        jdbc.update("UPDATE property_channel SET paused = ?, updated_at = NOW() WHERE id = ?",
                paused, id.value());
    }

    @Override
    public void recordPushSuccess(PropertyChannelId id) {
        jdbc.update("UPDATE property_channel SET last_push_at = NOW(), last_push_error = NULL "
                + "WHERE id = ?", id.value());
    }

    @Override
    public void recordPushError(PropertyChannelId id, String error) {
        jdbc.update("UPDATE property_channel SET last_push_error = ? WHERE id = ?",
                error, id.value());
    }

    @Override
    public boolean propertyOwnedBy(PropertyId propertyId, AccountId accountId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM property WHERE id = ? AND account_id = ?)",
                Boolean.class, propertyId.value(), accountId.value()));
    }

    @Override
    public List<PropertyChannel> findAllUnpaused() {
        return jdbc.query(
                "SELECT " + CHANNEL_COLUMNS + " FROM property_channel "
                        + "WHERE NOT paused ORDER BY id",
                MAPPER);
    }

    @Override
    public long bumpEpoch(PropertyChannelId id) {
        Long epoch = jdbc.queryForObject(
                "UPDATE property_channel SET epoch = epoch + 1, updated_at = NOW() "
                        + "WHERE id = ? RETURNING epoch",
                Long.class, id.value());
        if (epoch == null) {
            throw new IllegalStateException("Epoch bump matched no channel: " + id.value());
        }
        return epoch;
    }

    @Override
    public void recordReconciliation(PropertyChannelId id, int driftCount) {
        jdbc.update("UPDATE property_channel SET last_reconciled_at = NOW(), "
                        + "last_drift_count = ? WHERE id = ?",
                driftCount, id.value());
    }
}
