package com.finflow.service;

import com.finflow.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    @Async
    public void analyze(Transaction transaction) {
        log.info("Fraud analysis started for transaction {}", transaction.getId());
    }
}