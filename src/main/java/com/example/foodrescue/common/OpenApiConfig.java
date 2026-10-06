package com.example.foodrescue.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI foodRescueOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Food Rescue API")
                .version("1.0.0"));
    }
}
