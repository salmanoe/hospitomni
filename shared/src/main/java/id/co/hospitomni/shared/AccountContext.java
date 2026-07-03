/*
 * Carries the current account (tenant) scope for a virtual-thread request.
 *
 * Uses ScopedValue rather than ThreadLocal — a virtual thread may be remounted
 * on a different carrier thread between suspensions (same rule as HospitOps's
 * HotelContext). The user-api-key filter in bootstrap binds ACCOUNT_ID at the
 * start of every authenticated request; downstream code reads current().
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared;

public final class AccountContext {

    /** The scoped value slot — one per request, immutable once bound. */
    public static final ScopedValue<AccountId> ACCOUNT_ID = ScopedValue.newInstance();

    private AccountContext() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Returns the {@link AccountId} bound to the current scope.
     *
     * @throws IllegalStateException if no account context is bound — a tenant-scoped
     *                               operation was invoked outside an authenticated request
     */
    public static AccountId current() {
        return ACCOUNT_ID.orElseThrow(() ->
                new IllegalStateException(
                        "No account context bound to the current scope. "
                        + "Ensure the request carries a valid user-api-key header."));
    }

    /** Returns {@code true} if an account context is bound to the current scope. */
    public static boolean isBound() {
        return ACCOUNT_ID.isBound();
    }
}
