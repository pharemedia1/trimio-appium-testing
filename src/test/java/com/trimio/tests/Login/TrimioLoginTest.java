package com.trimio.tests.Login;

import com.trimio.tests.Base.AppiumBase;
import io.appium.java_client.AppiumBy;
import org.testng.annotations.*;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.util.List;

public class TrimioLoginTest extends AppiumBase {

    @Test(description = "Test successful login for all user types", priority = 1)
    public void testPositiveLoginAllUserTypes() {
        try {
            //debugFlutterElements();
            logInfo("LOGIN POSITIVE TESTING ALL 3 USER TYPES");

            // Test Client Login
            logInfo("Testing CLIENT login");
            performLogin(CLIENT_USERNAME);
            Thread.sleep(5000);
            assertLoginSuccess();
            performLogout();
            Thread.sleep(3000);

            // Test Professional Login
            logInfo("Testing PROFESSIONAL login");
            performLogin(PROFESSIONAL_USERNAME);
            Thread.sleep(5000);
            assertLoginSuccess();
            performLogout();
            Thread.sleep(3000);

            /* Uncomment when ready to test admin
            logInfo("Testing ADMIN login");
            performLogin(ADMIN_USERNAME);
            Thread.sleep(5000);
            assertLoginSuccess();
            performLogout();
            Thread.sleep(3000);
            */

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

            // Test with invalid email
            performInvalidLogin("invalid@email.com", PASSWORD);
            assertLoginFailure();

            // Test with invalid password
            performInvalidLogin(CLIENT_USERNAME, "wrongpassword");
            assertLoginFailure();

            // Test with empty credentials
            performInvalidLogin("", "");
            assertLoginFailure();

        } catch (Exception e) {
            logError("Failed Negative Testing: " + e.getMessage());
            throw new RuntimeException("Negative login test failed", e);
        }
    }

    @Test(description = "Test logout cancellation functionality", priority = 3)
    public void testLogoutCancellation() {
        try {
            logInfo("Testing logout cancellation");

            // Login first
            performLogin(CLIENT_USERNAME);
            Thread.sleep(3000);
            assertLoginSuccess();

            // Try to logout but cancel
            performLogoutCancel();

            // Should still be logged in
            assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Dashboard')]"),
                    "Should still be on dashboard after canceling logout");

        } catch (Exception e) {
            logError("Logout cancellation test failed: " + e.getMessage());
            throw new RuntimeException("Logout cancellation test failed", e);
        }
    }

    public void performLogin(String user) {
        try {
            // Check if on login page
            assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]"),
                    "Should be on login page before attempting login");

            logInfo("Locating text fields by android widgets");
            List<WebElement> textFields = androidDriver.findElements(AppiumBy.className("android.widget.EditText"));

            if (textFields.size() >= 2) {
                // First field - Email
                textFields.get(0).click();
                textFields.get(0).clear();
                textFields.get(0).sendKeys(user);
                logInfo("✅ Email entered: " + user);

                // Second field - Password
                textFields.get(1).click();
                textFields.get(1).clear();
                textFields.get(1).sendKeys(PASSWORD);
                logInfo("✅ Password entered");

                // Click login button
                WebElement loginButton = androidDriver.findElement(
                        AppiumBy.xpath("//*[contains(@content-desc, 'login') or contains(@content-desc, 'LOGIN')]")
                );
                loginButton.click();
                logInfo("✅ Login button pressed");

                // Wait for login to process
                Thread.sleep(3000);

            } else {
                testLogger.softAssertTrue(false, "Could not find email and password text fields");
            }

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Login failed with exception: " + e.getMessage());
            logError("Exception during login: " + e.getMessage());
        }
    }

    public void performInvalidLogin(String user, String password) {
        try {
            logInfo("Attempting invalid login with: " + user);

            List<WebElement> textFields = androidDriver.findElements(AppiumBy.className("android.widget.EditText"));

            if (textFields.size() >= 2) {
                textFields.get(0).click();
                textFields.get(0).clear();
                textFields.get(0).sendKeys(user);

                textFields.get(1).click();
                textFields.get(1).clear();
                textFields.get(1).sendKeys(password);

                WebElement loginButton = androidDriver.findElement(
                        AppiumBy.xpath("//*[contains(@content-desc, 'Login') or contains(@content-desc, 'login')]")
                );
                loginButton.click();

                Thread.sleep(3000); // Wait for error to appear

            }
        } catch (Exception e) {
            logError("Exception during invalid login: " + e.getMessage());
        }
    }

    protected void performLogout() {
        try {
            logInfo("Starting logout process...");

            // Click the Account tab
            WebElement accountTab = androidDriver.findElement(
                    AppiumBy.xpath("//*[contains(@content-desc, 'Account') or contains(@content-desc, 'account')]")
            );
            accountTab.click();
            logInfo("✅ Clicked Account tab");

            Thread.sleep(2000);
            scrollToBottomOfPage();

            // Click LOG OUT button
            WebElement logOutButton = androidDriver.findElement(
                    AppiumBy.xpath("//*[contains(@content-desc, 'LOG OUT') or contains(@content-desc, 'Log out')]")
            );
            logOutButton.click();
            logInfo("✅ Clicked LOG OUT button");

            // Handle logout confirmation
            handleLogoutConfirmation();
            Thread.sleep(2000);

            // Verify we're back on login page
            assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]"),
                    "Should return to login page after successful logout");

            logInfo("✅ User logged out successfully");

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Logout failed: " + e.getMessage());
            logError("Failed to log out user: " + e.getMessage());
        }
    }

    protected void performLogoutCancel() {
        try {
            logInfo("Starting logout cancellation test...");

            // Navigate to logout but cancel
            WebElement accountTab = androidDriver.findElement(
                    AppiumBy.xpath("//*[contains(@content-desc, 'Account') or contains(@content-desc, 'account')]")
            );
            accountTab.click();

            Thread.sleep(2000);
            scrollToBottomOfPage();

            WebElement logOutButton = androidDriver.findElement(
                    AppiumBy.xpath("//*[contains(@content-desc, 'LOG OUT') or contains(@content-desc, 'Log out')]")
            );
            logOutButton.click();

            // Cancel instead of confirming
            cancelLogout();

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Logout cancellation failed: " + e.getMessage());
            logError("Failed logout cancellation: " + e.getMessage());
        }
    }

    private void handleLogoutConfirmation() {
        try {
            Thread.sleep(2000);

            // Verify confirmation dialog appears
            WebElement confirmationTitle = androidDriver.findElement(
                    AppiumBy.xpath("//*[contains(@content-desc, 'Logout Confirmation')]")
            );
            testLogger.softAssertTrue(confirmationTitle.isDisplayed(),
                    "Logout confirmation dialog should appear");

            // Click confirm logout
            WebElement confirmLogoutButton = androidDriver.findElement(
                    AppiumBy.xpath("//android.widget.Button[@content-desc='Logout']")
            );
            confirmLogoutButton.click();
            logInfo("✅ Confirmed logout in dialog");

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Failed to handle logout confirmation: " + e.getMessage());
            logError("Failed to handle logout confirmation: " + e.getMessage());
        }
    }

    protected void cancelLogout() {
        try {
            Thread.sleep(1000);
            WebElement cancelButton = wait.until(ExpectedConditions.elementToBeClickable(
                    AppiumBy.xpath("//android.widget.Button[@content-desc='Cancel']")
            ));

            testLogger.softAssertTrue(cancelButton.isEnabled(), "Cancel button should be enabled");
            cancelButton.click();
            logInfo("✅ Cancelled logout");

        } catch (Exception e) {
            testLogger.softAssertTrue(false, "Failed to cancel logout: " + e.getMessage());
            logError("Failed to cancel logout: " + e.getMessage());
        }
    }

    // Override the assertLoginFailure method for more specific error checking
    protected void assertLoginFailure() {
        super.assertLoginFailure();

        // Additional login failure checks
        assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]"),
                "Should remain on login page after failed login");
    }
}