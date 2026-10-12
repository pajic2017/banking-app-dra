package com.bank.transactionservice.events;
import com.bank.observability.DurableTrace;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.context.annotation.*;
@Configuration
public class Telemetry { @Bean DurableTrace durableTrace(Tracer tracer,Propagator propagator) {return new DurableTrace(tracer,propagator);} }
