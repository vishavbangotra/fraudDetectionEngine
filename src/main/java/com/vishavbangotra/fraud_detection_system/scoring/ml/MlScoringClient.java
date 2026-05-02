package com.vishavbangotra.fraud_detection_system.scoring.ml;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@Slf4j
public class MlScoringClient {

    private final String scoreUrl;
    private final RestClient restClient;

    @Autowired
    public MlScoringClient(
            @Value("${fraud.rules.ml.url:http://localhost:8090/score}") String scoreUrl,
            @Value("${fraud.rules.ml.connect-timeout-ms:250}") long connectTimeoutMs,
            @Value("${fraud.rules.ml.read-timeout-ms:500}") long readTimeoutMs) {
        this(scoreUrl, RestClient.builder()
                .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
                .build());
    }

    MlScoringClient(String scoreUrl, RestClient restClient) {
        this.scoreUrl = scoreUrl;
        this.restClient = restClient;
    }

    public Optional<MlScoreResponse> score(Transaction txn) {
        if (txn == null) {
            return Optional.empty();
        }

        try {
            MlScoreResponse response = restClient.post()
                    .uri(scoreUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(txn)
                    .retrieve()
                    .body(MlScoreResponse.class);
            if (response == null || !response.isValid()) {
                log.warn("ML sidecar returned invalid response for txn {}",
                        txn.getTransactionId());
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("ML sidecar scoring failed for txn {}: {}",
                    txn.getTransactionId(), e.getMessage());
            return Optional.empty();
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }
}
