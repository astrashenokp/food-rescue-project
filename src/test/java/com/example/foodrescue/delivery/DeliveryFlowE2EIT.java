package com.example.foodrescue.delivery;

import com.example.foodrescue.ApiE2ESupport;
import com.example.foodrescue.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.foodrescue.common.DemoData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class DeliveryFlowE2EIT extends ApiE2ESupport {

    @Autowired JdbcClient jdbc;

    @Value("${foodrescue.demo.passwords.donor1}") String donorPassword;
    @Value("${foodrescue.demo.passwords.volunteer1}") String volunteerPassword;
    @Value("${foodrescue.demo.passwords.coordinator1}") String coordinatorPassword;

    @Test
    void fullFlow_ShouldReachConfirmedThroughRealHttpAndPersistData() throws Exception {
        DeliveryTestFixtures.ensureDemoVolunteer(jdbc);
        String donor = login("donor1", donorPassword);
        String volunteer = login("volunteer1", volunteerPassword);
        String coordinator = login("coordinator1", coordinatorPassword);

        client.get().uri("/api/v1/volunteers/{id}", DemoData.DEMO_VOLUNTEER)
                .headers(headers -> headers.setBearerAuth(volunteer))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(DemoData.DEMO_VOLUNTEER.toString());

        UUID point = UUID.randomUUID();
        String pointName = "E2E пункт " + UUID.randomUUID();
        String pointLocation = client.post().uri("/api/v1/destination-points")
                .headers(headers -> headers.setBearerAuth(coordinator))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"organizationId":"%s","name":"%s","address":"Київ, Центр 1",
                         "workingHours":"09:00-18:00","acceptedCategories":["BAKERY"]}
                        """.formatted(point, pointName))
                .exchange().expectStatus().isCreated()
                .returnResult(String.class).getResponseHeaders().getFirst("Location");
        UUID pointId = idFromLocation(pointLocation);
        assertThat(jdbc.sql("select name from destination_points where id = :id")
                .param("id", pointId).query(String.class).single()).isEqualTo(pointName);

        Instant now = Instant.now();
        String title = "E2E доставка " + UUID.randomUUID();
        String lotLocation = client.post().uri("/api/v1/lots")
                .headers(headers -> headers.setBearerAuth(donor))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"donorOrgId":"%s","title":"%s","category":"BAKERY",
                         "items":[{"name":"Хліб","quantity":5,"unit":"KG"}],
                         "totalWeightKg":5,"storageCondition":"ROOM","pickupAddress":"Київ, Тестова 1",
                         "pickupFrom":"%s","pickupTo":"%s"}
                        """.formatted(UUID.randomUUID(), title, now.plus(2, ChronoUnit.HOURS),
                        now.plus(4, ChronoUnit.HOURS)))
                .exchange().expectStatus().isCreated()
                .returnResult(String.class).getResponseHeaders().getFirst("Location");
        UUID lotId = idFromLocation(lotLocation);

        client.post().uri("/api/v1/lots/{id}/publication", lotId)
                .headers(headers -> headers.setBearerAuth(donor))
                .exchange().expectStatus().isOk();
        assertThat(statusOf(lotId)).isEqualTo("PUBLISHED");

        client.post().uri("/api/v1/lots/{id}/reservation", lotId)
                .headers(headers -> headers.setBearerAuth(volunteer))
                .exchange().expectStatus().isCreated();
        assertThat(statusOf(lotId)).isEqualTo("RESERVED");

        client.post().uri("/api/v1/lots/{id}/pickup", lotId)
                .headers(headers -> headers.setBearerAuth(volunteer))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"actualWeightKg\":5}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.lotStatus").isEqualTo("PICKED_UP");

        client.post().uri("/api/v1/lots/{id}/delivery", lotId)
                .headers(headers -> headers.setBearerAuth(volunteer))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"destinationPointId\":\"" + pointId + "\"}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.lotStatus").isEqualTo("DELIVERED");
        assertThat(statusOf(lotId)).isEqualTo("DELIVERED");

        String code = jdbc.sql("select confirmation_code from deliveries where lot_id = :id")
                .param("id", lotId).query(String.class).single();
        client.post().uri("/api/v1/lots/{id}/confirmation", lotId)
                .headers(headers -> headers.setBearerAuth(volunteer))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"confirmationCode\":\"" + code + "\",\"receivedWeightKg\":5}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.lotStatus").isEqualTo("CONFIRMED");

        assertThat(statusOf(lotId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.sql("select received_weight_kg from deliveries where lot_id = :id")
                .param("id", lotId).query(BigDecimal.class).single()).isEqualByComparingTo("5");
        assertThat(jdbc.sql("select destination_point_id from deliveries where lot_id = :id")
                .param("id", lotId).query(UUID.class).single()).isEqualTo(pointId);

        client.get().uri("/api/v1/lots/{id}/history", lotId)
                .headers(headers -> headers.setBearerAuth(volunteer))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[4].toStatus").isEqualTo("CONFIRMED");
    }

    @Test
    void wrongRole_ShouldBeForbiddenAndNotCreatePoint() {
        String volunteer = login("volunteer1", volunteerPassword);
        String name = "Недозволений " + UUID.randomUUID();
        long before = jdbc.sql("select count(*) from destination_points").query(Long.class).single();
        client.post().uri("/api/v1/destination-points")
                .headers(headers -> headers.setBearerAuth(volunteer))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"organizationId":"%s","name":"%s","address":"Київ",
                         "workingHours":"09:00-18:00","acceptedCategories":["BAKERY"]}
                        """.formatted(UUID.randomUUID(), name))
                .exchange().expectStatus().isForbidden();
        assertThat(jdbc.sql("select count(*) from destination_points").query(Long.class).single())
                .isEqualTo(before);
    }

    private UUID idFromLocation(String location) {
        assertThat(location).isNotBlank();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from food_lots where id = :id")
                .param("id", id).query(String.class).single();
    }
}
