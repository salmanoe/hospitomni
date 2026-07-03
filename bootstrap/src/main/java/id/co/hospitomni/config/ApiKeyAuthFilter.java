/*
 * Resolves the `user-api-key` header to an account and binds the tenant scope:
 * header → SHA-256 lookup (ApiKeyAuthService) → SecurityContext authentication
 * + AccountContext ScopedValue for the rest of the chain. Requests without a
 * valid key proceed unauthenticated — the entry point 401s protected routes.
 *
 * ScopedValue binding mirrors HospitOps's JwtAuthFilter (.call + rethrow).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.config;

import id.co.hospitomni.account.application.ApiKeyAuthService;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.AccountId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "user-api-key";

    private final ApiKeyAuthService apiKeyAuthService;

    public ApiKeyAuthFilter(ApiKeyAuthService apiKeyAuthService) {
        this.apiKeyAuthService = apiKeyAuthService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);
        Optional<AccountId> accountId =
                rawKey == null ? Optional.empty() : apiKeyAuthService.authenticate(rawKey);

        if (accountId.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                accountId.get(), null, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            ScopedValue.where(AccountContext.ACCOUNT_ID, accountId.get())
                    .call(() -> {
                        chain.doFilter(request, response);
                        return null;
                    });
        } catch (IOException | ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException("Unexpected error in account-context filter scope", e);
        }
    }
}
