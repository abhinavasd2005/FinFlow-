package com.finflow.notification;

public interface SmsProvider {

    /**
     * Indicates whether this SMS provider is available.
     */
    boolean isEnabled();

    /**
     * Sends an SMS.
     *
     * @param recipient phone number
     * @param body message body
     * @return provider message ID
     */
    String send(String recipient, String body);
}