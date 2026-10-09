package com.bank.accountservice.config;
import java.time.Clock;
import java.time.Duration;
import java.net.http.HttpClient;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
@Configuration
public class AccountConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean RestClient customerRestClient(RestClient.Builder builder,
            @Value("${bank.customer-service-url:http://localhost:8082}") String url) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        return builder.baseUrl(url).requestFactory(factory).build();
    }
    @Bean OpenAPI accountOpenApi() { return new OpenAPI().info(new Info().title("Banking Account API").version("1.0")); }
}
