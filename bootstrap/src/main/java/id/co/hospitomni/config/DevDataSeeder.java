/*
 * Local-profile seed: one account, one API key, one property — enough to
 * verify the auth chain (GET /properties → 200 for a seeded key).
 * Idempotent (ON CONFLICT DO NOTHING); never active outside the `local`
 * profile, so no fixed credential ever reaches a real environment.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.config;

import id.co.hospitomni.account.domain.model.ApiKeyDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Profile("local")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** Fixed dev credential — local profile only, never a real secret. */
    public static final String DEV_RAW_KEY = "homni_dev_5b1f3c9a2e7d4086b3a1c5f8d2e60417";
    public static final UUID DEV_ACCOUNT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    public static final UUID DEV_API_KEY_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    public static final UUID DEV_PROPERTY_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private final JdbcClient jdbc;

    public DevDataSeeder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.sql("INSERT INTO account (id, name) VALUES (:id, :name) ON CONFLICT DO NOTHING")
                .param("id", DEV_ACCOUNT_ID)
                .param("name", "Dev Account (local)")
                .update();
        jdbc.sql("""
                        INSERT INTO api_key (id, account_id, key_hash, label)
                        VALUES (:id, :accountId, :keyHash, :label) ON CONFLICT DO NOTHING""")
                .param("id", DEV_API_KEY_ID)
                .param("accountId", DEV_ACCOUNT_ID)
                .param("keyHash", ApiKeyDigest.sha256Hex(DEV_RAW_KEY))
                .param("label", "local dev key")
                .update();
        jdbc.sql("""
                        INSERT INTO property (id, account_id, title, currency, timezone)
                        VALUES (:id, :accountId, :title, 'IDR', 'Asia/Jakarta') ON CONFLICT DO NOTHING""")
                .param("id", DEV_PROPERTY_ID)
                .param("accountId", DEV_ACCOUNT_ID)
                .param("title", "Dev Hotel Bandung")
                .update();
        log.info("Local dev data seeded — use header {}: {}", ApiKeyAuthFilter.API_KEY_HEADER, DEV_RAW_KEY);
    }
}
