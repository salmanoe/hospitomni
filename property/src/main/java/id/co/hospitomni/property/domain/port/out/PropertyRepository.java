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

import java.util.List;

public interface PropertyRepository {

    List<Property> findAllByAccount(AccountId accountId);
}
