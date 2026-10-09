package com.bank.customerservice.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class CustomerConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean OpenAPI customerOpenApi() {
        return new OpenAPI().info(new Info().title("Banking Customer API").version("1.0")
                .description("Phase 2 customer lifecycle. Authentication is added in Phase 6."));
    }
}
