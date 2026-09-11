package com.finflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SmsNotificationProvider implements SmsProvider {

    private static final Logger log =
            LoggerFactory.getLogger(SmsNotificationProvider.class);

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String send(String recipient, String body) {

        String messageId = "MOCK-SMS-" + UUID.randomUUID();

        log.info(
                "[MOCK SMS] Simulated SMS delivery | messageId={} | recipient={} | body={}",
                messageId,
                recipient,
                body
        );

        return messageId;
    }
}