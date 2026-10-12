package com.bank.transactionservice.config;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
@Configuration
@EnableScheduling
public class TransactionConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean OpenAPI transactionOpenApi() { return new OpenAPI().info(new Info().title("Banking Transaction API").version("1.0")); }
}
