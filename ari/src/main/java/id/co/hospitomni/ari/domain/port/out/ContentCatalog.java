/*
 * Ownership checks against the content tables (property module owns the
 * schema; this port reads it via SQL to avoid a code-level module cycle
 * on the high-volume path).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.domain.port.out;

import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;

public interface ContentCatalog {

    boolean propertyOwnedBy(PropertyId propertyId, AccountId accountId);

    boolean roomTypeInProperty(RoomTypeId roomTypeId, PropertyId propertyId);

    boolean ratePlanInProperty(RatePlanId ratePlanId, PropertyId propertyId);
}
