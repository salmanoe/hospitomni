/*
 * Persistence port for API-key lookup — the auth filter's single query path.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.domain.port.out;

import id.co.hospitomni.account.domain.model.ApiKey;

import java.util.Optional;

public interface ApiKeyRepository {

    /** Finds a non-revoked key by its SHA-256 hex digest. */
    Optional<ApiKey> findActiveByKeyHash(String keyHash);
}
