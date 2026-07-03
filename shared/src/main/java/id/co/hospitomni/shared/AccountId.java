/*
 * Typed identifier for an account — the tenant root (one per connected PMS realm).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

import java.util.UUID;

public record AccountId(UUID value) implements DomainId {
    public AccountId {
        Guard.notNull(value, "value");
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }
}
