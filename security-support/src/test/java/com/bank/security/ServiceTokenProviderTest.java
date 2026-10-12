package com.bank.security;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ServiceTokenProviderTest {
    @Test void clientCredentialsAreExchangedOnceAndCached() throws Exception {
        var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/token",exchange -> {calls.incrementAndGet();var body=new String(exchange.getRequestBody().readAllBytes());
            assertThat(body).contains("grant_type=client_credentials","client_id=transaction-service","client_secret=local-test-secret");
            byte[] json="{\"access_token\":\"bounded-token\",\"expires_in\":300}".getBytes();exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,json.length);exchange.getResponseBody().write(json);exchange.close();});server.start();
        try {var provider=new ServiceTokenProvider("http://127.0.0.1:"+server.getAddress().getPort()+"/token","transaction-service","local-test-secret");
            assertThat(provider.token()).isEqualTo("bounded-token");assertThat(provider.token()).isEqualTo("bounded-token");assertThat(calls).hasValue(1);
        } finally {server.stop(0);}
    }
    @Test void unconfiguredAndFailedAuthenticationDoNotExposeIdpResponses() throws Exception {
        assertThatThrownBy(()->new ServiceTokenProvider("http://127.0.0.1:1","account-service","").token()).hasMessage("Service credentials are not configured.");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/token",exchange -> {byte[] body="private-server-response".getBytes();exchange.sendResponseHeaders(401,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try {assertThatThrownBy(()->new ServiceTokenProvider("http://127.0.0.1:"+server.getAddress().getPort()+"/token","account-service","local-test-secret").token())
            .hasMessage("Service authentication is unavailable.").hasNoCause();} finally {server.stop(0);}
    }
}
