/*
 * Maps the `property` table. Read-only in step 1; mutators arrive with the
 * content-sync use cases (build step 2).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "property")
public class PropertyJpaEntity {

    @Id
    private @Nullable UUID id;

    @Column(name = "account_id", nullable = false)
    private @Nullable UUID accountId;

    @Column(nullable = false, length = 200)
    private @Nullable String title;

    @Column(nullable = false, length = 3)
    private @Nullable String currency;

    @Column(nullable = false, length = 64)
    private @Nullable String timezone;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private @Nullable Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private @Nullable Instant updatedAt;

    protected PropertyJpaEntity() {} // JPA only

    public UUID id() {
        return Objects.requireNonNull(id);
    }

    public UUID accountId() {
        return Objects.requireNonNull(accountId);
    }

    public String title() {
        return Objects.requireNonNull(title);
    }

    public String currency() {
        return Objects.requireNonNull(currency);
    }

    public String timezone() {
        return Objects.requireNonNull(timezone);
    }
}
