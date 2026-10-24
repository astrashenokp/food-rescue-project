package com.example.foodrescue.lot;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

class LotPages {

    private final Page page;

    LotPages(Page page) {
        this.page = page;
    }

    void openCreateForm() {
        page.navigate("/lots/new");
    }

    void fillForm(String title) {
        LocalDateTime from = LocalDateTime.now().plusHours(2).truncatedTo(ChronoUnit.MINUTES);
        field("Назва").fill(title);
        field("Категорія").selectOption("BAKERY");
        field("Продукт").fill("Хліб");
        field("Кількість").fill("5");
        field("Одиниця").selectOption("KG");
        field("Загальна вага, кг").fill("5");
        field("Зберігання").selectOption("ROOM");
        field("Адреса самовивозу").fill("Київ, вул. Хлібна, 1");
        field("Самовивіз з").fill(from.toString());
        field("Самовивіз до").fill(from.plusHours(2).toString());
    }

    void submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Створити")).click();
    }

    Locator rowWithTitle(String title) {
        return page.getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName(title));
    }

    Locator successMessage() {
        return page.getByText("Лот успішно створено.");
    }

    Locator heading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Лоти").setExact(true));
    }

    Locator createButton() {
        return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Додати лот"));
    }

    private Locator field(String label) {
        return page.getByLabel(label, new Page.GetByLabelOptions().setExact(true));
    }
}
