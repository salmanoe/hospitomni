/*
 * Property list/read payload — Channex-compatible field names.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web.response;

import id.co.hospitomni.property.domain.model.Property;
import id.co.hospitomni.shared.PropertyId;

public record PropertyResponse(PropertyId id, String title, String currency, String timezone) {

    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.id(),
                property.title(),
                property.currency().getCurrencyCode(),
                property.timezone().getId());
    }
}
