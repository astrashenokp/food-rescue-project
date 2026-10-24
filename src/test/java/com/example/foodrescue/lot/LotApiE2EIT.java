package com.example.foodrescue.lot;

import com.example.foodrescue.ApiE2ESupport;
import com.example.foodrescue.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class LotApiE2EIT extends ApiE2ESupport {

    private static final String LOTS = "/api/v1/lots";

    @Value("${foodrescue.demo.passwords.donor1}")
    String donorPassword;

    @Value("${foodrescue.demo.passwords.volunteer1}")
    String volunteerPassword;

    @Autowired
    JdbcClient jdbc;

    @Test
    void donor_ShouldCreatePublishAndCancelLot() {
        String token = login("donor1", donorPassword);
        String title = uniqueTitle();

        String location = client.post().uri(LOTS)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(lotJson(title))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(String.class)
                .getResponseHeaders().getFirst("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        assertThat(statusOf(id)).isEqualTo("DRAFT");

        client.get().uri(LOTS + "/" + id)
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo(title)
                .jsonPath("$.status").isEqualTo("DRAFT");

        client.post().uri(LOTS + "/" + id + "/publication")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk();
        assertThat(statusOf(id)).isEqualTo("PUBLISHED");

        client.post().uri(LOTS + "/" + id + "/cancellation")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk();
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
    }

    @Test
    void volunteer_ShouldNotCreateLot() {
        String token = login("volunteer1", volunteerPassword);
        String title = uniqueTitle();

        client.post().uri(LOTS)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(lotJson(title))
                .exchange()
                .expectStatus().isForbidden();

        assertThat(jdbc.sql("select count(*) from food_lots where title = ?").param(title).query(Long.class).single())
                .isZero();
    }

    @Test
    void request_WithoutToken_ShouldBeUnauthorized() {
        client.get().uri(LOTS)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void login_WithWrongPassword_ShouldBeUnauthorized() {
        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "donor1", "password", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from food_lots where id = ?").param(id).query(String.class).single();
    }

    private String uniqueTitle() {
        return "Лот " + UUID.randomUUID();
    }

    private String lotJson(String title) {
        Instant now = Instant.now();
        return """
                {"donorOrgId":"%s","title":"%s","category":"BAKERY",
                "items":[{"name":"Хліб","quantity":5,"unit":"KG"}],
                "totalWeightKg":5,"storageCondition":"ROOM","pickupAddress":"Київ, вул. Хлібна, 1",
                "pickupFrom":"%s","pickupTo":"%s"}
                """.formatted(UUID.randomUUID(), title, now.plus(2, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));
    }
}
