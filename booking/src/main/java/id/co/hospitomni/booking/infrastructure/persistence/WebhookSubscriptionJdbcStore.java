/*
 * WebhookSubscriptionStore over webhook_subscription (V10). High-churn
 * delivery state → JdbcClient + SQL per the persistence-split decision.
 * The creation INSERT seeds the cursor at the current stream tip in the
 * same statement, so no event between "read tip" and "insert" is skipped.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.infrastructure.persistence;

import id.co.hospitomni.booking.domain.model.WebhookSubscription;
import id.co.hospitomni.booking.domain.port.out.WebhookSubscriptionStore;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.WebhookId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WebhookSubscriptionJdbcStore implements WebhookSubscriptionStore {

    private static final String SUBSCRIPTION_COLUMNS = """
            id, account_id, url, active, last_delivered_seq, consecutive_failures,
            last_error, last_success_at, created_at""";

    private static final String PENDING_COLUMN = """
            (SELECT COUNT(*) FROM booking_revision br
             WHERE br.account_id = ws.account_id
               AND br.seq > ws.last_delivered_seq) AS pending_events""";

    private final JdbcClient jdbc;

    public WebhookSubscriptionJdbcStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WebhookSubscription create(
            AccountId accountId, String url, byte[] secretCiphertext, String secretKeyId) {
        return jdbc.sql("""
                        INSERT INTO webhook_subscription
                            (id, account_id, url, secret_ciphertext, secret_key_id,
                             last_delivered_seq)
                        VALUES (:id, :accountId, :url, :ciphertext, :keyId,
                                (SELECT COALESCE(MAX(seq), 0) FROM booking_revision))
                        RETURNING\s""" + SUBSCRIPTION_COLUMNS)
                .param("id", WebhookId.generate().value())
                .param("accountId", accountId.value())
                .param("url", url)
                .param("ciphertext", secretCiphertext)
                .param("keyId", secretKeyId)
                .query(WebhookSubscriptionJdbcStore::mapSubscription)
                .single();
    }

    @Override
    public List<SubscriptionWithPending> listForAccount(AccountId accountId) {
        return jdbc.sql("SELECT " + SUBSCRIPTION_COLUMNS + ", " + PENDING_COLUMN + """
                        \sFROM webhook_subscription ws
                        WHERE ws.account_id = :accountId ORDER BY ws.created_at""")
                .param("accountId", accountId.value())
                .query(WebhookSubscriptionJdbcStore::mapWithPending)
                .list();
    }

    @Override
    public Optional<SubscriptionWithPending> findWithPending(AccountId accountId, WebhookId id) {
        return jdbc.sql("SELECT " + SUBSCRIPTION_COLUMNS + ", " + PENDING_COLUMN + """
                        \sFROM webhook_subscription ws
                        WHERE ws.account_id = :accountId AND ws.id = :id""")
                .param("accountId", accountId.value())
                .param("id", id.value())
                .query(WebhookSubscriptionJdbcStore::mapWithPending)
                .optional();
    }

    @Override
    public void replaceSecret(WebhookId id, byte[] secretCiphertext, String secretKeyId) {
        jdbc.sql("""
                        UPDATE webhook_subscription
                        SET secret_ciphertext = :ciphertext, secret_key_id = :keyId,
                            updated_at = NOW()
                        WHERE id = :id""")
                .param("ciphertext", secretCiphertext)
                .param("keyId", secretKeyId)
                .param("id", id.value())
                .update();
    }

    @Override
    public void setActive(WebhookId id, boolean active) {
        jdbc.sql("""
                        UPDATE webhook_subscription
                        SET active = :active,
                            consecutive_failures =
                                CASE WHEN :active THEN 0 ELSE consecutive_failures END,
                            next_attempt_at = CASE WHEN :active THEN NOW() ELSE next_attempt_at END,
                            last_error = CASE WHEN :active THEN NULL ELSE last_error END,
                            updated_at = NOW()
                        WHERE id = :id""")
                .param("active", active)
                .param("id", id.value())
                .update();
    }

    @Override
    public void delete(WebhookId id) {
        jdbc.sql("DELETE FROM webhook_subscription WHERE id = :id")
                .param("id", id.value())
                .update();
    }

    @Override
    public List<WebhookId> dueSubscriptionIds() {
        return jdbc.sql("""
                        SELECT id FROM webhook_subscription
                        WHERE active AND next_attempt_at <= NOW()""")
                .query((rs, rowNum) -> WebhookId.of(rs.getObject("id", UUID.class)))
                .list();
    }

    @Override
    public Optional<ClaimedSubscription> claim(WebhookId id) {
        return jdbc.sql("SELECT " + SUBSCRIPTION_COLUMNS + """
                        , secret_ciphertext, secret_key_id
                        FROM webhook_subscription
                        WHERE id = :id
                        FOR UPDATE SKIP LOCKED""")
                .param("id", id.value())
                .query((rs, rowNum) -> new ClaimedSubscription(
                        mapSubscription(rs, rowNum),
                        rs.getBytes("secret_ciphertext"),
                        rs.getString("secret_key_id")))
                .optional();
    }

    @Override
    public void recordSuccess(WebhookId id, long lastDeliveredSeq) {
        jdbc.sql("""
                        UPDATE webhook_subscription
                        SET last_delivered_seq = :seq, consecutive_failures = 0,
                            last_error = NULL, last_success_at = NOW(),
                            next_attempt_at = NOW(), updated_at = NOW()
                        WHERE id = :id""")
                .param("seq", lastDeliveredSeq)
                .param("id", id.value())
                .update();
    }

    @Override
    public void recordFailure(
            WebhookId id, String error, long backoffBaseSeconds, long backoffCapSeconds) {
        jdbc.sql("""
                        UPDATE webhook_subscription
                        SET consecutive_failures = consecutive_failures + 1,
                            next_attempt_at = NOW() + make_interval(secs => LEAST(
                                :base * POWER(2, LEAST(consecutive_failures, 5)), :cap)),
                            last_error = LEFT(:error, 500),
                            updated_at = NOW()
                        WHERE id = :id""")
                .param("base", backoffBaseSeconds)
                .param("cap", backoffCapSeconds)
                .param("error", error)
                .param("id", id.value())
                .update();
    }

    private static WebhookSubscription mapSubscription(ResultSet rs, int rowNum)
            throws SQLException {
        return new WebhookSubscription(
                WebhookId.of(rs.getObject("id", UUID.class)),
                AccountId.of(rs.getObject("account_id", UUID.class)),
                rs.getString("url"),
                rs.getBoolean("active"),
                rs.getLong("last_delivered_seq"),
                rs.getInt("consecutive_failures"),
                rs.getString("last_error"),
                instant(rs, "last_success_at"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static SubscriptionWithPending mapWithPending(ResultSet rs, int rowNum)
            throws SQLException {
        return new SubscriptionWithPending(
                mapSubscription(rs, rowNum), rs.getLong("pending_events"));
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
