package com.bank.transactionservice.client;

import com.bank.contracts.posting.v1.*;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PostingClient {
    private final RestClient rest;
    public PostingClient(RestClient.Builder builder, @Value("${bank.account-service-url:http://localhost:8083}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        rest = builder.baseUrl(url).requestFactory(factory).build();
    }

    public PostingResultV1 post(PostingCommandV1 command) {
        var result = rest.post().uri("/internal/v1/postings").body(command).retrieve().body(PostingResultV1.class);
        if (result == null || !command.operationId().equals(result.operationId()) || result.status() == null ||
                !command.instructionHash().equals(result.instructionHash()) || result.recordedAt() == null ||
                (result.status() == PostingStatus.REJECTED) != (result.failureCode() != null)) {
            throw new IllegalStateException("Invalid posting receipt.");
        }
        return result;
    }
}
