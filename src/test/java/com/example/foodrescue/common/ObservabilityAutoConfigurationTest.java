package com.example.foodrescue.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void registersFilterByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(TraceIdFilter.class));
    }

    @Test
    void registersFilterWhenPropertyTrue() {
        runner.withPropertyValues("foodrescue.observability.trace-id-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(TraceIdFilter.class));
    }

    @Test
    void skipsFilterWhenPropertyFalse() {
        runner.withPropertyValues("foodrescue.observability.trace-id-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TraceIdFilter.class));
    }
}
