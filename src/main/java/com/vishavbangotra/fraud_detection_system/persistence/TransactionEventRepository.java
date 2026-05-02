package com.vishavbangotra.fraud_detection_system.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionEventRepository extends JpaRepository<TransactionEventEntity, Long> {
}
