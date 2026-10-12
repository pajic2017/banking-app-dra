package com.bank.observability;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability(tracing=true,metrics=true)
@SpringBootTest(classes=DurableTraceTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"spring.application.name=trace-test","spring.cloud.config.enabled=false","management.tracing.propagation.type=w3c","management.tracing.sampling.probability=1"})
class DurableTraceTest {
 @SpringBootConfiguration @EnableAutoConfiguration static class App { }
 @Autowired Tracer tracer;@Autowired Propagator propagator;
 @Test void durableParentSurvivesTheOriginalScopeEnding() {
  var helper=new DurableTrace(tracer,propagator);var original=tracer.nextSpan().name("request").start();String saved;
  try(var scope=tracer.withSpan(original)){saved=helper.capture();assertThat(saved).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");}finally{original.end();}
  assertThat(tracer.currentSpan()).isNull();
  try(var scope=helper.resume(saved,"worker","correlation")){assertThat(tracer.currentSpan().context().traceId()).isEqualTo(original.context().traceId());assertThat(org.slf4j.MDC.get("correlationId")).isEqualTo("correlation");}
  assertThat(tracer.currentSpan()).isNull();assertThat(org.slf4j.MDC.get("correlationId")).isNull();
 }
 @Test void oldRowsWithoutTraceContextRemainProcessable() {
  var helper=new DurableTrace(tracer,propagator);assertThat(helper.capture()).isNull();
  org.slf4j.MDC.put("correlationId","previous");
  try(var scope=helper.resume(null,"legacy.worker","current")){assertThat(tracer.currentSpan()).isNotNull();}
  assertThat(org.slf4j.MDC.get("correlationId")).isEqualTo("previous");org.slf4j.MDC.remove("correlationId");
 }
}
