package org.example.tests;

import org.example.base.BaseTest;
import org.example.pages.HomePage;
import org.example.pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * EXAMPLE data-driven test demonstrating the full hybrid stack: POM ({@link LoginPage}/
 * {@link HomePage}), data-driven input (the {@code loginData} provider), reusable actions
 * ({@code BasePage}), and listener-driven reporting/screenshots.
 *
 * <p>TODO: With placeholder selectors these will fail at the Trimio interaction step; that is
 * expected and proves the wiring. Point {@code baseUrl} at the real Trimio app and update
 * {@link LoginPage} selectors for green runs.
 */
public class LoginTest extends BaseTest {

    @Test(dataProvider = "loginData",
            dataProviderClass = org.example.dataproviders.TestDataProvider.class)
    public void loginScenario(Map<String, Object> data) {
        String username = String.valueOf(data.get("username"));
        String password = String.valueOf(data.get("password"));
        boolean expectSuccess = Boolean.parseBoolean(String.valueOf(data.get("expectSuccess")));

        LoginPage loginPage = new LoginPage(page).open();
        HomePage homePage = loginPage.loginAs(username, password);

        if (expectSuccess) {
            Assert.assertTrue(homePage.isLoaded(),
                    "Expected to land on the home page after a valid login");
        } else {
            Assert.assertTrue(loginPage.isErrorDisplayed(),
                    "Expected an error message for invalid credentials");
        }
    }
}
