package com.vishavbangotra.fraud_detection_system.alerting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WebhookAlertService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 500;

    private final String webhookUrl;
    private final RestClient restClient;

    public WebhookAlertService(@Value("${alerts.webhook.url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.restClient = RestClient.create();
    }

    public void send(ScoredTransaction scored) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Webhook URL not configured; skipping alert for txn {}",
                    scored.getTransaction().getTransactionId());
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restClient.post()
                        .uri(webhookUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(scored)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Webhook alert sent for txn {} (attempt {})",
                        scored.getTransaction().getTransactionId(), attempt);
                return;
            } catch (RestClientException e) {
                log.warn("Webhook attempt {}/{} failed for txn {}: {}",
                        attempt, MAX_ATTEMPTS,
                        scored.getTransaction().getTransactionId(), e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(BACKOFF_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("Webhook alert exhausted retries for txn {}",
                scored.getTransaction().getTransactionId());
    }
}
