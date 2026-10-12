package com.bank.accountservice.events;

import com.bank.contracts.events.v1.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.web.context.request.*;

@Component
public class EventOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final com.bank.observability.DurableTrace trace;
    public EventOutbox(JdbcTemplate jdbc,ObjectMapper json,Clock clock,com.bank.observability.DurableTrace trace) { this.jdbc=jdbc;this.json=json;this.clock=clock;this.trace=trace; }
    public static UUID currentCorrelation() {
        var attributes=RequestContextHolder.getRequestAttributes();
        if(attributes instanceof ServletRequestAttributes servlet && servlet.getRequest().getAttribute("bank.correlationId") instanceof UUID id) return id;
        return UUID.randomUUID();
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void add(EventType type,String aggregate,UUID correlation,Map<String,String> data) {
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        var event=new BankEventV1(UUID.randomUUID(),type,1,correlation,type.producer(),now,aggregate,data);
        String payload;
        try { payload=json.writeValueAsString(event); } catch(JsonProcessingException invalid) { throw new IllegalStateException("Could not serialize event.",invalid); }
        jdbc.update("INSERT INTO event_outbox(event_id,event_type,aggregate_id,topic,payload,next_attempt_at,created_at,traceparent) VALUES (?,?,?,?,?,?,?,?)",
                event.eventId().toString(),type.name(),aggregate,type.topic(),payload,Timestamp.from(now),Timestamp.from(now),trace.capture());
    }
}
