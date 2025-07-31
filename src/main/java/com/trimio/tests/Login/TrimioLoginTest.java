package com.trimio.tests.Login;

import com.trimio.tests.Base.AppiumBase;

import io.appium.java_client.AppiumBy;
import io.netty.channel.PreferHeapByteBufAllocator;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TrimioLoginTest extends AppiumBase {

    boolean onLoginPage;

    public static void main(String[] args) {
        TrimioLoginTest test = new TrimioLoginTest();
        try {

            test.setUp();
            test.executeTestSuite();
            Thread.sleep(3000); // Wait to see the result
            test.returnFails("LoginTestSuite");

//            test.setUp();
//            test.debugFlutterElements();

        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            test.tearDown();
        }
    }
    @Override
    public void executeTestSuite() {
        try {
            logInfo("LOGIN POSITIVE TESTING ALL 3 USER TYPES");
            performLogin(CLIENT_USERNAME);
            Thread.sleep(5000);
            performLogout();
            Thread.sleep(5000);
            performLogin(PROFESSIONAL_USERNAME);
            Thread.sleep(5000);
            performLogout();
            Thread.sleep(5000);
            /*
            performLogin(ADMIN_USERNAME);
            performLogout();
            Thread.sleep(5000);
             */
        } catch (Exception e) {
            logError(e.getMessage());
        }finally{
            logInfo("Exited Positive Test Suite");
        }

        try{
            logInfo("Starting Negative Test Suite");
        } catch (Exception e) {
            logError("Failed Negative Testing: " + e.getMessage());
        }finally {
            logInfo("Reached End of Negative Test Cases");
        }
    }


    public void performLogin(String user) {
        try {
            onLoginPage = false;

            WebElement title = webDriver.findElement((AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]")));
            if(!(title == null))
                onLoginPage = true;
            assertTrue(onLoginPage, "On Login Page");
            System.out.println("Locating text fields by android widgets");
            // Find text fields by class
            List<WebElement> textFields = androidDriver.findElements(AppiumBy.className("android.widget.EditText"));




            if (textFields.size() >= 2) {
                // First field - Email
                textFields.get(0).click();
                textFields.get(0).clear(); // Clear existing text
                textFields.get(0).sendKeys(user);
                System.out.println("✅ Email entered (method 2)");

                // Second field - Password
                textFields.get(1).click();
                textFields.get(1).clear(); // Clear existing text
                textFields.get(1).sendKeys(PASSWORD);
                System.out.println("✅ Password entered (method 2)");

                // Check for Button
                try {
                    WebElement loginButton = androidDriver.findElement(AppiumBy.xpath("//*[contains(@content-desc, 'Login') or contains(@content-desc, 'login')]"));
                    loginButton.click();
                    System.out.println("✅ Login button pressed by content-desc");
                } catch (Exception e) {
                    System.out.println("Button not found by content-desc");
                    System.out.println("Error message: " + e.getMessage());
                }
            }
        }catch (Exception e){
            System.out.println("Exception caught: " + e.getMessage());
        }catch (Throwable e){
            System.out.println("Throwable caught: " + e.getMessage());
        }
    }

    /**
     * Logs out the current user by navigating to Account tab and clicking Log Out
     * Handles the logout confirmation dialog
     */
    protected void performLogout() {
        onLoginPage = false;
        try {
            logInfo("Starting logout process...");

            // Step 1: Click the Account tab in bottom navigation
            WebElement accountTab = androidDriver.findElement(AppiumBy.xpath("//*[contains(@content-desc, 'Account') or contains(@content-desc, 'account')]"));
            accountTab.click();
            logInfo("✅ Clicked Account tab");

            // Wait for Account page to load
            Thread.sleep(2000);

            // Step 2: Scroll down to find the LOG OUT button
            scrollToBottomOfPage();

            // Step 3: Click LOG OUT button
            boolean isButtonClicked = false;
            WebElement logOutButton = null;
                try {
                    logInfo("Method 1: Pressing Logout button by Content-desc");
                    logOutButton = androidDriver.findElement(AppiumBy.xpath("//*[contains(@content-desc, 'LOG OUT') or contains(@content-desc, 'Log out')]"));
                    isButtonClicked = true;
                } catch (Exception e) {
                    System.out.println("Method 1 Failed");
                    logError(e.getMessage());
                }

            logOutButton.click();
            logInfo("✅ Clicked LOG OUT button");

            // Step 4: Handle logout confirmation dialog
            handleLogoutConfirmation();

            // Step 5: Wait for logout to complete
            Thread.sleep(2000);

            WebElement title = webDriver.findElement((AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]")));
            if(!(title==null)){
                onLoginPage = true;
            }
            assertTrue(onLoginPage, "User logout");
            logInfo("✅ User logged out successfully");

        } catch (Exception e) {
            logError("Failed to log out user: " + e.getMessage());
            throw new RuntimeException("Logout operation failed", e);
        }
    }

    /**
     * Handles the logout confirmation dialog
     */
    private void handleLogoutConfirmation() {
        try {
            // Wait for confirmation dialog to appear
            Thread.sleep(2000);
            WebElement confirmationTitle = androidDriver.findElement(AppiumBy.xpath("//*[contains(@content-desc, 'Logout Confirmation')]"));
            logInfo("✅ Logout confirmation dialog appeared");


            // Click the red "Logout" button to confirm
            WebElement confirmLogoutButton = androidDriver.findElement(
                    AppiumBy.xpath("//android.widget.Button[@content-desc='Logout']")
            );
            confirmLogoutButton.click();
            logInfo("✅ Confirmed logout in dialog");

        } catch (Exception e) {
            logError("Failed to handle logout confirmation: " + e.getMessage());
            throw new RuntimeException("Could not confirm logout", e);
        }
    }

    /**
     * Alternative method if you want to cancel the logout
     */
    protected void cancelLogout() {
        try {
            // Click Cancel button instead
            WebElement cancelButton = wait.until(ExpectedConditions.elementToBeClickable(
                    AppiumBy.xpath("//android.widget.Button[@content-desc='Cancel']")
            ));
            cancelButton.click();
            logInfo("✅ Cancelled logout");

        } catch (Exception e) {
            logError("Failed to cancel logout: " + e.getMessage());
            throw new RuntimeException("Could not cancel logout", e);
        }
    }


}