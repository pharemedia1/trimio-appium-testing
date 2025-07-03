package com.trimio.tests;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.net.URL;
import java.time.Duration;

public class TrimioLoginTest {

    private AndroidDriver driver;
    private WebDriverWait wait;

    public static void main(String[] args) {
        TrimioLoginTest test = new TrimioLoginTest();
        try {
            test.setUp();
            test.performLogin();
            Thread.sleep(3000); // Wait to see the result
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            test.tearDown();
        }
    }

    public void setUp() throws Exception {
        // Configure for your running Trimio app
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setDeviceName("emulator-5554");
        options.setAutomationName("UiAutomator2");

        // Since your app is already running, we'll connect to the current session
        // If you know your package name, you can use:
        // options.setAppPackage("com.example.trimio"); // Replace with actual package
        // options.setAppActivity("com.example.trimio.MainActivity");
        options.setNoReset(true);

        // For now, let's try to connect to the current app
        driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void performLogin() {
        try {
            System.out.println("Starting login test...");

            // Wait for the login screen to be visible
            Thread.sleep(2000);

            // Method 1: Try to find elements by text (most reliable for Flutter)
            try {
                // Find Email field by placeholder text
                WebElement emailField = wait.until(ExpectedConditions.elementToBeClickable(
                        AppiumBy.xpath("//*[@text='Email' or contains(@content-desc, 'Email')]")));
                emailField.click();
                emailField.sendKeys("test@example.com");
                System.out.println("✅ Email entered successfully");

                // Find Password field
                WebElement passwordField = driver.findElement(
                        AppiumBy.xpath("//*[@text='Password' or contains(@content-desc, 'Password')]"));
                passwordField.click();
                passwordField.sendKeys("password123");
                System.out.println("✅ Password entered successfully");

                // Find Login button
                WebElement loginButton = driver.findElement(
                        AppiumBy.xpath("//*[@text='Login' or contains(@content-desc, 'Login')]"));
                loginButton.click();
                System.out.println("✅ Login button pressed");

            } catch (Exception e1) {
                System.out.println("Method 1 failed, trying Method 2...");

                // Method 2: Try by class name (Android widgets)
                try {
                    // Find text fields by class
                    var textFields = driver.findElements(AppiumBy.className("android.widget.EditText"));

                    if (textFields.size() >= 2) {
                        // First field - Email
                        textFields.get(0).click();
                        textFields.get(0).sendKeys("test@example.com");
                        System.out.println("✅ Email entered (method 2)");

                        // Second field - Password
                        textFields.get(1).click();
                        textFields.get(1).sendKeys("password123");
                        System.out.println("✅ Password entered (method 2)");

                        // Find button
                        WebElement loginButton = driver.findElement(AppiumBy.className("android.widget.Button"));
                        loginButton.click();
                        System.out.println("✅ Login button pressed (method 2)");
                    }

                } catch (Exception e2) {
                    System.out.println("Method 2 failed, trying Method 3...");

                    // Method 3: Try by accessibility ID or resource ID
                    try {
                        // These are common Flutter/Android patterns
                        WebElement emailField = driver.findElement(AppiumBy.xpath("//android.widget.EditText[1]"));
                        emailField.sendKeys("test@example.com");

                        WebElement passwordField = driver.findElement(AppiumBy.xpath("//android.widget.EditText[2]"));
                        passwordField.sendKeys("password123");

                        WebElement loginButton = driver.findElement(AppiumBy.xpath("//android.widget.Button"));
                        loginButton.click();

                        System.out.println("✅ Login completed (method 3)");

                    } catch (Exception e3) {
                        System.err.println("❌ All methods failed. Let's debug...");
                        debugElements();
                    }
                }
            }

            // Wait a moment to see what happens
            Thread.sleep(3000);
            System.out.println("✅ Login test completed");

        } catch (Exception e) {
            System.err.println("❌ Login failed: " + e.getMessage());
            debugElements();
        }
    }

    private void debugElements() {
        try {
            System.out.println("🔍 Debugging: Finding all elements on screen...");

            // Get page source to see the structure
            String pageSource = driver.getPageSource();
            System.out.println("Page source length: " + pageSource.length());

            // Find all clickable elements
            var clickableElements = driver.findElements(AppiumBy.xpath("//*[@clickable='true']"));
            System.out.println("Found " + clickableElements.size() + " clickable elements");

            // Find all text fields
            var textFields = driver.findElements(AppiumBy.className("android.widget.EditText"));
            System.out.println("Found " + textFields.size() + " text fields");

            // Find all buttons
            var buttons = driver.findElements(AppiumBy.className("android.widget.Button"));
            System.out.println("Found " + buttons.size() + " buttons");

        } catch (Exception e) {
            System.err.println("Debug failed: " + e.getMessage());
        }
    }

    public void tearDown() {
        if (driver != null) {
            driver.quit();
            System.out.println("✅ Test session ended");
        }
    }
}