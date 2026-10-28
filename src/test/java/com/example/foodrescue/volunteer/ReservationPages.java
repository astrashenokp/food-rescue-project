package com.example.foodrescue.volunteer;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;

class ReservationPages {

    private final Page page;

    ReservationPages(Page page) {
        this.page = page;
    }

    void open() {
        page.navigate("/reservations");
    }

    void chooseLot(String title) {
        Locator select = page.getByLabel("Лот", new Page.GetByLabelOptions().setExact(true));
        String label = select.getByRole(AriaRole.OPTION, new Locator.GetByRoleOptions().setName(title)).textContent();
        select.selectOption(new SelectOption().setLabel(label));
    }

    void reserve() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Зарезервувати")).click();
    }

    Locator alert() {
        return page.getByRole(AriaRole.ALERT);
    }

    Locator rowWithTitle(String title) {
        return page.getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName(title));
    }
}
