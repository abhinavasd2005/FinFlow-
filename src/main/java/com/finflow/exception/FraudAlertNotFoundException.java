package com.finflow.exception;

public class FraudAlertNotFoundException extends RuntimeException {
    public FraudAlertNotFoundException(String message) {
        super(message);
    }
}