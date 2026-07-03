/*
 * A property (hotel) under an account. Business dates for a property are
 * hotel-local per its IANA timezone — never server/UTC.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.domain.model;

import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;

import java.time.ZoneId;
import java.util.Currency;

public record Property(
        PropertyId id,
        AccountId accountId,
        String title,
        Currency currency,
        ZoneId timezone) {

    public Property {
        Guard.notNull(id, "id");
        Guard.notNull(accountId, "accountId");
        Guard.notBlank(title, "title");
        Guard.maxLength(title, 200, "title");
        Guard.notNull(currency, "currency");
        Guard.notNull(timezone, "timezone");
    }
}
