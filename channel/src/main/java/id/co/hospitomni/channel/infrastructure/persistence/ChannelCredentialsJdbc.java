/*
 * ChannelCredentialsPort over the V1-reserved columns
 * property_channel.credentials_ciphertext / credentials_key_id.
 * AES-GCM sealing happens here, at the storage boundary — nothing above
 * this adapter ever holds ciphertext, nothing below it ever plaintext.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.infrastructure.persistence;

import id.co.hospitomni.channel.domain.port.out.ChannelCredentialsPort;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.crypto.SecretCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ChannelCredentialsJdbc implements ChannelCredentialsPort {

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;

    public ChannelCredentialsJdbc(JdbcTemplate jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public void store(PropertyChannelId channelId, String credentialsJson) {
        jdbc.update("""
                        UPDATE property_channel
                        SET credentials_ciphertext = ?, credentials_key_id = ?, updated_at = NOW()
                        WHERE id = ?""",
                cipher.encrypt(credentialsJson), cipher.keyId(), channelId.value());
    }

    @Override
    public void clear(PropertyChannelId channelId) {
        jdbc.update("""
                        UPDATE property_channel
                        SET credentials_ciphertext = NULL, credentials_key_id = NULL, updated_at = NOW()
                        WHERE id = ?""",
                channelId.value());
    }

    @Override
    public Optional<String> credentialsFor(PropertyChannelId channelId) {
        record Sealed(byte[] ciphertext, String keyId) {
        }
        return jdbc.query("""
                                SELECT credentials_ciphertext, credentials_key_id
                                FROM property_channel
                                WHERE id = ? AND credentials_ciphertext IS NOT NULL""",
                        (rs, i) -> new Sealed(
                                rs.getBytes("credentials_ciphertext"),
                                rs.getString("credentials_key_id")),
                        channelId.value())
                .stream().findFirst()
                .map(sealed -> cipher.decrypt(sealed.ciphertext(), sealed.keyId()));
    }
}
