package com.tanveer.wallet_service.biller;

public interface BillerClient {

    BillerDecision pay(String reference, long amount);
}
