package com.example.foodrescue.delivery;

import com.example.foodrescue.IntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@IntegrationTest
class DeliveryAlertIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired UserDetailsService users;
    @Autowired WireMockServer alertsServer;

    @BeforeEach
    void prepareStub() {
        alertsServer.resetAll();
        alertsServer.stubFor(WireMock.post("/alerts").willReturn(ok()));
    }

    @Test
    void confirmation_ShouldSendExpectedJsonWebhook() throws Exception {
        UUID id = confirmDelivery();
        awaitWebhook(id);
        alertsServer.verify(1, postRequestedFor(urlEqualTo("/alerts"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(containing(id.toString()))
                .withRequestBody(WireMock.matchingJsonPath("$.title", equalTo("DELIVERY_CONFIRMED"))));
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void webhook_ServerError_ShouldNotRollbackConfirmation() throws Exception {
        alertsServer.stubFor(WireMock.post("/alerts")
                .willReturn(aResponse().withStatus(500)));
        UUID id = confirmDelivery();
        awaitWebhook(id);
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void webhook_Timeout_ShouldNotRollbackConfirmation() throws Exception {
        alertsServer.stubFor(WireMock.post("/alerts")
                .willReturn(aResponse().withStatus(200).withFixedDelay(1500)));
        UUID id = confirmDelivery();
        awaitWebhook(id);
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    private UUID confirmDelivery() throws Exception {
        DeliveryTestFixtures fixtures = new DeliveryTestFixtures(mvc, jdbc, users);
        UUID id = fixtures.createDeliveredLot();
        fixtures.confirm(id);
        return id;
    }

    private void awaitWebhook(UUID id) {
        await().atMost(5, SECONDS).untilAsserted(() -> alertsServer.verify(1,
                postRequestedFor(urlEqualTo("/alerts"))
                        .withRequestBody(containing(id.toString()))));
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from food_lots where id = :id")
                .param("id", id).query(String.class).single();
    }
}
