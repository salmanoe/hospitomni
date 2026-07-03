/*
 * Maps the `property` table.
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

    public PropertyJpaEntity(UUID id, UUID accountId, String title, String currency, String timezone) {
        this.id = id;
        this.accountId = accountId;
        this.title = title;
        this.currency = currency;
        this.timezone = timezone;
    }

    /** Content update; identity (id, accountId) is immutable. */
    public void updateContent(String title, String currency, String timezone) {
        this.title = title;
        this.currency = currency;
        this.timezone = timezone;
        this.updatedAt = Instant.now();
    }

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
