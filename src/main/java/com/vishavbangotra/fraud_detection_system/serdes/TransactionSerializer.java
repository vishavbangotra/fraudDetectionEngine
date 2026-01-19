package com.vishavbangotra.fraud_detection_system.serdes;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishavbangotra.fraud_detection_system.model.Transaction;

public class TransactionSerializer implements Serializer<Transaction> {
    ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, Transaction data) {
        try {
            return mapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

}
