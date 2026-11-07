package com.mt.transactions.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI transactionsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transactions Service")
                .version("0.0.1")
                .description("In-memory transactions: create, list by type and sum with descendants."));
    }
}
