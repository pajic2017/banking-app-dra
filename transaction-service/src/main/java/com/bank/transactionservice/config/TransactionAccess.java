package com.bank.transactionservice.config;
import com.bank.security.BankAccess;
import com.bank.transactionservice.repository.TransactionRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
@Component("transactionAccess")
public class TransactionAccess {
    private final BankAccess access;
    private final TransactionRepository transactions;
    private final RestClient accounts;
    public TransactionAccess(BankAccess access,TransactionRepository transactions,RestClient.Builder builder,
            @Value("${bank.account-service-url:http://localhost:8083}") String url) {
        this.access=access;this.transactions=transactions;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));accounts=builder.baseUrl(url).requestFactory(factory).build();
    }
    public boolean account(long id) {
        if(access.staff())return true;
        if(!access.authority("ROLE_CUSTOMER"))return false;
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(!(auth instanceof JwtAuthenticationToken token))return false;
        try {
            var row=accounts.get().uri("/accounts/{id}",id).headers(h -> h.setBearerAuth(token.getToken().getTokenValue())).retrieve().body(java.util.Map.class);
            return row != null && row.get("customerId") instanceof Number owner && access.customer(owner.longValue());
        } catch(org.springframework.web.client.HttpClientErrorException denied) {
            if(denied.getStatusCode().value()==403 || denied.getStatusCode().value()==404)return false;
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Ownership verification unavailable.");
        } catch(org.springframework.web.client.RestClientException unavailable) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Ownership verification unavailable.");
        }
    }
    public boolean transaction(UUID id) {
        if(access.staff())return true;
        var row=transactions.get(id);
        return row.isPresent() && ((row.get().fromAccountId()!=null && account(row.get().fromAccountId())) ||
            (row.get().toAccountId()!=null && account(row.get().toAccountId())));
    }
}
