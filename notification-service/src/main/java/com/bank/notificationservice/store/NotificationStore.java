package com.bank.notificationservice.store;

import com.bank.contracts.events.v1.BankEventV1;
import com.bank.notificationservice.events.*;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.*;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import static com.mongodb.client.model.Filters.eq;

@Component
public class NotificationStore {
    private final MongoTemplate mongo;
    private final EventCodec codec;
    public NotificationStore(MongoTemplate mongo,EventCodec codec) {this.mongo=mongo;this.codec=codec;}
    public boolean accept(BankEventV1 event,String topic,int partition,long offset) {
        var collection=mongo.getCollection("notifications").withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        String id=event.eventId().toString(),hash=codec.fingerprint(event);
        var doc=new Document("_id",id).append("eventType",event.eventType().name()).append("aggregateId",event.aggregateId())
                .append("correlationId",event.correlationId().toString()).append("payload",codec.canonical(event))
                .append("fingerprint",hash).append("topic",topic).append("partition",partition).append("offset",offset)
                .append("receivedAt",Date.from(Instant.now()));
        boolean inserted=false;
        try {
            var result=collection.updateOne(eq("_id",id),new Document("$setOnInsert",doc),new UpdateOptions().upsert(true));
            if(!result.wasAcknowledged())throw new IllegalStateException("Unacknowledged notification write.");
            inserted=result.getUpsertedId()!=null;
        } catch(MongoWriteException concurrent) {if(concurrent.getCode()!=11000)throw concurrent;}
        var stored=collection.find(eq("_id",id)).first();
        if(stored==null)throw new IllegalStateException("Notification write is unconfirmed.");
        if(!hash.equals(stored.getString("fingerprint")))throw new InvalidEvent("Event ID reused with different contents.");
        return inserted;
    }
    public Map<String,Object> get(String id) {
        var doc=mongo.getCollection("notifications").find(eq("_id",id)).first();
        return doc==null?null:view(doc);
    }
    public Map<String,Object> page(String eventType,String aggregate,String correlation,int page,int size) {
        var filter=new Document();if(eventType!=null)filter.put("eventType",eventType);
        if(aggregate!=null)filter.put("aggregateId",aggregate);if(correlation!=null)filter.put("correlationId",correlation);
        var collection=mongo.getCollection("notifications");long count=collection.countDocuments(filter);
        var rows=new ArrayList<Map<String,Object>>();
        collection.find(filter).sort(new Document("receivedAt",-1).append("_id",-1)).skip(page*size).limit(size).forEach(d->rows.add(view(d)));
        return Map.of("items",rows,"page",page,"size",size,"totalElements",count,"totalPages",(count+size-1)/size);
    }
    private Map<String,Object> view(Document doc) {
        var result=new LinkedHashMap<String,Object>();result.put("id",doc.getString("_id"));
        result.put("event",codec.decode(doc.getString("payload"),doc.getString("topic")));
        for(var field:List.of("topic","partition","offset","receivedAt"))result.put(field,doc.get(field));return result;
    }
}
