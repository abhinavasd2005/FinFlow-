package com.finflow.exception;

public class FrozenWalletException extends RuntimeException {
    public FrozenWalletException(String message) {
        super(message);
    }
}