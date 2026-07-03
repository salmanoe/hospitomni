/*
 * AccountRepository port implementation over Spring Data JPA.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.infrastructure.persistence;

import id.co.hospitomni.account.domain.model.Account;
import id.co.hospitomni.account.domain.port.out.AccountRepository;
import id.co.hospitomni.account.infrastructure.persistence.entity.AccountJpaEntity;
import id.co.hospitomni.shared.AccountId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;

    public AccountRepositoryAdapter(AccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpa.findById(id.value()).map(AccountRepositoryAdapter::toDomain);
    }

    private static Account toDomain(AccountJpaEntity entity) {
        return new Account(AccountId.of(entity.id()), entity.name(), entity.active());
    }
}
