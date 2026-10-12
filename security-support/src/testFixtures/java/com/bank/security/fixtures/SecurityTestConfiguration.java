package com.bank.security.fixtures;
import com.bank.security.ServiceTokenProvider;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import static org.mockito.Mockito.*;
/** Authorization fixtures only: production JWT signature validation is tested separately with RSA/JWKs. */
@TestConfiguration
public class SecurityTestConfiguration {
    @Bean @Primary JwtDecoder testDecoder() { return value -> {
        if(value.equals("invalid"))throw new BadJwtException("Invalid fixture token");
        String client="banking-local";String role=switch(value) {case "alice","bob","unlinked" -> "CUSTOMER";case "employee" -> "BANK_EMPLOYEE";case "roleless" -> "NONE";case "posting" -> "SERVICE_TRANSACTION";case "account-service" -> "SERVICE_ACCOUNT";case "customer-service" -> "SERVICE_CUSTOMER";case "admin" -> "ADMIN";default -> value.startsWith("customer-")?"CUSTOMER":"NONE";};
        if(value.equals("posting"))client="transaction-service";
        if(value.endsWith("-service"))client=value;
        var builder=Jwt.withTokenValue(value).header("alg","RS256").issuer("http://localhost:8090/realms/banking").subject(value)
            .audience(List.of("banking-api")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("azp",client).claim("realm_access",Map.of("roles",List.of(role)));
        if(value.startsWith("customer-"))builder.claim("customer_id",value.substring(9));
        if(value.equals("alice"))builder.claim("customer_id","1");if(value.equals("bob"))builder.claim("customer_id","2");return builder.build();
    }; }
    @Bean @Primary ServiceTokenProvider testServiceTokens() { var tokens=mock(ServiceTokenProvider.class);when(tokens.token()).thenReturn("fixture-service-token");return tokens; }
}
