package com.example.foodrescue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@TestConfiguration(proxyBeanMethods = false)
public class AlertsStubConfiguration {

    @Bean(destroyMethod = "stop")
    WireMockServer alertsServer() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        server.stubFor(WireMock.post("/alerts").willReturn(ok()));
        return server;
    }

    @Bean
    DynamicPropertyRegistrar alertsProperties(WireMockServer alertsServer) {
        return registry -> registry.add("foodrescue.alerts.url", () -> alertsServer.baseUrl() + "/alerts");
    }
}
