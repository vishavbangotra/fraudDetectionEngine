package com.vishavbangotra.fraud_detection_system.scoring.rules;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

@Component
public class VelocityRule implements Rule {

    private final StringRedisTemplate redis;
    private final long windowSeconds;
    private final long maxInWindow;

    public VelocityRule(
            StringRedisTemplate redis,
            @Value("${fraud.rules.velocity.window-seconds:60}") long windowSeconds,
            @Value("${fraud.rules.velocity.max:5}") long maxInWindow) {
        this.redis = redis;
        this.windowSeconds = windowSeconds;
        this.maxInWindow = maxInWindow;
    }

    @Override
    public String name() {
        return "VELOCITY";
    }

    @Override
    public int weight() {
        return 25;
    }

    @Override
    public boolean evaluate(Transaction txn) {
        if (txn == null || txn.getCustomerId() == null || txn.getTransactionId() == null) {
            return false;
        }
        String key = "velocity:" + txn.getCustomerId();
        long nowMs = txn.getTimestamp() != null ? txn.getTimestamp().toEpochMilli() : System.currentTimeMillis();
        long cutoffMs = nowMs - Duration.ofSeconds(windowSeconds).toMillis();

        redis.opsForZSet().add(key, txn.getTransactionId(), nowMs);
        redis.opsForZSet().removeRangeByScore(key, 0, cutoffMs);
        Long count = redis.opsForZSet().zCard(key);
        redis.expire(key, Duration.ofSeconds(windowSeconds * 2));

        return count != null && count > maxInWindow;
    }
}
