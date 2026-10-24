package com.tanveer.wallet_service.biller;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class HttpBillerClient implements BillerClient {

    private final RestClient restClient;

    public HttpBillerClient(@Value("${biller.base-url:http://localhost:9090}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }


    @Override
    public BillerDecision pay(String reference, long amount) {
        try {
            return restClient.post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new BillerPaymentRequest(reference, amount))
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();

                        if (status.value() == 200) {
                            return BillerDecision.APPROVED;
                        }

                        if (status.value() == 422) {
                            return BillerDecision.DECLINED;
                        }

                        throw new BillerTimeoutException("Unexpected biller status " + status.value());
                    });

        } catch (ResourceAccessException e) {
            throw new BillerTimeoutException("Biller did not answer", e);
        }
    }

}
