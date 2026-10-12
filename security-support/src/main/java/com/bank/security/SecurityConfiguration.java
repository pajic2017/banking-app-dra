package com.bank.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean(name="access") BankAccess access() { return new BankAccess(); }
    @Bean @ConditionalOnMissingBean JwtDecoder jwtDecoder(
            @Value("${bank.security.issuer:http://localhost:8090/realms/banking}") String issuer,
            @Value("${bank.security.jwk-set-uri:http://localhost:8090/realms/banking/protocol/openid-connect/certs}") String jwks,
            @Value("${bank.security.audience:banking-api}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(validator(issuer, audience));
        return decoder;
    }
    public static OAuth2TokenValidator<Jwt> validator(String issuer,String audience) {
        OAuth2TokenValidator<Jwt> aud = jwt -> jwt.getSubject()!=null && !jwt.getSubject().isBlank() && jwt.getSubject().length()<=255 && jwt.getAudience()!=null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is absent.", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), aud);
    }
    @Bean @ConditionalOnMissingBean ServiceTokenProvider serviceTokens(
            @Value("${bank.security.token-uri:http://localhost:8090/realms/banking/protocol/openid-connect/token}") String uri,
            @Value("${spring.application.name}") String client,
            @Value("${bank.security.client-secret:}") String secret) { return new ServiceTokenProvider(uri,client,secret); }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(BankRoles::authorities);
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/foundation", "/*/foundation", "/oidc").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasAnyRole("ADMIN", "BANK_EMPLOYEE")
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((request,response,error) -> { response.setHeader("WWW-Authenticate", "Bearer");problem(response,401,"Authentication required"); }))
            .exceptionHandling(errors -> errors.accessDeniedHandler((request,response,error) -> problem(response,403,"Access denied")));
        return http.build();
    }
    private static void problem(jakarta.servlet.http.HttpServletResponse response,int status,String title) throws java.io.IOException {
        response.setStatus(status);response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":"+status+",\"title\":\""+title+"\"}");
    }
}
