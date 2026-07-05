/*
 * Enforces the `Idempotency-Key` header on every north-API write
 * (POST/PUT/PATCH/DELETE under /api/v1/). A retry with the same key and
 * payload gets the stored response back verbatim (flagged
 * `Idempotency-Replayed: true`); reusing a key with a different payload is
 * 422; a duplicate arriving while the first attempt still runs is 409.
 * All rejections are RFC 9457 Problem Details.
 *
 * Runs inside the ApiKeyAuthFilter's account scope — unauthenticated
 * requests pass through untouched so the 401 wins over any 400 here.
 * /api/v1/mock-ota/** is exempt: it simulates the OTA side (redelivering
 * the same revision_seq there deliberately exercises booking dedupe).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.config;

import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.AccountId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";

    /** Sanity bound; real clients send UUIDs or similar short tokens. */
    static final int MAX_KEY_LENGTH = 200;

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !WRITE_METHODS.contains(request.getMethod())
                || !path.startsWith("/api/v1/")
                || path.startsWith("/api/v1/mock-ota");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!AccountContext.isBound()) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            writeProblem(response, 400, "Missing Idempotency-Key",
                    "Idempotency-Key header is required on all write requests.");
            return;
        }
        if (key.length() > MAX_KEY_LENGTH) {
            writeProblem(response, 400, "Invalid Idempotency-Key",
                    "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters.");
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        String pathAndQuery = request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
        String hash = IdempotencyService.requestHash(
                request.getMethod(), pathAndQuery, new String(body, StandardCharsets.UTF_8));

        AccountId accountId = AccountContext.current();
        switch (idempotencyService.claim(accountId, key, hash)) {
            case IdempotencyService.Claim.Replay replay -> {
                response.setStatus(replay.status());
                // Before setContentType: a bare "application/json" must not pick
                // up the servlet default charset (ISO-8859-1) on the way out.
                response.setCharacterEncoding(StandardCharsets.UTF_8);
                if (replay.contentType() != null) {
                    response.setContentType(replay.contentType());
                }
                response.setHeader(REPLAYED_HEADER, "true");
                response.getWriter().write(replay.body());
            }
            case IdempotencyService.Claim.InFlight ignored ->
                    writeProblem(response, 409, "Idempotency Conflict",
                            "A request with this Idempotency-Key is still being processed.");
            case IdempotencyService.Claim.PayloadMismatch ignored ->
                    writeProblem(response, 422, "Idempotency-Key Reuse",
                            "This Idempotency-Key was already used with a different request payload.");
            case IdempotencyService.Claim.Acquired ignored ->
                    executeAndStore(new CachedBodyRequest(request, body), response, chain, accountId, key);
        }
    }

    private void executeAndStore(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            AccountId accountId, String key) throws ServletException, IOException {
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, captured);
        } catch (IOException | ServletException | RuntimeException e) {
            // No storable outcome — free the key so the client's retry can run.
            idempotencyService.release(accountId, key);
            throw e;
        }
        idempotencyService.complete(
                accountId, key, captured.getStatus(), captured.getContentType(),
                new String(captured.getContentAsByteArray(), StandardCharsets.UTF_8));
        captured.copyBodyToResponse();
    }

    private static void writeProblem(
            HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(title, status, detail));
    }

    /**
     * Replays the already-consumed body to the rest of the chain. The body
     * has to be read before dispatch to fingerprint the request, and servlet
     * input streams are single-shot.
     */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return buffer.read();
                }

                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(@Nullable ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not supported");
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
