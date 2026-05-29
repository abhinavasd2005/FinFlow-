package com.finflow.service;

import com.finflow.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Async("notificationExecutor")
    public void send(Transaction transaction) {
        log.info("[NOTIFICATION] Transaction {} | Status: {} | Amount: {}",
                transaction.getId(),
                transaction.getStatus(),
                transaction.getAmount());
    }
}