package com.bank.contracts.events.v1;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BankEventV1Test {
    @Test void validatesVersionProducerAndRequiredData() {
        for(int version:new int[]{0,2})assertThatThrownBy(()->new BankEventV1(UUID.randomUUID(),EventType.CUSTOMER_CREATED,version,UUID.randomUUID(),"customer-service",Instant.now(),"1",Map.of("customerId","1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BankEventV1(UUID.randomUUID(),EventType.CUSTOMER_CREATED,1,UUID.randomUUID(),"account-service",Instant.now(),"1",Map.of("customerId","1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BankEventV1(UUID.randomUUID(),EventType.CUSTOMER_CREATED,1,UUID.randomUUID(),"customer-service",Instant.now(),"2",Map.of("customerId","1"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void envelopeDefensivelyCopiesPayloadAndTopicMappingIsExplicit() {
        var data=new HashMap<String,String>();data.put("customerId","1");
        var event=new BankEventV1(UUID.randomUUID(),EventType.CUSTOMER_CREATED,1,UUID.randomUUID(),"customer-service",Instant.now(),"1",data);
        data.put("customerId","2");assertThat(event.data().get("customerId")).isEqualTo("1");
        assertThatThrownBy(()->event.data().put("customerId","3")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(event.eventType().topic()).isEqualTo("bank.customer.created");
    }
}
