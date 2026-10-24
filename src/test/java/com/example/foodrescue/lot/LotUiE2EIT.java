package com.example.foodrescue.lot;

import com.example.foodrescue.BrowserTestSupport;
import com.example.foodrescue.E2ETest;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@E2ETest
class LotUiE2EIT extends BrowserTestSupport {

    @Value("${foodrescue.demo.passwords.donor1}")
    String donorPassword;

    @Value("${foodrescue.demo.passwords.volunteer1}")
    String volunteerPassword;

    @Test
    void donor_ShouldCreateLotThroughFormAndSeeItInList() {
        String title = "Лот " + UUID.randomUUID();
        loginAs("donor1", donorPassword);
        LotPages lots = new LotPages(page);

        lots.openCreateForm();
        lots.fillForm(title);
        lots.submit();

        assertThat(lots.successMessage()).isVisible();
        assertThat(lots.rowWithTitle(title)).isVisible();
    }

    @Test
    void volunteer_ShouldNotSeeCreateButton() {
        loginAs("volunteer1", volunteerPassword);
        LotPages lots = new LotPages(page);

        assertThat(lots.heading()).isVisible();
        assertThat(lots.createButton()).hasCount(0);
    }

    @Test
    void login_WithWrongPassword_ShouldShowAlert() {
        loginAs("donor1", "wrong-password");

        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Невірне ім’я користувача або пароль.");
    }
}
