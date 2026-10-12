package com.bank.customerservice.service;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
class AccountOverviewClientTest {
    private HttpServer server;
    private AccountOverviewClient client;
    private String payload;
    private int responseStatus;
    @BeforeEach void setup() throws Exception {
        responseStatus=200;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/accounts/customer/1",exchange->{
            var bytes=payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(responseStatus,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();var tokens=org.mockito.Mockito.mock(com.bank.security.ServiceTokenProvider.class);org.mockito.Mockito.when(tokens.token()).thenReturn("fixture-service-token");client=new AccountOverviewClient(RestClient.builder(),"http://127.0.0.1:"+server.getAddress().getPort(),tokens);
    }
    @AfterEach void stop() {server.stop(0);}
    @Test void acceptsAValidPageOverHttp() {
        payload="{\"items\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}";
        assertThat(client.get(1,0,20).items()).isEmpty();
    }
    @Test void rejectsAnAccountFromAnotherCustomer() {
        payload="{\"items\":[{\"customerId\":2}],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}";
        assertThatThrownBy(()->client.get(1,0,20)).isInstanceOf(AccountOverviewUnavailable.class);
    }
    @Test void rejectsUnavailableAndMalformedResponses() {
        payload="{}";responseStatus=503;
        assertThatThrownBy(()->client.get(1,0,20)).isInstanceOf(AccountOverviewUnavailable.class);
        responseStatus=200;payload="not json";
        assertThatThrownBy(()->client.get(1,0,20)).isInstanceOf(AccountOverviewUnavailable.class);
    }
}
