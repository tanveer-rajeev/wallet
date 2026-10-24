package com.tanveer.wallet_service;

import com.tanveer.wallet_service.support.FakeBiller;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FakeBillerConfig {

    @Bean
    @Primary
    FakeBiller fakeBiller() {
        return new FakeBiller(42);
    }
}
