package com.bank.notificationservice.events;
import com.bank.contracts.events.v1.*;
import com.bank.contracts.posting.v1.PostingCommandV1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
@Component
public class EventCodec {
    private final ObjectMapper json;
    public EventCodec(ObjectMapper json) {this.json=json;}
    public BankEventV1 decode(String payload,String topic) {
        try {
            var event=json.readValue(payload,BankEventV1.class);
            if(event==null || !event.eventType().topic().equals(topic)) throw new InvalidEvent("Event schema or topic does not match.");
            return event;
        } catch(JsonProcessingException | IllegalArgumentException invalid) {throw new InvalidEvent("Unsupported or malformed event.");}
    }
    public String canonical(BankEventV1 event) {
        try {return json.writeValueAsString(event);} catch(JsonProcessingException invalid) {throw new InvalidEvent("Invalid event.");}
    }
    public String fingerprint(BankEventV1 event) {return PostingCommandV1.hash(canonical(event));}
}
