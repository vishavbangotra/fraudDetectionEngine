package com.vishavbangotra.fraud_detection_system.serdes;

import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.kafka.support.serializer.DeserializationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;

public class ScoredTransactionDeserializer implements Deserializer<ScoredTransaction> {

    private final ObjectMapper mapper = JsonMapper.create();

    @Override
    public ScoredTransaction deserialize(String topic, byte[] bytes) {
        try {
            return mapper.readValue(bytes, ScoredTransaction.class);
        } catch (Exception e) {
            throw new DeserializationException(topic, bytes, false, null);
        }
    }
}
