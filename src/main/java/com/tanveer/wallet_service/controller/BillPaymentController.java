package com.tanveer.wallet_service.controller;

import com.tanveer.wallet_service.dto.BillPaymentRequest;
import com.tanveer.wallet_service.dto.BillPaymentResponse;
import com.tanveer.wallet_service.service.BillPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bill-payments")
@RequiredArgsConstructor
public class BillPaymentController {

    private final BillPaymentService billPaymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillPaymentResponse pay(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @Valid @RequestBody BillPaymentRequest request) {
        return BillPaymentResponse.from(
                billPaymentService.pay(request.walletId(), request.amount(), idempotencyKey));
    }
}
