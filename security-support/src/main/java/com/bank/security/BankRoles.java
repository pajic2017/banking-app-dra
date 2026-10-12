package com.bank.security;

import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Only trusted Keycloak realm roles and known authorized-party clients grant bank privileges. */
public final class BankRoles {
    private BankRoles() { }
    public static Collection<GrantedAuthority> authorities(Jwt jwt) {
        var roles = new ArrayList<GrantedAuthority>();
        if (!(jwt.getClaim("realm_access") instanceof Map<?,?> realm) || !(realm.get("roles") instanceof Collection<?> values)) return roles;
        String client = jwt.getClaimAsString("azp");
        for (Object value : values) {
            if (!(value instanceof String)) continue;
            if ("banking-local".equals(client) && Set.of("ADMIN", "BANK_EMPLOYEE", "CUSTOMER").contains(value))
                roles.add(new SimpleGrantedAuthority("ROLE_" + value));
            if (("transaction-service".equals(client) && "SERVICE_TRANSACTION".equals(value)) ||
                ("account-service".equals(client) && "SERVICE_ACCOUNT".equals(value)) ||
                ("customer-service".equals(client) && "SERVICE_CUSTOMER".equals(value))) roles.add(new SimpleGrantedAuthority(value.toString()));
        }
        return roles;
    }
}
