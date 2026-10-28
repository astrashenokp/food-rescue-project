package com.example.foodrescue.delivery.internal;

import com.example.foodrescue.delivery.DeliveryFinishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** Best-effort outbound notification. Delivery completion is never rolled back by webhook errors. */
@Component
public class DeliveryAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAlertNotifier.class);

    private final RestClient client;
    private final String url;

    public DeliveryAlertNotifier(@Value("${foodrescue.alerts.url}") String url,
                                 @Value("${foodrescue.alerts.timeout:2s}") Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(factory).build();
        this.url = url;
    }

    @ApplicationModuleListener
    void on(DeliveryFinishedEvent event) {
        try {
            client.post().uri(url)
                    .body(Map.of("title", "DELIVERY_" + event.outcome(), "message", "lot " + event.lotId()))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Alert webhook failed: {}", ex.getMessage());
        }
    }
}
