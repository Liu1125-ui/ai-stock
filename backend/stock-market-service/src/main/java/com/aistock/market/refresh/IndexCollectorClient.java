package com.aistock.market.refresh;

import com.aistock.common.BusinessException;
import com.aistock.market.quotation.IndexSnapshotStore.Candidate;
import com.aistock.market.quotation.IndexSnapshotStore.Request;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IndexCollectorClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final JsonMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final URI endpoint;
    private final String token;

    public IndexCollectorClient(@Value("${aistock.algorithm.url:http://127.0.0.1:18090}") String url,
            @Value("${aistock.internal.token:}") String token) {
        endpoint = URI.create(url + "/internal/v1/market/index-collect");
        this.token = token;
    }

    public Candidate collect(Request input) {
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                    .header("X-Service-Token", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(input))).build();
            var response = client.send(request, info -> new AlgorithmCollectorClient.LimitedBody(16384));
            if (response.statusCode() != 200) throw unavailable();
            var root = mapper.readTree(response.body());
            if (!"AVAILABLE".equals(root.path("state").asText()) || !root.path("candidate").isObject()
                    || !root.path("errorCode").isNull()) throw unavailable();
            return mapper.treeToValue(root.get("candidate"), Candidate.class);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception error) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException("INDEX_SOURCE_UNAVAILABLE", "指数数据来源暂不可用", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
