package com.vishavbangotra.fraud_detection_system.serdes;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;

public class ScoredTransactionSerde implements Serde<ScoredTransaction> {

    @Override
    public Serializer<ScoredTransaction> serializer() {
        return new ScoredTransactionSerializer();
    }

    @Override
    public Deserializer<ScoredTransaction> deserializer() {
        return new ScoredTransactionDeserializer();
    }
}
