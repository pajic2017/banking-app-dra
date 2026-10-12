package com.bank.apigateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"CONFIG_IMPORT=optional:classpath:/no-config-client.yml", "spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
class ApiGatewayApplicationTest {
    @Autowired TestRestTemplate rest;
    @Test void healthEndpointIsUp() {
        var response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("UP");
    }
    @Test void missingBearerCannotReachBusinessRoute() {
        var response=rest.getForEntity("/api/customers/1",String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).contains("Authentication required");
    }
    @Test void malformedBearerIsRejectedByReactiveDecoder() {
        var headers=new org.springframework.http.HttpHeaders();headers.setBearerAuth("not-a-jwt");
        var response=rest.exchange("/api/accounts/1",org.springframework.http.HttpMethod.GET,new org.springframework.http.HttpEntity<>(headers),String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
