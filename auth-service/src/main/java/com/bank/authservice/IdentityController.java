package com.bank.authservice;
import java.util.*;
import com.bank.security.BankRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
@RestController
public class IdentityController {
    private final String issuer;
    public IdentityController(@Value("${bank.security.issuer}") String issuer) { this.issuer=issuer; }
    @GetMapping("/oidc") public Map<String,String> oidc() {
        return Map.of("issuer",issuer,"clientId","banking-local","authorizationEndpoint",issuer+"/protocol/openid-connect/auth",
            "tokenEndpoint",issuer+"/protocol/openid-connect/token","logoutEndpoint",issuer+"/protocol/openid-connect/logout","flow","authorization_code","pkce","S256");
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','BANK_EMPLOYEE','CUSTOMER')")
    @GetMapping("/me") public Map<String,Object> me(JwtAuthenticationToken authentication) {
        var jwt=authentication.getToken();var result=new LinkedHashMap<String,Object>();result.put("subject",jwt.getSubject());
        result.put("roles",BankRoles.authorities(jwt).stream().map(a -> a.getAuthority()).toList());
        if(jwt.hasClaim("customer_id"))result.put("customerId",jwt.getClaimAsString("customer_id"));return result;
    }
}
