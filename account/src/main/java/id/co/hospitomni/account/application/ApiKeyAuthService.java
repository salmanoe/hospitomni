/*
 * Resolves a raw user-api-key header value to the owning account:
 * SHA-256 the raw key → indexed hash lookup (non-revoked) → account must be
 * active. Constant work regardless of miss/revoked/inactive — no oracle.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.application;

import id.co.hospitomni.account.domain.model.ApiKeyDigest;
import id.co.hospitomni.account.domain.port.out.AccountRepository;
import id.co.hospitomni.account.domain.port.out.ApiKeyRepository;
import id.co.hospitomni.shared.AccountId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ApiKeyAuthService {

    private final ApiKeyRepository apiKeyRepository;
    private final AccountRepository accountRepository;

    public ApiKeyAuthService(ApiKeyRepository apiKeyRepository, AccountRepository accountRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.accountRepository = accountRepository;
    }

    /** Returns the account the key belongs to, or empty for unknown/revoked/inactive. */
    @Transactional(readOnly = true)
    public Optional<AccountId> authenticate(String rawKey) {
        if (rawKey.isBlank()) {
            return Optional.empty();
        }
        return apiKeyRepository.findActiveByKeyHash(ApiKeyDigest.sha256Hex(rawKey))
                .flatMap(key -> accountRepository.findById(key.accountId()))
                .filter(account -> account.active())
                .map(account -> account.id());
    }
}
