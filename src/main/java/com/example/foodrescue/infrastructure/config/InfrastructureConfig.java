package com.example.foodrescue.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableAsync
public class InfrastructureConfig {

    @Bean
    public Clock appClock() {
        return Clock.systemUTC();
    }
}
