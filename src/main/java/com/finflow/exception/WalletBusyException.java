package com.finflow.exception;

public class WalletBusyException extends RuntimeException {
    public WalletBusyException(String message) {
        super(message);
    }
}