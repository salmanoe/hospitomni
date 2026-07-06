/*
 * Webhook subscription management. The signing secret is generated
 * server-side, returned exactly once at registration (and on rotation),
 * and stored AES-GCM sealed — after that response it is unrecoverable
 * through the API. Rotation exists so a leaked secret never forces
 * delete+recreate, which would reset the cursor and skip events.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.application;

import id.co.hospitomni.booking.domain.model.WebhookSubscription;
import id.co.hospitomni.booking.domain.port.out.WebhookSubscriptionStore;
import id.co.hospitomni.booking.domain.port.out.WebhookSubscriptionStore.SubscriptionWithPending;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.WebhookId;
import id.co.hospitomni.shared.crypto.SecretCipher;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class WebhookService {

    /** Subscription plus the plaintext secret — exists only in this response. */
    public record RegisteredWebhook(WebhookSubscription subscription, String secret) {
    }

    private final WebhookSubscriptionStore store;
    private final SecretCipher cipher;
    private final WebhookProperties properties;
    private final SecureRandom random = new SecureRandom();

    public WebhookService(
            WebhookSubscriptionStore store, SecretCipher cipher, WebhookProperties properties) {
        this.store = store;
        this.cipher = cipher;
        this.properties = properties;
    }

    @Transactional
    public RegisteredWebhook register(String url) {
        requireValidUrl(url);
        String secret = newSecret();
        WebhookSubscription subscription = store.create(
                AccountContext.current(), url, cipher.encrypt(secret), cipher.keyId());
        return new RegisteredWebhook(subscription, secret);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionWithPending> list() {
        return store.listForAccount(AccountContext.current());
    }

    @Transactional
    public RegisteredWebhook rotateSecret(WebhookId id) {
        SubscriptionWithPending owned = requireOwned(id);
        String secret = newSecret();
        store.replaceSecret(id, cipher.encrypt(secret), cipher.keyId());
        return new RegisteredWebhook(owned.subscription(), secret);
    }

    @Transactional
    public SubscriptionWithPending setActive(WebhookId id, boolean active) {
        requireOwned(id);
        store.setActive(id, active);
        return requireOwned(id);
    }

    @Transactional
    public void delete(WebhookId id) {
        requireOwned(id);
        store.delete(id);
    }

    private SubscriptionWithPending requireOwned(WebhookId id) {
        return store.findWithPending(AccountContext.current(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook", id.value()));
    }

    private void requireValidUrl(String url) {
        String scheme;
        try {
            scheme = String.valueOf(URI.create(url).getScheme()).toLowerCase();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("url is not a valid URI");
        }
        if (properties.httpsRequired()) {
            Guard.isTrue("https".equals(scheme), "url must use https");
        } else {
            Guard.isTrue("https".equals(scheme) || "http".equals(scheme),
                    "url must use http or https");
        }
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
