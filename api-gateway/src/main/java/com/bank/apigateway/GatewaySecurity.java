package com.bank.apigateway;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurity {
    @Bean ReactiveJwtDecoder decoder(@Value("${bank.security.issuer}") String issuer,@Value("${bank.security.jwk-set-uri}") String jwks) {
        var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
        OAuth2TokenValidator<Jwt> audience=jwt -> jwt.getSubject()!=null && !jwt.getSubject().isBlank() && jwt.getSubject().length()<=255 && jwt.getAudience()!=null && jwt.getAudience().contains("banking-api") ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audience));return decoder;
    }
    @Bean SecurityWebFilterChain security(ServerHttpSecurity http) {
        var converter=new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var result=new ArrayList<org.springframework.security.core.GrantedAuthority>();
            if("banking-local".equals(jwt.getClaimAsString("azp")) && jwt.getClaim("realm_access") instanceof Map<?,?> realm && realm.get("roles") instanceof Collection<?> roles)
                for(var role:roles)if(role instanceof String && Set.of("ADMIN","CUSTOMER","BANK_EMPLOYEE").contains(role))result.add(new SimpleGrantedAuthority("ROLE_"+role));return result;
        });
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(auth -> auth.pathMatchers("/actuator/health", "/actuator/health/**", "/api/*/foundation", "/api/auth/oidc").permitAll()
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                .pathMatchers("/api/auth/me").hasAnyRole("ADMIN","BANK_EMPLOYEE","CUSTOMER")
                .pathMatchers("/api/notifications", "/api/notifications/**").hasRole("ADMIN")
                .pathMatchers("/api/**").hasAnyRole("ADMIN","BANK_EMPLOYEE","CUSTOMER")
                .anyExchange().denyAll())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter)))
                .authenticationEntryPoint((exchange,error) -> {exchange.getResponse().getHeaders().set("WWW-Authenticate","Bearer");return problem(exchange,401,"Authentication required");}))
            .exceptionHandling(errors -> errors.accessDeniedHandler((exchange,error) -> problem(exchange,403,"Access denied"))).build();
    }
    private Mono<Void> problem(org.springframework.web.server.ServerWebExchange exchange,int status,String title) {
        var response=exchange.getResponse();response.setStatusCode(HttpStatus.valueOf(status));response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body=("{\"type\":\"about:blank\",\"status\":"+status+",\"title\":\""+title+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
