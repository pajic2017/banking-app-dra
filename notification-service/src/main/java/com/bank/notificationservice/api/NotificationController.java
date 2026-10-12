package com.bank.notificationservice.api;
import com.bank.contracts.events.v1.EventType;
import com.bank.notificationservice.events.EventCodec;
import com.bank.notificationservice.store.*;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@org.springframework.security.access.prepost.PreAuthorize("@access.admin()")
@RestController
@Validated
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationStore notifications;private final DeadLetterStore deadLetters;private final EventCodec codec;
    private final KafkaTemplate<String,String> kafka;
    public NotificationController(NotificationStore notifications,DeadLetterStore deadLetters,EventCodec codec,KafkaTemplate<String,String> kafka) {this.notifications=notifications;this.deadLetters=deadLetters;this.codec=codec;this.kafka=kafka;}
    @GetMapping public Map<String,Object> page(@RequestParam(required=false) EventType eventType,@RequestParam(required=false) @Size(max=64) String aggregateId,
            @RequestParam(required=false) UUID correlationId,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return notifications.page(eventType==null?null:eventType.name(),aggregateId,correlationId==null?null:correlationId.toString(),page,size);
    }
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id) {
        var notification=notifications.get(id.toString());if(notification==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification was not found.");return notification;
    }
    @GetMapping("/dead-letters") public Map<String,Object> deadLetters(@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return deadLetters.page(page,size);}
    public record ReplayRequest(String correctedPayload) { }
    @PostMapping("/dead-letters/{id}/replay") public ResponseEntity<Map<String,String>> replay(@PathVariable @Pattern(regexp="[a-f0-9]{64}") String id,@RequestBody(required=false) ReplayRequest request) {
        var record=deadLetters.get(id);if(record==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Dead letter was not found.");
        var payload=request!=null && request.correctedPayload()!=null?request.correctedPayload():record.getString("payload");
        var event=codec.decode(payload,record.getString("originalTopic"));
        try {kafka.send(event.eventType().topic(),event.aggregateId(),codec.canonical(event)).get(9,TimeUnit.SECONDS);}
        catch(Exception unavailable) {
            if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Replay is unconfirmed; retry using the same event ID.");
        }
        deadLetters.replayed(id,event.eventId().toString());
        return ResponseEntity.accepted().body(Map.of("eventId",event.eventId().toString(),"status","REPLAY_REQUESTED"));
    }
}
