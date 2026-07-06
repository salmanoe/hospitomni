/*
 * The outbound HTTP seam for webhook deliveries. Implementations return
 * the endpoint's status code and throw WebhookDeliveryException on
 * transport failure — with messages that NEVER contain request or
 * response bodies (the payload carries guest PII; V5 policy forbids PII
 * in logs and error columns).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.domain.port.out;

public interface WebhookDeliveryPort {

    int post(String url, long timestampSeconds, String signature, byte[] body);
}
