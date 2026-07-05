/*
 * Plaintext-level credential access for one property-channel; encryption
 * is the adapter's concern (AES-GCM at rest, key held outside the DB).
 * store/clear back the management API; credentialsFor is the seam real
 * OTA adapters read at push/poll time. Plaintext must never be logged
 * or returned by any API response.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.domain.port.out;

import id.co.hospitomni.shared.PropertyChannelId;

import java.util.Optional;

public interface ChannelCredentialsPort {

    /** Encrypts and stores the credentials JSON, replacing any previous set. */
    void store(PropertyChannelId channelId, String credentialsJson);

    void clear(PropertyChannelId channelId);

    /** Decrypted credentials JSON, or empty if none are stored. */
    Optional<String> credentialsFor(PropertyChannelId channelId);
}
