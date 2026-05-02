package com.vishavbangotra.fraud_detection_system.scoring.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

class MlScoringClientTest {

    private Transaction txn() {
        return Transaction.builder()
                .transactionId("t1")
                .customerId("c1")
                .merchantId("m1")
                .amount(12500.0)
                .country("US")
                .deviceId("d1")
                .timestamp(Instant.parse("2026-05-01T12:34:56Z"))
                .build();
    }

    @Test
    void postsTransactionAndParsesValidResponse() {
        TestClient testClient = testClient();
        testClient.server.expect(once(), requestTo("http://ml-sidecar/score"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"transactionId\":\"t1\"")))
                .andRespond(withSuccess("""
                {"model":"isolation_forest","modelVersion":"iforest-demo-v1","riskScore":0.82,"anomaly":true,"reasonCodes":["high_amount"]}
                """, MediaType.APPLICATION_JSON));

        Optional<MlScoreResponse> response = testClient.client.score(txn());

        assertThat(response).isPresent();
        assertThat(response.get().riskScore()).isEqualTo(0.82);
        assertThat(response.get().reasonCodes()).containsExactly("high_amount");
        testClient.server.verify();
    }

    @Test
    void non2xxResponseReturnsEmpty() {
        TestClient testClient = testClient();
        testClient.server.expect(once(), requestTo("http://ml-sidecar/score"))
                .andRespond(withServerError());

        assertThat(testClient.client.score(txn())).isEmpty();
        testClient.server.verify();
    }

    @Test
    void invalidRiskScoreReturnsEmpty() {
        TestClient testClient = testClient();
        testClient.server.expect(once(), requestTo("http://ml-sidecar/score"))
                .andRespond(withSuccess("""
                {"model":"isolation_forest","modelVersion":"iforest-demo-v1","riskScore":1.2,"anomaly":true,"reasonCodes":[]}
                """, MediaType.APPLICATION_JSON));

        assertThat(testClient.client.score(txn())).isEmpty();
        testClient.server.verify();
    }

    private TestClient testClient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MlScoringClient client = new MlScoringClient("http://ml-sidecar/score", builder.build());
        return new TestClient(client, server);
    }

    private record TestClient(MlScoringClient client, MockRestServiceServer server) {
    }
}
