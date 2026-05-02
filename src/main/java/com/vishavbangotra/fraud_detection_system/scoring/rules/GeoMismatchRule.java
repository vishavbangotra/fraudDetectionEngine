package com.vishavbangotra.fraud_detection_system.scoring.rules;

import java.time.Duration;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

@Component
public class GeoMismatchRule implements Rule {

    private static final long IMPOSSIBLE_TRAVEL_WINDOW_MS = Duration.ofHours(1).toMillis();
    private static final Duration KEY_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public GeoMismatchRule(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String name() {
        return "GEO_MISMATCH";
    }

    @Override
    public int weight() {
        return 25;
    }

    @Override
    public boolean evaluate(Transaction txn) {
        if (txn == null || txn.getCustomerId() == null || txn.getCountry() == null) {
            return false;
        }
        String key = "geo:" + txn.getCustomerId();
        long nowMs = txn.getTimestamp() != null ? txn.getTimestamp().toEpochMilli() : System.currentTimeMillis();

        HashOperations<String, Object, Object> hash = redis.opsForHash();
        String prevCountry = (String) hash.get(key, "country");
        Object prevSeenObj = hash.get(key, "lastSeenMillis");
        long prevSeenMs = prevSeenObj == null ? 0L : Long.parseLong(prevSeenObj.toString());

        boolean fired = prevCountry != null
                && !prevCountry.equals(txn.getCountry())
                && (nowMs - prevSeenMs) < IMPOSSIBLE_TRAVEL_WINDOW_MS;

        hash.put(key, "country", txn.getCountry());
        hash.put(key, "lastSeenMillis", Long.toString(nowMs));
        redis.expire(key, KEY_TTL);

        return fired;
    }
}
