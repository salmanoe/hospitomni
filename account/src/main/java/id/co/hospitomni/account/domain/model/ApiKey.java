/*
 * An API key credential — only the SHA-256 digest is ever held; the raw key
 * exists once, at issue time. revoked_at supports revocation from day one.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.account.domain.model;

import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.ApiKeyId;
import id.co.hospitomni.shared.Guard;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record ApiKey(
        ApiKeyId id,
        AccountId accountId,
        String keyHash,
        String label,
        Instant createdAt,
        @Nullable Instant revokedAt) {

    public ApiKey {
        Guard.notNull(id, "id");
        Guard.notNull(accountId, "accountId");
        Guard.notBlank(keyHash, "keyHash");
        Guard.isTrue(keyHash.length() == 64, "keyHash must be a SHA-256 hex digest (64 chars)");
        Guard.notBlank(label, "label");
        Guard.notNull(createdAt, "createdAt");
    }

    public boolean revoked() {
        return revokedAt != null;
    }
}
