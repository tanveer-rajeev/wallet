package com.tanveer.wallet_service.controller;

import com.tanveer.wallet_service.domain.Wallet;
import com.tanveer.wallet_service.dto.DepositRequest;
import com.tanveer.wallet_service.dto.CreateWalletRequest;
import com.tanveer.wallet_service.dto.DepositResponse;
import com.tanveer.wallet_service.dto.WalletResponse;
import com.tanveer.wallet_service.service.DepositService;
import com.tanveer.wallet_service.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;
    private final DepositService depositService;

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public DepositResponse deposit(@PathVariable UUID id,
                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @Valid @RequestBody DepositRequest request) {
        return DepositResponse.from(depositService.deposit(id, request.amount(), idempotencyKey));
    }

    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        Wallet wallet = walletService.createWallet(request.ownerId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(wallet.getId())
                .toUri();
        return ResponseEntity.created(location).body(WalletResponse.from(wallet));
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable UUID id) {
        return WalletResponse.from(walletService.getWallet(id));
    }

//    @PostMapping("/{id}/deposit")
//    public WalletResponse deposit(@PathVariable UUID id, @RequestBody DepositRequest depositRequest){
//        return WalletResponse.from(walletService.deposit(id,depositRequest));
//    }
}
