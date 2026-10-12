package com.bank.security;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=SecurityProtocolTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.application.name=security-test", "spring.cloud.config.enabled=false"})
class SecurityProtocolTest {
    static final RSAKey KEY,FORGED;
    static final HttpServer JWKS;
    static volatile RSAKey current;
    static {
        try {
            KEY=new RSAKeyGenerator(2048).keyID("original").generate();FORGED=new RSAKeyGenerator(2048).keyID("forged").generate();current=KEY;
            JWKS=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            JWKS.createContext("/certs",exchange -> {byte[] json=new JWKSet(current.toPublicJWK()).toString().getBytes();exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,json.length);exchange.getResponseBody().write(json);exchange.close();});JWKS.start();
        } catch(Exception failure) {throw new ExceptionInInitializerError(failure);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("bank.security.jwk-set-uri",()->"http://127.0.0.1:"+JWKS.getAddress().getPort()+"/certs");p.add("bank.security.issuer",()->"https://trusted.example/realms/banking");
    }
    @SpringBootConfiguration @EnableAutoConfiguration @Import(Endpoints.class) static class App { }
    @RestController static class Endpoints {
        @GetMapping("/own/{id}") @PreAuthorize("@access.customer(#id)") public Map<String,String> own(@PathVariable long id) {return Map.of("result","owned");}
        @GetMapping("/admin") @PreAuthorize("@access.admin()") public String admin() {return "admin";}
        @PostMapping("/internal") @PreAuthorize("hasAuthority('SERVICE_TRANSACTION')") public String internal() {return "posted";}
    }
    @Autowired TestRestTemplate rest;
    @AfterAll static void stop() {JWKS.stop(0);}
    String signed(RSAKey key,String issuer,String audience,String client,List<String> roles,String customer,Instant expiry,Instant notBefore) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject("user-1").audience(audience).issueTime(Date.from(Instant.now())).expirationTime(Date.from(expiry))
            .notBeforeTime(Date.from(notBefore)).claim("azp",client).claim("realm_access",Map.of("roles",roles));
        if(customer!=null)claims.claim("customer_id",customer);
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),claims.build());jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    String token(String role,String customer,String client) throws Exception {return signed(KEY,"https://trusted.example/realms/banking","banking-api",client,List.of(role),customer,Instant.now().plusSeconds(300),Instant.now().minusSeconds(1));}
    ResponseEntity<String> call(String path,String token,HttpMethod method) {var headers=new HttpHeaders();if(token!=null)headers.setBearerAuth(token);return rest.exchange(path,method,new HttpEntity<>(headers),String.class);}
    @Test void missingAndInvalidSignaturesReturnBearer401() throws Exception {
        for(String token:new String[]{null,"not-a-jwt",signed(FORGED,"https://trusted.example/realms/banking","banking-api","banking-local",List.of("ADMIN"),null,Instant.now().plusSeconds(300),Instant.now())}) {
            var response=call("/admin",token,HttpMethod.GET);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");assertThat(response.getBody()).contains("Authentication required").doesNotContain("not-a-jwt");
        }
    }
    @Test void issuerAudienceExpirationAndFutureNotBeforeAreValidated() throws Exception {
        for(var parameters:List.of(new String[]{"https://wrong.example","banking-api","valid"},new String[]{"https://trusted.example/realms/banking","other-api","valid"},new String[]{"https://trusted.example/realms/banking",null,"valid"},
                new String[]{"https://trusted.example/realms/banking","banking-api","expired"},new String[]{"https://trusted.example/realms/banking","banking-api","future"})) {
            var value=signed(KEY,parameters[0],parameters[1],"banking-local",List.of("ADMIN"),null,parameters[2].equals("expired")?Instant.now().minusSeconds(120):Instant.now().plusSeconds(300),parameters[2].equals("future")?Instant.now().plusSeconds(120):Instant.now().minusSeconds(1));
            assertThat(call("/admin",value,HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
    @Test void validCustomerOwnsOnlyItsTrustedClaimAndCannotAdminister() throws Exception {
        var alice=token("CUSTOMER","1","banking-local");
        assertThat(call("/own/1",alice,HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call("/own/2",alice,HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/admin",alice,HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/own/1",token("CUSTOMER",null,"banking-local"),HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/own/1",token("CUSTOMER","01","banking-local"),HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/admin",token("ADMIN",null,"unknown-client"),HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void servicePostingNeedsBothRoleAndAuthorizedParty() throws Exception {
        assertThat(call("/internal",token("SERVICE_TRANSACTION",null,"transaction-service"),HttpMethod.POST).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call("/internal",token("SERVICE_TRANSACTION",null,"banking-local"),HttpMethod.POST).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/internal",token("SERVICE_ACCOUNT",null,"account-service"),HttpMethod.POST).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/internal",token("ADMIN",null,"banking-local"),HttpMethod.POST).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void newSigningKeyIsDiscoveredWithoutDisablingValidation() throws Exception {
        var rotated=new RSAKeyGenerator(2048).keyID("rotated").generate();current=rotated;
        try {var value=signed(rotated,"https://trusted.example/realms/banking","banking-api","banking-local",List.of("ADMIN"),null,Instant.now().plusSeconds(300),Instant.now());
            assertThat(call("/admin",value,HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {current=KEY;}
    }
    @Test void signedJwtWithoutSubjectCannotAcquireABankIdentity() throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer("https://trusted.example/realms/banking").audience("banking-api")
            .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("azp","banking-local").claim("realm_access",Map.of("roles",List.of("ADMIN"))).build();
        var value=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),claims);value.sign(new RSASSASigner(KEY));
        assertThat(call("/admin",value.serialize(),HttpMethod.GET).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
