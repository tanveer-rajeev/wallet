package com.tanveer.wallet_service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WalletApiTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createWalletReturns201WithZeroBalance() throws Exception {
        MvcResult result = createWallet("user-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value("user-1"))
                .andExpect(jsonPath("$.currency").value("IQD"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/wallets/" + id);

        UUID walletId = UUID.fromString(id);
        assertThat(jdbc.queryForObject("SELECT kind FROM wallets WHERE id = ?", String.class, walletId))
                .isEqualTo("CUSTOMER");
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE id = ?", Long.class, walletId))
                .isZero();
    }

    @Test
    void createWalletRejectsBlankOwner() throws Exception {
        createWallet("   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.ownerId").isNotEmpty());
    }


    @Test
    void getWalletReturnsWhatWasCreated() throws Exception {
        String id = JsonPath.read(
                createWallet("user-2").andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/wallets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.ownerId").value("user-2"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getUnknownWalletReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }


    private org.springframework.test.web.servlet.ResultActions createWallet(String ownerId) throws Exception {
        return mockMvc.perform(post("/api/v1/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"" + ownerId + "\"}"));
    }
}
