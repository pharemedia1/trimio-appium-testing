package com.trimio.tests.Login;

import com.trimio.tests.Base.AppiumBase;
import io.appium.java_client.AppiumBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.util.List;

public class TrimioLoginTest extends AppiumBase {



    @Test(description = "Test successful login for all user types", priority = 1)
    public void testPositiveLoginAllUserTypes() {
        try {
            logInfo("LOGIN POSITIVE TESTING ALL 3 USER TYPES");

            // Test Client Login
            logInfo("Testing CLIENT login");
            performLogin(CLIENT_USERNAME);
            assertLoginSuccess();
            performLogout();
            Thread.sleep(2000);

            // Test Professional Login
            logInfo("Testing PROFESSIONAL login");
            performLogin(PROFESSIONAL_USERNAME);
            assertLoginSuccess();
            performLogout();
            Thread.sleep(2000);

        } catch (Exception e) {
            logError("Positive login test failed: " + e.getMessage());
            debugFlutterElements();
            throw new RuntimeException("Positive login test failed", e);
        }
    }

    @Test(description = "Test login with invalid credentials", priority = 2)
    public void testNegativeLogin() {
        try {
            logInfo("Starting Negative Test Suite");

            performInvalidLogin("invalid@email.com", PASSWORD);
            assertLoginFailure();

            performInvalidLogin(CLIENT_USERNAME, "wrongpassword");
            assertLoginFailure();

            performInvalidLogin("", "");
            assertEmptyFieldLoginError();

        } catch (Exception e) {
            logError("Failed Negative Testing: " + e.getMessage());
            throw new RuntimeException("Negative login test failed", e);
        }
    }

    @Test(description = "Test logout cancellation functionality", priority = 3)
    public void testLogoutCancellation() {
        try {
            logInfo("Testing logout cancellation");

            performLogin(CLIENT_USERNAME);
            assertLoginSuccess();
            performLogoutCancel();

            Thread.sleep(1000);
            assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Profile') or contains(@content-desc, 'Member Since')]"),
                    "Should still be on Account Page after canceling logout");

        } catch (Exception e) {
            logError("Logout cancellation test failed: " + e.getMessage());
            throw new RuntimeException("Logout cancellation test failed", e);
        }
    }

    public void performLogin(String user) {
        try {
            assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]"),
                    "Should be on login page before attempting login");

            logInfo("Locating text fields by platform-specific widgets");

            // Use platform-specific text field detection
            List<WebElement> textFields = getTextFields();

            if (textFields.size() >= 2) {
                // First field - Email
                textFields.get(0).click();
                textFields.get(0).clear();
                textFields.get(0).sendKeys(user);
                logInfo("Email entered: " + user);

                // Second field - Password
                textFields.get(1).click();
                textFields.get(1).clear();
                textFields.get(1).sendKeys(PASSWORD);
                logInfo("Password entered");

                // Hide Keyboard
                hideKeyboard();
                Thread.sleep(1000); // Allow time for the keyboard to go away

                // Click login button using generic approach
                WebElement loginButton = findLoginButton();
                loginButton.click();
                logInfo("Login button pressed");

                Thread.sleep(3000);

            } else {
                testLogger.softAssertTrue(false, "Could not find email and password text fields");
            }

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Login failed with exception: " + e.getMessage());
            logError("Exception during login: " + e.getMessage());
        }
    }

    // Platform-specific helper methods
    private List<WebElement> getTextFields() {
        if ("android".equals(platform)) {
            return webDriver.findElements(AppiumBy.className("android.widget.EditText"));
        } else if ("ios".equals(platform)) {
            return webDriver.findElements(AppiumBy.className("XCUIElementTypeTextField"));
        } else {
            // Fallback - try both
            try {
                return webDriver.findElements(AppiumBy.className("android.widget.EditText"));
            } catch (Exception e) {
                return webDriver.findElements(AppiumBy.className("XCUIElementTypeTextField"));
            }
        }
    }

    private WebElement findLoginButton() {
        // Try content-desc first (works for both platforms)
        try {
            logInfo("Locating Login button by xPath content-desc");
            return webDriver.findElement(
                    AppiumBy.xpath("//*[contains(@content-desc, 'login') or contains(@content-desc, 'LOGIN')]")
            );
        } catch (Exception e) {
            logError("XPath failed. Could not locate.");
            // Platform-specific fallback
            if ("android".equals(platform)) {
                logInfo("Locating login button by android widgets");
                return webDriver.findElement(AppiumBy.className("android.widget.Button"));
            } else {
                logInfo("Locating button by xpath text element and clickable attr.");
                return webDriver.findElement(AppiumBy.xpath("//*[@text='Login' and @clickable='true']"));
            }
        }
    }

    private WebElement findAccountTab() {
        return webDriver.findElement(
                AppiumBy.xpath("//*[contains(@content-desc, 'Account') or contains(@content-desc, 'account')]")
        );
    }

    private WebElement findLogoutButton() {
        return webDriver.findElement(
                AppiumBy.xpath("//*[contains(@content-desc, 'LOG OUT') or contains(@content-desc, 'Log out')]")
        );
    }

    private WebElement findLogoutConfirmButton() {
        if ("android".equals(platform)) {
            return webDriver.findElement(
                    AppiumBy.xpath("//android.widget.Button[@content-desc='Logout']")
            );
        } else {
            return webDriver.findElement(
                    AppiumBy.xpath("//XCUIElementTypeButton[@name='Logout']")
            );
        }
    }

    private WebElement findCancelButton() {
        if ("android".equals(platform)) {
            return webDriver.findElement(
                    AppiumBy.xpath("//android.widget.Button[@content-desc='Cancel']")
            );
        } else {
            return webDriver.findElement(
                    AppiumBy.xpath("//XCUIElementTypeButton[@name='Cancel']")
            );
        }
    }

    public void performInvalidLogin(String user, String password) {
        try {
            logInfo("Attempting invalid login with: " + user);

            List<WebElement> textFields = getTextFields();

            if (textFields.size() >= 2) {
                textFields.get(0).click();
                textFields.get(0).clear();
                textFields.get(0).sendKeys(user);

                textFields.get(1).click();
                textFields.get(1).clear();
                textFields.get(1).sendKeys(password);

                hideKeyboard();
                WebElement loginButton = findLoginButton();
                loginButton.click();

                Thread.sleep(3000);
            }
        } catch (Exception e) {
            logError("Exception during invalid login: " + e.getMessage());
        }
    }

    protected void performLogout() {
        try {
            logInfo("Starting logout process...");

            WebElement accountTab = findAccountTab();
            accountTab.click();
            logInfo("Clicked Account tab");

            Thread.sleep(2000);
            scrollToBottomOfPage();

            WebElement logOutButton = findLogoutButton();
            logOutButton.click();
            logInfo("Clicked LOG OUT button");

            handleLogoutConfirmation();
            Thread.sleep(2000);

            assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]"),
                    "Should return to login page after successful logout");

            logInfo("User logged out successfully");

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Logout failed: " + e.getMessage());
            logError("Failed to log out user: " + e.getMessage());
        }
    }

    protected void performLogoutCancel() {
        try {
            logInfo("Starting logout cancellation test...");

            WebElement accountTab = findAccountTab();
            accountTab.click();

            Thread.sleep(2000);
            scrollToBottomOfPage();

            WebElement logOutButton = findLogoutButton();
            logOutButton.click();

            cancelLogout();

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Logout cancellation failed: " + e.getMessage());
            logError("Failed logout cancellation: " + e.getMessage());
        }
    }

    private void handleLogoutConfirmation() {
        try {
            Thread.sleep(2000);

            WebElement confirmationTitle = webDriver.findElement(
                    AppiumBy.xpath("//*[contains(@content-desc, 'Logout Confirmation')]")
            );
            testLogger.softAssertTrue(confirmationTitle.isDisplayed(),
                    "Logout confirmation dialog should appear");

            WebElement confirmLogoutButton = findLogoutConfirmButton();
            confirmLogoutButton.click();
            logInfo("Confirmed logout in dialog");

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Failed to handle logout confirmation: " + e.getMessage());
            logError("Failed to handle logout confirmation: " + e.getMessage());
        }
    }

    protected void cancelLogout() {
        try {
            Thread.sleep(1000);
            WebElement cancelButton = wait.until(ExpectedConditions.elementToBeClickable(findCancelButton()));

            testLogger.softAssertTrue(cancelButton.isEnabled(), "Cancel button should be enabled");
            cancelButton.click();
            logInfo("Cancelled logout");

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Failed to cancel logout: " + e.getMessage());
            logError("Failed to cancel logout: " + e.getMessage());
        }
    }
}