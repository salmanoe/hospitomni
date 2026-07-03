/*
 * Maps the `account` table. Field access; no setters — accounts are managed
 * via seed/ops for now, mutators arrive with the account-management use cases.
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
import java.util.UUID;

@Entity
@Table(name = "account")
public class AccountJpaEntity {

    @Id
    private @Nullable UUID id;

    @Column(nullable = false, length = 200)
    private @Nullable String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private @Nullable Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private @Nullable Instant updatedAt;

    protected AccountJpaEntity() {} // JPA only

    public UUID id() {
        return java.util.Objects.requireNonNull(id);
    }

    public String name() {
        return java.util.Objects.requireNonNull(name);
    }

    public boolean active() {
        return active;
    }
}
