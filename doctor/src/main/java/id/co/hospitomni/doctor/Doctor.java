/*
 * HospitOmni doctor — walks the public API exactly like a PMS client would
 * and reports one PASS/WARN/FAIL line per check:
 *
 *   api-key      key accepted, API reachable
 *   mappings     properties have OTA channel mappings, and each channel has
 *                its unit-level identity set (listing code + room types)
 *   ari          availability/restrictions readback responds (value-level
 *                equality against the PMS is the PMS-side doctor's job)
 *   events       booking-events stream reachable; reports the tip cursor
 *   sync-status  per-channel push health: dead letters, push errors,
 *                stale backlog (drift), paused channels
 *   reconcile    per-channel drift-correction outcome: never reconciled or
 *                drift found → warn (the reconciler self-heals)
 *   webhooks     delivery health of registered webhook endpoints
 *
 * Exit code: 0 healthy (warnings allowed), 1 any check failed, 2 bad usage.
 *
 * @author Salman
 * @version 1.1
 * @since 2026-07-05
 */
package id.co.hospitomni.doctor;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class Doctor {

    record Options(String baseUrl, String apiKey, @Nullable String propertyId, int staleMinutes) {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    public static int run(String[] args, PrintStream out) {
        Options options = parse(args, out);
        if (options == null) {
            return 2;
        }
        out.println("HospitOmni doctor — " + options.baseUrl());
        return new Doctor(options, out).checkup();
    }

    private static @Nullable Options parse(String[] args, PrintStream out) {
        String baseUrl = "http://localhost:8080";
        String apiKey = System.getenv("HOSPITOMNI_API_KEY");
        String propertyId = null;
        int staleMinutes = 10;
        for (String arg : args) {
            if (arg.startsWith("--base-url=")) {
                baseUrl = arg.substring("--base-url=".length());
            } else if (arg.startsWith("--api-key=")) {
                apiKey = arg.substring("--api-key=".length());
            } else if (arg.startsWith("--property-id=")) {
                propertyId = arg.substring("--property-id=".length());
            } else if (arg.startsWith("--stale-minutes=")) {
                staleMinutes = Integer.parseInt(arg.substring("--stale-minutes=".length()));
            } else {
                out.println("Unknown argument: " + arg);
                apiKey = null;
                break;
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            out.println("""
                    Usage: doctor --api-key=<key> [--base-url=http://localhost:8080]
                                  [--property-id=<uuid>] [--stale-minutes=10]
                    The key may also come from the HOSPITOMNI_API_KEY environment variable.""");
            return null;
        }
        return new Options(
                baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl,
                apiKey, propertyId, staleMinutes);
    }

    // ── checkup ─────────────────────────────────────────────────────────

    /** Aborts the whole checkup — nothing after this check can be trusted. */
    private static final class Abort extends RuntimeException {
        final String check;

        Abort(String check, String message) {
            super(message);
            this.check = check;
        }
    }

    private final Options options;
    private final PrintStream out;
    private final HttpClient http;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private int passed;
    private int warned;
    private int failed;

    private Doctor(Options options, PrintStream out) {
        this.options = options;
        this.out = out;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private int checkup() {
        try {
            List<JsonNode> properties = checkApiKey();
            checkMappings(properties);
            checkAri(properties);
            checkEvents();
            checkSyncStatus();
            checkWebhooks();
        } catch (Abort abort) {
            fail(abort.check, abort.getMessage());
        }
        out.printf("%n%d passed, %d warning(s), %d failed — exit %d%n",
                passed, warned, failed, failed > 0 ? 1 : 0);
        return failed > 0 ? 1 : 0;
    }

    private List<JsonNode> checkApiKey() {
        JsonNode data = getData("api-key", "/api/v1/properties");
        List<JsonNode> properties = new ArrayList<>();
        for (JsonNode property : data) {
            if (options.propertyId() == null
                    || options.propertyId().equals(property.path("id").asString())) {
                properties.add(property);
            }
        }
        if (options.propertyId() != null && properties.isEmpty()) {
            throw new Abort("api-key",
                    "key accepted, but property " + options.propertyId() + " is not visible to it");
        }
        pass("api-key", data.size() + " propert" + (data.size() == 1 ? "y" : "ies") + " visible");
        if (properties.isEmpty()) {
            warn("mappings", "no properties yet — property checks skipped");
        }
        return properties;
    }

    private void checkMappings(List<JsonNode> properties) {
        for (JsonNode property : properties) {
            String id = property.path("id").asString();
            JsonNode channels = getData("mappings", "/api/v1/property-channels?property_id=" + id);
            if (channels.isEmpty()) {
                fail("mappings", describe(property) + ": no OTA channel mapped");
                continue;
            }
            List<String> names = new ArrayList<>();
            for (JsonNode channel : channels) {
                names.add(channel.path("ota_name").asString()
                        + (channel.path("paused").asBoolean() ? " (paused)" : ""));
            }
            pass("mappings", describe(property) + ": " + String.join(", ", names));
            int totalRoomTypes =
                    getData("mappings", "/api/v1/room-types?property_id=" + id).size();
            for (JsonNode channel : channels) {
                checkUnitMappings(property, channel, totalRoomTypes);
            }
        }
    }

    /** Unit-level identity: without listing codes, real pushes are unaddressable. */
    private void checkUnitMappings(JsonNode property, JsonNode channel, int totalRoomTypes) {
        String label = describe(property) + " → " + channel.path("ota_name").asString();
        JsonNode mappings = getData("mappings",
                "/api/v1/mappings?property_channel_id=" + channel.path("id").asString());
        if (mappings.path("property_code").isNull()) {
            warn("mappings", label + ": no OTA listing code yet (PUT /mappings)");
            return;
        }
        int mapped = mappings.path("room_types").size();
        if (mapped < totalRoomTypes) {
            warn("mappings", label + ": only " + mapped + "/" + totalRoomTypes
                    + " room types mapped to OTA codes");
        } else {
            pass("mappings", label + ": listing code set, "
                    + mapped + "/" + totalRoomTypes + " room types mapped");
        }
    }

    private void checkAri(List<JsonNode> properties) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(13);
        for (JsonNode property : properties) {
            String id = property.path("id").asString();
            String window = "&date_gte=" + from + "&date_lte=" + to;
            JsonNode availability =
                    getData("ari", "/api/v1/availability?property_id=" + id + window);
            getData("ari", "/api/v1/restrictions?property_id=" + id + window + "&fields=rate");
            if (availability.isEmpty()) {
                warn("ari", describe(property) + ": no availability published for the next 14 days");
            } else {
                pass("ari", describe(property) + ": readback OK, "
                        + availability.size() + " availability range(s) in the next 14 days");
            }
        }
    }

    private void checkEvents() {
        getData("events", "/api/v1/booking-events?after=0&limit=1");
        pass("events", "booking-events stream reachable");
    }

    private void checkSyncStatus() {
        String path = "/api/v1/sync-status"
                + (options.propertyId() == null ? "" : "?property_id=" + options.propertyId());
        JsonNode status = getData("sync-status", path);

        JsonNode stream = status.path("booking_events");
        pass("sync-status", "stream tip seq " + stream.path("tip_seq").asLong()
                + (stream.path("last_event_at").isNull() || stream.path("last_event_at").isMissingNode()
                        ? " (no events yet)" : ", last event " + stream.path("last_event_at").asString()));

        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(options.staleMinutes()));
        for (JsonNode channel : status.path("channels")) {
            String label = channel.path("ota_name").asString()
                    + " @ " + channel.path("property_id").asString();
            long deadLetters = channel.path("dead_letters").asLong();
            long pending = channel.path("pending_cells").asLong();
            String lastError = channel.path("last_push_error").isNull()
                    ? null : channel.path("last_push_error").asString();
            String lastPush = channel.path("last_push_at").isNull()
                    ? "never" : channel.path("last_push_at").asString();

            if (deadLetters > 0) {
                fail("sync-status", label + ": " + deadLetters + " dead-letter cell(s)");
            } else if (lastError != null) {
                fail("sync-status", label + ": last push failed — " + lastError);
            } else if (pending > 0
                    && !channel.path("oldest_pending_marked_at").isNull()
                    && Instant.parse(channel.path("oldest_pending_marked_at").asString())
                            .isBefore(staleBefore)) {
                fail("sync-status", label + ": " + pending + " cell(s) pending for over "
                        + options.staleMinutes() + " min — relay drifting");
            } else if (channel.path("paused").asBoolean()) {
                warn("sync-status", label + ": paused (kill switch on), last push " + lastPush);
            } else if (pending > 0) {
                warn("sync-status", label + ": " + pending
                        + " cell(s) in flight, last push " + lastPush);
            } else {
                pass("sync-status", label + ": in sync, last push " + lastPush);
            }
            checkReconciliation(channel, label);
        }
    }

    /** Drift correction is self-healing, so its findings warn rather than fail. */
    private void checkReconciliation(JsonNode channel, String label) {
        JsonNode reconciledAt = channel.path("last_reconciled_at");
        if (reconciledAt.isNull() || reconciledAt.isMissingNode()) {
            warn("reconcile", label + ": never reconciled — drift on the OTA would go unseen");
            return;
        }
        int drift = channel.path("last_drift_count").asInt();
        if (drift > 0) {
            warn("reconcile", label + ": " + drift + " drifted night(s) at last run "
                    + reconciledAt.asString() + " — full refresh was fenced in");
        } else {
            pass("reconcile", label + ": drift-free at " + reconciledAt.asString());
        }
    }

    private void checkWebhooks() {
        JsonNode hooks = getData("webhooks", "/api/v1/webhooks");
        if (hooks.isEmpty()) {
            pass("webhooks", "no webhook subscriptions — consumers poll /booking-events");
            return;
        }
        for (JsonNode hook : hooks) {
            String label = hook.path("url").asString();
            long pending = hook.path("pending_events").asLong();
            int failures = hook.path("consecutive_failures").asInt();
            if (!hook.path("active").asBoolean()) {
                warn("webhooks", label + ": paused, " + pending + " event(s) waiting");
            } else if (failures > 0) {
                warn("webhooks", label + ": " + failures + " consecutive failure(s) — "
                        + hook.path("last_error").asString() + ", " + pending + " pending"
                        + " (polling backstop still covers them)");
            } else {
                pass("webhooks", label + ": delivered through seq "
                        + hook.path("last_delivered_seq").asLong()
                        + (pending > 0 ? ", " + pending + " in flight" : ""));
            }
        }
    }

    // ── plumbing ────────────────────────────────────────────────────────

    /** GETs an ApiResponse envelope and returns its {@code data}; aborts on transport/auth errors. */
    private JsonNode getData(String check, String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(options.baseUrl() + path))
                .header("user-api-key", options.apiKey())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new Abort(check, "cannot reach " + options.baseUrl() + " — " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Abort(check, "interrupted while calling " + path);
        }
        if (response.statusCode() == 401) {
            throw new Abort("api-key", "API key rejected (401) — missing, unknown, or revoked");
        }
        if (response.statusCode() != 200) {
            throw new Abort(check, "GET " + path + " → HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body()).path("data");
    }

    private static String describe(JsonNode property) {
        return property.path("title").asString();
    }

    private void pass(String check, String detail) {
        passed++;
        out.printf("[PASS] %-12s %s%n", check, detail);
    }

    private void warn(String check, String detail) {
        warned++;
        out.printf("[WARN] %-12s %s%n", check, detail);
    }

    private void fail(String check, String detail) {
        failed++;
        out.printf("[FAIL] %-12s %s%n", check, detail);
    }
}
