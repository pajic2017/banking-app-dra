package com.bank.customerservice.service;
import com.bank.customerservice.api.CustomerAccountPage;
import java.time.Duration;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class AccountOverviewClient {
    private final RestClient rest;
    private final com.bank.security.ServiceTokenProvider tokens;
    public AccountOverviewClient(RestClient.Builder builder,
            @Value("${bank.account-service-url:http://localhost:8083}") String url,com.bank.security.ServiceTokenProvider tokens) {
        this.tokens=tokens;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        rest=builder.baseUrl(url).requestFactory(factory).build();
    }
    public CustomerAccountPage get(long customerId,int page,int size) {
        try {
            var result=rest.get().uri("/internal/v1/accounts/customer/{id}?page={page}&size={size}",customerId,page,size).headers(h -> h.setBearerAuth(tokens.token()))
                    .header("X-Correlation-ID",com.bank.customerservice.events.EventOutbox.currentCorrelation().toString()).retrieve().body(CustomerAccountPage.class);
            if (result==null || result.items()==null || result.items().stream().anyMatch(a->a==null || a.customerId()==null || a.customerId()!=customerId)) {
                throw new AccountOverviewUnavailable();
            }
            return result;
        } catch (RestClientException | IllegalStateException unavailable) { throw new AccountOverviewUnavailable(); }
    }
}
