package com.bank.observability;
import io.micrometer.tracing.*;
import io.micrometer.tracing.propagation.Propagator;
import java.util.*;
/** Persist only W3C trace context, never authentication or request payloads. */
public class DurableTrace {
    private final Tracer tracer;private final Propagator propagator;
    public DurableTrace(Tracer tracer,Propagator propagator) {this.tracer=tracer;this.propagator=propagator;}
    public String capture() {
        var span=tracer.currentSpan();if(span==null)return null;
        var headers=new HashMap<String,String>();propagator.inject(span.context(),headers,Map::put);return headers.get("traceparent");
    }
    public Scope resume(String parent,String name,String correlation) {
        var headers=parent==null?Map.<String,String>of():Map.of("traceparent",parent);
        var span=propagator.extract(headers,Map::get).name(name).start();
        return new Scope(span,tracer.withSpan(span),correlation);
    }
    public static final class Scope implements AutoCloseable {
        private final Span span;private final Tracer.SpanInScope scope;private final String previous;
        Scope(Span span,Tracer.SpanInScope scope,String correlation) {this.span=span;this.scope=scope;previous=org.slf4j.MDC.get("correlationId");if(correlation!=null)org.slf4j.MDC.put("correlationId",correlation);}
        public void close() {if(previous==null)org.slf4j.MDC.remove("correlationId");else org.slf4j.MDC.put("correlationId",previous);scope.close();span.end();}
    }
}
