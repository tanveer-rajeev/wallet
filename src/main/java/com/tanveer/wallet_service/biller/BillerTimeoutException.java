package com.tanveer.wallet_service.biller;

public class BillerTimeoutException extends RuntimeException {

    public BillerTimeoutException(String message) {
        super(message);
    }

    public BillerTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
