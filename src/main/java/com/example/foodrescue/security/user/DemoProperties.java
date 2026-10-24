package com.example.foodrescue.security.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "foodrescue.demo")
public record DemoProperties(Map<String, String> passwords) {
}
