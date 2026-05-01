package com.vishavbangotra.fraud_detection_system.serdes;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;

public class ScoredTransactionSerializer implements Serializer<ScoredTransaction> {

    private final ObjectMapper mapper = JsonMapper.create();

    @Override
    public byte[] serialize(String topic, ScoredTransaction data) {
        try {
            return mapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }
}
