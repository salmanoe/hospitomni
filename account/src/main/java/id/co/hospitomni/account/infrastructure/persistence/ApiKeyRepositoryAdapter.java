/*
 * ApiKeyRepository port implementation over Spring Data JPA.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.infrastructure.persistence;

import id.co.hospitomni.account.domain.model.ApiKey;
import id.co.hospitomni.account.domain.port.out.ApiKeyRepository;
import id.co.hospitomni.account.infrastructure.persistence.entity.ApiKeyJpaEntity;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.ApiKeyId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ApiKeyRepositoryAdapter implements ApiKeyRepository {

    private final ApiKeyJpaRepository jpa;

    public ApiKeyRepositoryAdapter(ApiKeyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ApiKey> findActiveByKeyHash(String keyHash) {
        return jpa.findByKeyHashAndRevokedAtIsNull(keyHash).map(ApiKeyRepositoryAdapter::toDomain);
    }

    private static ApiKey toDomain(ApiKeyJpaEntity entity) {
        return new ApiKey(
                ApiKeyId.of(entity.id()),
                AccountId.of(entity.accountId()),
                entity.keyHash(),
                entity.label(),
                entity.createdAt(),
                entity.revokedAt());
    }
}
