package com.example.foodrescue.infrastructure.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "foodrescue.observability")
public record ObservabilityProperties(boolean traceIdEnabled, String traceHeader) {

    public ObservabilityProperties {
        if (traceHeader == null) {
            traceHeader = "X-Trace-Id";
        }
    }
}
