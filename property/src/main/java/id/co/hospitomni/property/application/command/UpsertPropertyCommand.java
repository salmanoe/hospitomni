/*
 * Create/update payload for a property.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.application.command;

public record UpsertPropertyCommand(String title, String currency, String timezone) {
}
