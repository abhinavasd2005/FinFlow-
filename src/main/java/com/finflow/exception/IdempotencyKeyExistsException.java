package com.finflow.exception;

import com.finflow.dto.response.TransferResponse;

public class IdempotencyKeyExistsException extends RuntimeException {

    private final TransferResponse cachedResponse;

    public IdempotencyKeyExistsException(TransferResponse cachedResponse) {
        super("Duplicate request");
        this.cachedResponse = cachedResponse;
    }

    public TransferResponse getCachedResponse() {
        return cachedResponse;
    }
}