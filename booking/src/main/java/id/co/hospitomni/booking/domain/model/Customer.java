/*
 * Guest contact snapshot — the MINIMUM the PMS contract requires (UU PDP:
 * no extra PII is persisted, and none of these fields may reach logs,
 * dead-letter rows, or audit tables).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.domain.model;

import id.co.hospitomni.shared.Guard;
import org.jspecify.annotations.Nullable;

public record Customer(
        String name,
        @Nullable String surname,
        @Nullable String mail,
        @Nullable String phone,
        @Nullable String country) {

    public Customer {
        Guard.notBlank(name, "name");
    }
}
