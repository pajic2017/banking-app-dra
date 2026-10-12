package com.bank.observability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.*;
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability(tracing=true,metrics=true)
@SpringBootTest(classes=MetricsSecurityTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.application.name=metrics-test","spring.cloud.config.enabled=false","management.endpoints.web.exposure.include=health,prometheus","bank.monitoring.password=unit-scrape-credential"})
class MetricsSecurityTest {
 @SpringBootConfiguration @EnableAutoConfiguration static class App { }
 @Autowired TestRestTemplate rest;
 @Test void scrapeRequiresTheDedicatedCredential() {
  assertThat(rest.getForEntity("/actuator/prometheus",String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  var good=new HttpHeaders();good.setBasicAuth("monitoring","unit-scrape-credential");
  var result=rest.exchange("/actuator/prometheus",HttpMethod.GET,new HttpEntity<>(good),String.class);
  assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(result.getBody()).contains("jvm_memory_used_bytes");
  good.setBasicAuth("monitoring","wrong");assertThat(rest.exchange("/actuator/prometheus",HttpMethod.GET,new HttpEntity<>(good),String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
 }
}
