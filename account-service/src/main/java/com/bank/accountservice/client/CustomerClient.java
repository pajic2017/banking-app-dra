package com.bank.accountservice.client;
import com.bank.accountservice.service.AccountFailure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.http.HttpStatus;
@Component
public class CustomerClient {
    private final RestClient rest;
    private final com.bank.security.ServiceTokenProvider tokens;
    public CustomerClient(RestClient customerRestClient,com.bank.security.ServiceTokenProvider tokens) { rest=customerRestClient;this.tokens=tokens; }
    public void requireActive(long id) {
        CustomerView customer;
        try { customer=rest.get().uri("/internal/v1/customers/{id}/status",id).headers(h -> h.setBearerAuth(tokens.token())).header("X-Correlation-ID",com.bank.accountservice.events.EventOutbox.currentCorrelation().toString()).retrieve().body(CustomerView.class); }
        catch (HttpClientErrorException.NotFound missing) { throw new AccountFailure(HttpStatus.UNPROCESSABLE_ENTITY,"Customer does not exist."); }
        catch (RestClientException | IllegalStateException unavailable) { throw new AccountFailure(HttpStatus.SERVICE_UNAVAILABLE,"Customer verification is unavailable; retry later."); }
        if (customer==null || customer.id()==null || customer.id()!=id ||
                !("ACTIVE".equals(customer.status()) || "INACTIVE".equals(customer.status()))) {
            throw new AccountFailure(HttpStatus.SERVICE_UNAVAILABLE,"Customer verification returned an invalid response.");
        }
        if (!"ACTIVE".equals(customer.status())) throw new AccountFailure(HttpStatus.CONFLICT,"Accounts require an active customer.");
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record CustomerView(Long id, String status) { }
}
