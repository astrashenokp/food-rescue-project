package com.example.foodrescue.delivery;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.UUID;

/** Semantic page object: Playwright selectors follow labels, roles and visible text. */
final class DestinationPointPages {

    private final Page page;

    DestinationPointPages(Page page) {
        this.page = page;
    }

    void openCreateForm() {
        page.navigate("/destination-points/new");
    }

    void fillForm(UUID organizationId, String name) {
        page.getByLabel("ID організації (UUID)").fill(organizationId.toString());
        page.getByLabel("Назва").fill(name);
        page.getByLabel("Адреса").fill("Київ, Шкільна 1");
        page.getByLabel("Години роботи (HH:mm-HH:mm)").fill("09:00-18:00");
        page.getByLabel("Категорії продуктів (можна обрати кілька)").selectOption("BAKERY");
    }

    void submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Створити")).click();
    }

    Locator successMessage() {
        return page.getByText("Пункт призначення успішно створено.");
    }

    Locator rowWithName(String name) {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(name));
    }
}
