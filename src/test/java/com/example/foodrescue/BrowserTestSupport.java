package com.example.foodrescue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Paths;

public abstract class BrowserTestSupport {

    static Playwright playwright;
    static Browser browser;

    @LocalServerPort
    int port;
    BrowserContext context;
    protected Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setBaseURL("http://localhost:" + port));
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        page = context.newPage();
    }

    @AfterEach
    void closePage() {
        context.tracing().stop(new Tracing.StopOptions()
                .setPath(Paths.get("target/playwright/trace-" + System.nanoTime() + ".zip")));
        context.close();
    }

    protected void loginAs(String username, String password) {
        page.navigate("/login");
        page.getByLabel("Ім’я користувача").fill(username);
        page.getByLabel("Пароль").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Увійти")).click();
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }
}
