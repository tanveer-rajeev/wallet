package com.tanveer.wallet_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotBlank(message = "ownerId is required")
        @Size(max = 64, message = "ownerId must be at most 64 characters")
        @Pattern(regexp = "^(?!(?i:system)$).+$", message = "ownerId 'SYSTEM' is reserved")
        String ownerId
) {
}
