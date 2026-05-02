package com.vishavbangotra.fraud_detection_system.scoring.rules;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

@Component
public class NewDeviceRule implements Rule {

    private static final Duration KEY_TTL = Duration.ofDays(90);

    private final StringRedisTemplate redis;

    public NewDeviceRule(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String name() {
        return "NEW_DEVICE";
    }

    @Override
    public int weight() {
        return 10;
    }

    @Override
    public boolean evaluate(Transaction txn) {
        if (txn == null || txn.getCustomerId() == null || txn.getDeviceId() == null) {
            return false;
        }
        String key = "devices:" + txn.getCustomerId();
        Boolean isMember = redis.opsForSet().isMember(key, txn.getDeviceId());
        boolean fired = isMember == null || !isMember;
        redis.opsForSet().add(key, txn.getDeviceId());
        redis.expire(key, KEY_TTL);
        return fired;
    }
}
