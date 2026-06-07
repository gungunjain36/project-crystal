package com.crystal.msintake.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ms-intake API",
                version = "1.0",
                description = "Entry point for Project Crystal static security analysis pipeline. Accepts scan requests and enqueues them on the Kafka scan-jobs topic.",
                contact = @Contact(name = "Project Crystal")
        ),
        servers = @Server(url = "/", description = "Default Server")
)
@SecurityScheme(
        name = "ApiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key",
        description = "API key for authentication. Pass in the X-API-Key header."
)
public class OpenApiConfig {
}
