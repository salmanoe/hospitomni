/*
 * Wires the app-held AES-256-GCM secret cipher. The key never touches the
 * database: it arrives base64-encoded via configuration (HOSPITOMNI_SECRET_KEY
 * env in real environments; application.yaml carries a dev-only fallback so
 * the local profile boots without setup). Pre-production hardening: reject
 * the dev fallback outside the local profile.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.config;

import id.co.hospitomni.shared.crypto.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
public class SecurityCryptoConfig {

    @Bean
    SecretCipher secretCipher(
            @Value("${hospitomni.security.secret-key}") String base64Key,
            @Value("${hospitomni.security.secret-key-id}") String keyId) {
        return new SecretCipher(Base64.getDecoder().decode(base64Key), keyId);
    }
}
