package com.bank.notificationservice.store;
import com.bank.contracts.posting.v1.PostingCommandV1;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.*;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import static com.mongodb.client.model.Filters.eq;
@Component
public class DeadLetterStore {
    private final MongoTemplate mongo;
    public DeadLetterStore(MongoTemplate mongo) {this.mongo=mongo;}
    public void retain(String topic,int partition,long offset,String original,String key,String payload) {
        var id=PostingCommandV1.hash(topic+"|"+partition+"|"+offset);
        var doc=new Document("_id",id).append("dltTopic",topic).append("partition",partition).append("offset",offset)
                .append("originalTopic",original).append("key",key).append("payload",payload).append("receivedAt",Date.from(Instant.now()));
        var result=mongo.getCollection("dead_letters").withWriteConcern(WriteConcern.MAJORITY.withJournal(true))
                .updateOne(eq("_id",id),new Document("$setOnInsert",doc),new UpdateOptions().upsert(true));
        if(!result.wasAcknowledged())throw new IllegalStateException("Unacknowledged quarantine write.");
    }
    public Document get(String id) {return mongo.getCollection("dead_letters").find(eq("_id",id)).first();}
    public Map<String,Object> page(int page,int size) {
        var c=mongo.getCollection("dead_letters");long total=c.countDocuments();var rows=new ArrayList<Map<String,Object>>();
        c.find().sort(new Document("receivedAt",-1).append("_id",-1)).skip(page*size).limit(size).forEach(d->{
            var row=new LinkedHashMap<String,Object>(d);row.put("id",row.remove("_id"));rows.add(row);
        });return Map.of("items",rows,"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size);
    }
    public void replayed(String id,String eventId) {
        mongo.getCollection("dead_letters").withWriteConcern(WriteConcern.MAJORITY.withJournal(true)).updateOne(eq("_id",id),
                new Document("$set",new Document("lastReplayEventId",eventId).append("lastReplayAt",Date.from(Instant.now()))).append("$inc",new Document("replayRequests",1)));
    }
}
