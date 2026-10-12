package com.bank.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** No caller-supplied headers or request IDs establish resource ownership. */
public class BankAccess {
    public boolean authority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
    public boolean staff() { return authority("ROLE_ADMIN") || authority("ROLE_BANK_EMPLOYEE"); }
    public boolean admin() { return authority("ROLE_ADMIN"); }
    public boolean customer(long id) {
        if (!authority("ROLE_CUSTOMER") || id <= 0) return false;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) return false;
        Object claim = token.getToken().getClaim("customer_id");
        return claim instanceof String value && value.matches("[1-9][0-9]{0,18}") && value.equals(Long.toString(id));
    }
    public String subject() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : null;
    }
    public String scopedKey(String key) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) throw new org.springframework.security.access.AccessDeniedException("Authenticated principal required.");
        try {
            String scope = token.getToken().getIssuer() + "|" + token.getToken().getSubject() + "|" + key;
            return "P6-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
