package org.example.pages;

import com.microsoft.playwright.Page;
import org.example.base.BasePage;
import org.example.config.ConfigReader;

/**
 * EXAMPLE page object for the Trimio login screen.
 *
 * <p>TODO: Replace the placeholder selectors below with the real Trimio locators
 * (inspect the app and prefer stable selectors — {@code getByRole}, {@code data-test} attrs,
 * or stable ids over brittle CSS/XPath).
 */
public class LoginPage extends BasePage {

    // ---- Locators (PLACEHOLDERS — update for the real Trimio app) ----
    private static final String USERNAME_INPUT = "#username";
    private static final String PASSWORD_INPUT = "#password";
    private static final String LOGIN_BUTTON = "button[type='submit']";
    private static final String ERROR_MESSAGE = ".error-message";

    public LoginPage(Page page) {
        super(page);
    }

    /** Opens the login page using {@code baseUrl} (+ optional {@code loginPath}) from config. */
    public LoginPage open() {
        String baseUrl = ConfigReader.get("baseUrl");
        String loginPath = ConfigReader.get("loginPath", "");
        navigate(baseUrl + loginPath);
        return this;
    }

    public HomePage loginAs(String username, String password) {
        LOG.info("Logging in as user: {}", username);
        fill(USERNAME_INPUT, username);
        fill(PASSWORD_INPUT, password);
        click(LOGIN_BUTTON);
        return new HomePage(page);
    }

    public boolean isErrorDisplayed() {
        return isVisible(ERROR_MESSAGE);
    }

    public String getErrorMessage() {
        return getText(ERROR_MESSAGE);
    }
}
