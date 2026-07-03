/*
 * Spring Data JPA repository for account rows.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.infrastructure.persistence;

import id.co.hospitomni.account.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {
}
