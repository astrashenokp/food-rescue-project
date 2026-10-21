package com.example.foodrescue.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    private static final Logger log = LoggerFactory.getLogger(PasswordEncoderConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 16384, 3);
    }

    @Bean
    public ApplicationRunner passwordHashLogger(PasswordEncoder passwordEncoder) {
        return args -> log.info("Приклад хеша Argon2id: {}", passwordEncoder.encode("example"));
    }
}
