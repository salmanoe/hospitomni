/*
 * Spring Data JPA repository for api_key rows.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.infrastructure.persistence;

import id.co.hospitomni.account.infrastructure.persistence.entity.ApiKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKeyJpaEntity, UUID> {

    Optional<ApiKeyJpaEntity> findByKeyHashAndRevokedAtIsNull(String keyHash);
}
