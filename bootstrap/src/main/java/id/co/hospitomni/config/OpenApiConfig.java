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
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
