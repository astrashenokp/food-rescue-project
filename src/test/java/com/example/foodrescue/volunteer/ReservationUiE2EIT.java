package com.example.foodrescue.volunteer;

import com.example.foodrescue.BrowserTestSupport;
import com.example.foodrescue.E2ETest;
import com.example.foodrescue.common.DemoData;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@E2ETest
class ReservationUiE2EIT extends BrowserTestSupport {

    @Value("${foodrescue.demo.passwords.volunteer1}")
    String volunteerPassword;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void prepareDemoVolunteer() {
        VolunteerTestFixtures.ensureDemoVolunteer(jdbc);
    }

    @Test
    void volunteer_ShouldReserveLotOnReservationsPage() {
        String title = VolunteerTestFixtures.uniqueTitle();
        UUID lotId = VolunteerTestFixtures.insertLot(jdbc, title, "PUBLISHED", null);
        loginAs("volunteer1", volunteerPassword);
        ReservationPages reservations = new ReservationPages(page);

        reservations.open();
        reservations.chooseLot(title);
        reservations.reserve();

        assertThat(reservations.alert()).containsText("Лот зарезервовано.");
        assertThat(reservations.rowWithTitle(title)).isVisible();
        Assertions.assertThat(jdbc.sql("select reserved_by_volunteer_id from food_lots where id = ?")
                .param(lotId).query(UUID.class).single())
                .isEqualTo(DemoData.DEMO_VOLUNTEER);
    }
}
