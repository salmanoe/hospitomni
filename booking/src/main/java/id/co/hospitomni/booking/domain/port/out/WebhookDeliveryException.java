/*
 * A webhook delivery that did not land: transport failure or non-2xx.
 * Messages carry status/class information only — never request or
 * response bodies (guest PII).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.domain.port.out;

public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message) {
        super(message);
    }
}
