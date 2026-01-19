package com.vishavbangotra.fraud_detection_system.serdes;

import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.kafka.support.serializer.DeserializationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishavbangotra.fraud_detection_system.model.Transaction;

public class TransactionDeserializer implements Deserializer<Transaction> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Transaction deserialize(String topic, byte[] bytes) {
        try {
            return mapper.readValue(bytes, Transaction.class);
        } catch (Exception e) {
            throw new DeserializationException(topic, bytes, false, null);
        }
    }

}
