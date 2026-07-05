/*
 * OpenAPI metadata + the user-api-key security scheme. The generated spec at
 * /v3/api-docs is the source for HospitOps's HospitOmniConnector client.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.stream.Stream;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "userApiKey";

    @Bean
    OpenAPI hospitomniOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HospitOmni North API")
                        .description("Channex-shaped channel-manager API consumed by HospitOps.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(ApiKeyAuthFilter.API_KEY_HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }

    /**
     * Marks {@code Idempotency-Key} required on every write operation, matching
     * what {@link IdempotencyFilter} enforces — the generated HospitOps client
     * must send it. Mock-OTA paths are exempt there and stay exempt here.
     */
    @Bean
    OpenApiCustomizer idempotencyKeyHeader() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/mock-ota")) {
                return;
            }
            Stream.of(item.getPost(), item.getPut(), item.getPatch(), item.getDelete())
                    .filter(Objects::nonNull)
                    .forEach(operation -> operation.addParametersItem(new HeaderParameter()
                            .name(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER)
                            .required(true)
                            .description("Unique key that makes this write safely retryable: "
                                    + "a retry with the same key and payload replays the "
                                    + "original response instead of re-executing.")
                            .schema(new StringSchema().maxLength(IdempotencyFilter.MAX_KEY_LENGTH))));
        });
    }
}
