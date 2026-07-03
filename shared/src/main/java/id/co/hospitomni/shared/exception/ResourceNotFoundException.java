/*
 * A tenant-scoped resource was not found (or belongs to another account —
 * indistinguishable by design, no cross-tenant existence oracle). Maps to 404.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
