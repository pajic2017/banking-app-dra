package com.bank.accountservice.client;
import com.bank.accountservice.service.AccountFailure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.http.HttpStatus;
@Component
public class CustomerClient {
    private final RestClient rest;
    public CustomerClient(RestClient customerRestClient) { rest=customerRestClient; }
    public void requireActive(long id) {
        CustomerView customer;
        try { customer=rest.get().uri("/customers/{id}",id).retrieve().body(CustomerView.class); }
        catch (HttpClientErrorException.NotFound missing) { throw new AccountFailure(HttpStatus.UNPROCESSABLE_ENTITY,"Customer does not exist."); }
        catch (RestClientException unavailable) { throw new AccountFailure(HttpStatus.SERVICE_UNAVAILABLE,"Customer verification is unavailable; retry later."); }
        if (customer==null || customer.id()==null || customer.id()!=id ||
                !("ACTIVE".equals(customer.status()) || "INACTIVE".equals(customer.status()))) {
            throw new AccountFailure(HttpStatus.SERVICE_UNAVAILABLE,"Customer verification returned an invalid response.");
        }
        if (!"ACTIVE".equals(customer.status())) throw new AccountFailure(HttpStatus.CONFLICT,"Accounts require an active customer.");
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record CustomerView(Long id, String status) { }
}
