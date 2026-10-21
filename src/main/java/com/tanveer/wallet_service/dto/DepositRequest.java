package com.tanveer.wallet_service.dto;

import com.tanveer.wallet_service.domain.MoneyLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DepositRequest(
        @NotNull(message = "amount is required")
        @Min(value = MoneyLimits.MIN_AMOUNT, message = "amount must be at least 1")
        @Max(value = MoneyLimits.MAX_AMOUNT, message = "amount must be at most 5000000")
        Long amount
) {
}
