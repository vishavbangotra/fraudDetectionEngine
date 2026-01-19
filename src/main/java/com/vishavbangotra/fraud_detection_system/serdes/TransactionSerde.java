package com.vishavbangotra.fraud_detection_system.serdes;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

public class TransactionSerde implements Serde<Transaction> {

    @Override
    public Serializer<Transaction> serializer() {
        return new TransactionSerializer();
    }

    @Override
    public Deserializer<Transaction> deserializer() {
        return new TransactionDeserializer();
    }
}
