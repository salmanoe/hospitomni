/*
 * Maps the `api_key` table — hash-only credential storage.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "api_key")
public class ApiKeyJpaEntity {

    @Id
    private @Nullable UUID id;

    @Column(name = "account_id", nullable = false)
    private @Nullable UUID accountId;

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private @Nullable String keyHash;

    @Column(nullable = false, length = 200)
    private @Nullable String label;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private @Nullable Instant createdAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    protected ApiKeyJpaEntity() {} // JPA only

    public UUID id() {
        return Objects.requireNonNull(id);
    }

    public UUID accountId() {
        return Objects.requireNonNull(accountId);
    }

    public String keyHash() {
        return Objects.requireNonNull(keyHash);
    }

    public String label() {
        return Objects.requireNonNull(label);
    }

    public Instant createdAt() {
        return Objects.requireNonNull(createdAt);
    }

    public @Nullable Instant revokedAt() {
        return revokedAt;
    }
}
