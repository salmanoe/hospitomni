/*
 * Persistence port for property content — always tenant-scoped.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.domain.port.out;

import id.co.hospitomni.property.domain.model.Property;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyId;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository {

    Property save(Property property);

    /** Tenant-scoped lookup — a property of another account is simply absent. */
    Optional<Property> findByIdAndAccount(PropertyId id, AccountId accountId);

    List<Property> findAllByAccount(AccountId accountId);
}
