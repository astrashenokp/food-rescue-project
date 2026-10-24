package com.example.foodrescue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;

public abstract class ApiE2ESupport {

    @Autowired
    protected RestTestClient client;

    protected String login(String username, String password) {
        return client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .get("accessToken").toString();
    }
}
