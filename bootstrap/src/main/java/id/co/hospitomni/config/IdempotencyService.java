/*
 * Claim/complete store behind the Idempotency-Key filter. A key is claimed
 * (row inserted, response columns NULL) before the write executes, so a
 * concurrent duplicate is caught while the first attempt is in flight; the
 * response is stored on completion and replayed verbatim for retries with
 * the same payload. 5xx outcomes release the claim instead of storing —
 * a server fault must stay retryable, never be replayed as the "result".
 *
 * Retention is bounded: a scheduled purge drops records older than 24h,
 * which comfortably covers any sane client retry horizon.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.config;

import id.co.hospitomni.account.domain.model.ApiKeyDigest;
import id.co.hospitomni.shared.AccountId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class IdempotencyService {

    /** Outcome of trying to claim a key for a write request. */
    public sealed interface Claim {
        /** First time this key is seen — proceed and complete/release afterwards. */
        record Acquired() implements Claim {}

        /** Another request with this key is still executing. */
        record InFlight() implements Claim {}

        /** Key reused with a different method/path/body. */
        record PayloadMismatch() implements Claim {}

        /** Key already completed with this exact payload — replay the stored response. */
        record Replay(int status, @Nullable String contentType, String body) implements Claim {}
    }

    private final JdbcClient jdbc;

    public IdempotencyService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Hashes the request fingerprint (method + path + body) for payload pinning. */
    public static String requestHash(String method, String pathAndQuery, String body) {
        return ApiKeyDigest.sha256Hex(method + '\n' + pathAndQuery + '\n' + body);
    }

    public Claim claim(AccountId accountId, String key, String requestHash) {
        int inserted = jdbc.sql("""
                        INSERT INTO idempotency_record (id, account_id, idem_key, request_hash)
                        VALUES (:id, :accountId, :key, :hash)
                        ON CONFLICT (account_id, idem_key) DO NOTHING""")
                .param("id", UUID.randomUUID())
                .param("accountId", accountId.value())
                .param("key", key)
                .param("hash", requestHash)
                .update();
        if (inserted == 1) {
            return new Claim.Acquired();
        }

        Optional<Claim> existing = jdbc.sql("""
                        SELECT request_hash, response_status, response_content_type, response_body
                        FROM idempotency_record
                        WHERE account_id = :accountId AND idem_key = :key""")
                .param("accountId", accountId.value())
                .param("key", key)
                .query((rs, rowNum) -> {
                    if (!requestHash.equals(rs.getString("request_hash"))) {
                        return (Claim) new Claim.PayloadMismatch();
                    }
                    int status = rs.getInt("response_status");
                    if (rs.wasNull()) {
                        return new Claim.InFlight();
                    }
                    return new Claim.Replay(
                            status,
                            rs.getString("response_content_type"),
                            Objects.requireNonNullElse(rs.getString("response_body"), ""));
                })
                .optional();
        // Insert conflicted but the row is gone: the purge job removed it in
        // between. The key is free again — claim it on the retry path.
        return existing.orElseGet(() -> claim(accountId, key, requestHash));
    }

    /** Stores the outcome; a 5xx releases the claim so the client can retry. */
    public void complete(
            AccountId accountId, String key, int status, @Nullable String contentType, String body) {
        if (status >= 500) {
            release(accountId, key);
            return;
        }
        jdbc.sql("""
                        UPDATE idempotency_record
                        SET response_status = :status, response_content_type = :contentType,
                            response_body = :body, completed_at = now()
                        WHERE account_id = :accountId AND idem_key = :key""")
                .param("status", status)
                .param("contentType", contentType)
                .param("body", body)
                .param("accountId", accountId.value())
                .param("key", key)
                .update();
    }

    /** Frees the key (write failed before producing a storable response). */
    public void release(AccountId accountId, String key) {
        jdbc.sql("DELETE FROM idempotency_record WHERE account_id = :accountId AND idem_key = :key")
                .param("accountId", accountId.value())
                .param("key", key)
                .update();
    }

    @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void purgeExpired() {
        jdbc.sql("DELETE FROM idempotency_record WHERE created_at < now() - INTERVAL '24 hours'")
                .update();
    }
}
