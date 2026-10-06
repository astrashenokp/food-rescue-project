package com.example.foodrescue.common;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(
        prefix = "foodrescue.observability",
        name = "trace-id-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter(ObservabilityProperties properties) {
        return new TraceIdFilter(properties.traceHeader());
    }
}
