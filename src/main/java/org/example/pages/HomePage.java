package org.example.pages;

import com.microsoft.playwright.Page;
import org.example.base.BasePage;

/**
 * EXAMPLE page object for the Trimio landing/dashboard page reached after login.
 *
 * <p>TODO: Replace placeholder selectors with the real Trimio locators.
 */
public class HomePage extends BasePage {

    // ---- Locators (PLACEHOLDERS — update for the real Trimio app) ----
    private static final String USER_MENU = "[data-test='user-menu']";
    private static final String LOGOUT_BUTTON = "[data-test='logout']";

    public HomePage(Page page) {
        super(page);
    }

    /** A simple post-login signal page objects/tests can assert on. */
    public boolean isLoaded() {
        return isVisible(USER_MENU);
    }

    public void logout() {
        click(USER_MENU);
        click(LOGOUT_BUTTON);
    }
}
