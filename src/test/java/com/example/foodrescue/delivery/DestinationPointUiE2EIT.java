package com.example.foodrescue.delivery;

import com.example.foodrescue.BrowserTestSupport;
import com.example.foodrescue.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@E2ETest
class DestinationPointUiE2EIT extends BrowserTestSupport {

    @Value("${foodrescue.demo.passwords.coordinator1}")
    String coordinatorPassword;

    @Test
    void coordinator_ShouldCreateDestinationPointAndSeeItOnList() {
        String name = "Їдальня " + UUID.randomUUID();
        loginAs("coordinator1", coordinatorPassword);
        DestinationPointPages points = new DestinationPointPages(page);

        points.openCreateForm();
        points.fillForm(UUID.randomUUID(), name);
        points.submit();

        assertThat(points.successMessage()).isVisible();
        assertThat(points.rowWithName(name)).isVisible();
    }
}
