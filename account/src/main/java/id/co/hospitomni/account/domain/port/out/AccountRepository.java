/*
 * Persistence port for account lookup.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.domain.port.out;

import id.co.hospitomni.account.domain.model.Account;
import id.co.hospitomni.shared.AccountId;

import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findById(AccountId id);
}
