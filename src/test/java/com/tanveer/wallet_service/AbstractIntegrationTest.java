package com.tanveer.wallet_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {
}
