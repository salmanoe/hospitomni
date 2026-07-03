/*
 * An account — one per connected PMS realm (tenant root; all data hangs off it).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.domain.model;

import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.Guard;

public record Account(AccountId id, String name, boolean active) {

    public Account {
        Guard.notNull(id, "id");
        Guard.notBlank(name, "name");
        Guard.maxLength(name, 200, "name");
    }
}
