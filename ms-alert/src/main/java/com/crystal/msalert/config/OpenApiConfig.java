package com.crystal.msalert.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI msAlertOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MS-Alert API")
                        .description("Project Crystal - Alert microservice that monitors scan results and sends Slack notifications for high/critical severity issues")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Project Crystal")
                                .email("team@crystal.com")));
    }
}
