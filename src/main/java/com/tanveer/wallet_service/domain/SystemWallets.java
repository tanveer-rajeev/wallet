package com.tanveer.wallet_service.domain;

import java.util.UUID;

public final class SystemWallets {
    public static final UUID FEE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID CASH_IN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID BILLER_CLEARING = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private SystemWallets() {
    }
}
