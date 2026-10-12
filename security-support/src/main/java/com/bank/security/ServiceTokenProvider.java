package com.bank.security;

import java.net.http.HttpClient;
import java.time.*;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Client credentials stay server-side; cache bounded tokens and renew before expiry. */
public class ServiceTokenProvider {
    private final RestClient client;
    private final String uri, clientId, secret;
    private String token;
    private Instant renewAt = Instant.EPOCH;
    public ServiceTokenProvider(String uri,String clientId,String secret) {
        this.uri=uri;this.clientId=clientId;this.secret=secret;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));client=RestClient.builder().requestFactory(factory).build();
    }
    public synchronized String token() {
        if(token != null && Instant.now().isBefore(renewAt)) return token;
        if(secret == null || secret.isBlank()) throw new IllegalStateException("Service credentials are not configured.");
        var form=new LinkedMultiValueMap<String,String>();
        form.add("grant_type","client_credentials");form.add("client_id",clientId);form.add("client_secret",secret);
        try {
            var body=client.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            if(body == null || !(body.get("access_token") instanceof String value) || value.isBlank() || !(body.get("expires_in") instanceof Number ttl) || ttl.longValue() <= 30)
                throw new IllegalStateException("Unconfirmed service authentication.");
            token=value;renewAt=Instant.now().plusSeconds(Math.min(ttl.longValue()-30,300));return token;
        } catch (org.springframework.web.client.RestClientException unavailable) {
            // Do not propagate IdP response bodies, credentials or token material into application logs.
            throw new IllegalStateException("Service authentication is unavailable.");
        }
    }
}
