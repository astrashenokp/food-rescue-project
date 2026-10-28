package com.example.foodrescue.volunteer;

import com.example.foodrescue.ApiE2ESupport;
import com.example.foodrescue.E2ETest;
import com.example.foodrescue.common.DemoData;
import org.junit.jupiter.api.BeforeEach;
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
class ReservationApiE2EIT extends ApiE2ESupport {

    @Value("${foodrescue.demo.passwords.donor1}")
    String donorPassword;

    @Value("${foodrescue.demo.passwords.volunteer1}")
    String volunteerPassword;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void prepareDemoVolunteer() {
        VolunteerTestFixtures.ensureDemoVolunteer(jdbc);
    }

    @Test
    void volunteer_ShouldReserveAndCancelPublishedLot() {
        UUID id = publishedLotOfDonor();
        String volunteer = login("volunteer1", volunteerPassword);

        client.post().uri(reservationUri(id))
                .headers(h -> h.setBearerAuth(volunteer))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", reservationUri(id));
        assertThat(lotRow(id)).containsEntry("status", "RESERVED")
                .containsEntry("reserved_by_volunteer_id", DemoData.DEMO_VOLUNTEER);

        client.post().uri(reservationUri(id))
                .headers(h -> h.setBearerAuth(volunteer))
                .exchange()
                .expectStatus().isEqualTo(409);

        client.delete().uri(reservationUri(id))
                .headers(h -> h.setBearerAuth(volunteer))
                .exchange()
                .expectStatus().isNoContent();
        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED")
                .containsEntry("reserved_by_volunteer_id", null);
    }

    @Test
    void donor_ShouldNotReserveLot() {
        UUID id = publishedLotOfDonor();
        String donor = login("donor1", donorPassword);

        client.post().uri(reservationUri(id))
                .headers(h -> h.setBearerAuth(donor))
                .exchange()
                .expectStatus().isForbidden();

        assertThat(lotRow(id)).containsEntry("status", "PUBLISHED")
                .containsEntry("reserved_by_volunteer_id", null);
    }

    private UUID publishedLotOfDonor() {
        String donor = login("donor1", donorPassword);
        Instant now = Instant.now();
        String location = client.post().uri("/api/v1/lots")
                .headers(h -> h.setBearerAuth(donor))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"donorOrgId":"%s","title":"%s","category":"BAKERY",
                        "items":[{"name":"Хліб","quantity":5,"unit":"KG"}],
                        "totalWeightKg":5,"storageCondition":"ROOM","pickupAddress":"Київ, вул. Хлібна, 1",
                        "pickupFrom":"%s","pickupTo":"%s"}
                        """.formatted(DemoData.DONOR, VolunteerTestFixtures.uniqueTitle(),
                        now.plus(2, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS)))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(String.class)
                .getResponseHeaders().getFirst("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        client.post().uri("/api/v1/lots/" + id + "/publication")
                .headers(h -> h.setBearerAuth(donor))
                .exchange()
                .expectStatus().isOk();
        return id;
    }

    private Map<String, Object> lotRow(UUID id) {
        return jdbc.sql("select status, reserved_by_volunteer_id from food_lots where id = ?")
                .param(id).query().singleRow();
    }

    private String reservationUri(UUID id) {
        return "/api/v1/lots/" + id + "/reservation";
    }
}
