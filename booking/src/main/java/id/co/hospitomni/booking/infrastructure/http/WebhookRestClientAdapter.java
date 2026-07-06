/*
 * WebhookDeliveryPort over Spring RestClient (JDK HttpClient underneath,
 * strict timeouts — the worker holds a row lock for the duration of the
 * POST). Returns whatever status the endpoint answered; exceptions carry
 * only the failure class — request bodies hold guest PII and response
 * bodies are the endpoint's business, so neither ever reaches a message.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.booking.infrastructure.http;

import id.co.hospitomni.booking.application.WebhookProperties;
import id.co.hospitomni.booking.application.WebhookSigner;
import id.co.hospitomni.booking.domain.port.out.WebhookDeliveryException;
import id.co.hospitomni.booking.domain.port.out.WebhookDeliveryPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class WebhookRestClientAdapter implements WebhookDeliveryPort {

    private final RestClient restClient;

    public WebhookRestClientAdapter(WebhookProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                        .build());
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public int post(String url, long timestampSeconds, String signature, byte[] body) {
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSigner.TIMESTAMP_HEADER, Long.toString(timestampSeconds))
                    .header(WebhookSigner.SIGNATURE_HEADER, signature)
                    .body(body)
                    .retrieve()
                    // Non-2xx is the caller's decision, not an exception here.
                    .onStatus(status -> true, (request, response) -> {
                    })
                    .toBodilessEntity()
                    .getStatusCode()
                    .value();
        } catch (RestClientException e) {
            throw new WebhookDeliveryException(
                    "Delivery transport failure: " + e.getClass().getSimpleName());
        }
    }
}
