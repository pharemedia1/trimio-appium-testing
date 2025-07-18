package com.trimio.tests;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.net.URL;
import java.time.Duration;
import java.util.Map;

import javax.swing.PopupFactory;

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
        final String PROFESSIONAL_USERNAME = "trimiotest+professional_appium1@gmail.com";
        final String PASSWORD = "AppiumTesting1$";
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
                emailField.clear(); // Clear existing text
                emailField.sendKeys(PROFESSIONAL_USERNAME);
                System.out.println("✅ Email entered successfully");

                // Find Password field
                WebElement passwordField = driver.findElement(
                        AppiumBy.xpath("//*[@text='Password' or contains(@content-desc, 'Password')]"));
                passwordField.click();
                passwordField.clear(); // Clear existing text
                passwordField.sendKeys(PASSWORD);
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
                        textFields.get(0).clear(); // Clear existing text
                        textFields.get(0).sendKeys(PROFESSIONAL_USERNAME);
                        System.out.println("✅ Email entered (method 2)");

                        // Second field - Password
                        textFields.get(1).click();
                        textFields.get(1).clear(); // Clear existing text
                        textFields.get(1).sendKeys(PASSWORD);
                        System.out.println("✅ Password entered (method 2)");

                        // Try multiple ways to find and click the login button (Flutter ElevatedButton)
                        boolean buttonClicked = false;

                        // Try 1: Find Flutter ElevatedButton by text "Login"
                        try {
                            WebElement loginButton = driver.findElement(AppiumBy.xpath("//*[@text='Login']"));
                            loginButton.click();
                            System.out.println("✅ Login button pressed by text (method 2)");
                            buttonClicked = true;
                        } catch (Exception e) {
                            System.out.println("Button not found by text, trying Flutter semantic...");
                        }

                        // Try 2: Find by Flutter semantic label or accessibility
                        if (!buttonClicked) {
                            try {
                                WebElement loginButton = driver.findElement(AppiumBy.xpath("//*[contains(@content-desc, 'Login') or contains(@content-desc, 'login')]"));
                                loginButton.click();
                                System.out.println("✅ Login button pressed by content-desc (method 2)");
                                buttonClicked = true;
                            } catch (Exception e) {
                                System.out.println("Button not found by content-desc, trying class...");
                            }
                        }

                        // Try 3: Find by Android View class (Flutter buttons often render as Views)
                        if (!buttonClicked) {
                            try {
                                var buttons = driver.findElements(AppiumBy.className("android.view.View"));
                                for (WebElement button : buttons) {
                                    String text = button.getAttribute("text");
                                    String contentDesc = button.getAttribute("content-desc");
                                    if ((text != null && text.contains("Login")) ||
                                            (contentDesc != null && contentDesc.contains("Login"))) {
                                        button.click();
                                        System.out.println("✅ Login button pressed by View class (method 2)");
                                        buttonClicked = true;
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("Button not found by View class, trying clickable elements...");
                            }
                        }

                        // Try 4: Find any clickable element with "Login" text
                        if (!buttonClicked) {
                            try {
                                WebElement loginButton = driver.findElement(AppiumBy.xpath("//*[@clickable='true' and (contains(@text, 'Login') or contains(@content-desc, 'Login'))]"));
                                loginButton.click();
                                System.out.println("✅ Login button pressed by clickable element (method 2)");
                                buttonClicked = true;
                            } catch (Exception e) {
                                System.out.println("Button not found by clickable, trying tap coordinates...");
                            }
                        }

                        // Try 5: Use tap coordinates (approximate location of login button)
                        if (!buttonClicked) {
                            try {
                                // Get screen size and tap where login button should be
                                var screenSize = driver.manage().window().getSize();
                                int x = screenSize.width / 2; // Center horizontally
                                int y = (int)(screenSize.height * 0.7); // About 70% down the screen

                                driver.executeScript("mobile: tap", Map.of("x", x, "y", y));
                                System.out.println("✅ Login button pressed by coordinates (method 2)");
                                buttonClicked = true;
                            } catch (Exception e) {
                                System.out.println("Coordinate tap failed");
                            }
                        }

                        if (!buttonClicked) {
                            System.out.println("❌ Could not find or click login button");
                            // Let's debug what elements are available
                            debugFlutterElements();
                        }
                    }

                } catch (Exception e2) {
                    System.out.println("Method 2 failed, trying Method 3...");

                    // Method 3: Try by accessibility ID or resource ID
                    try {
                        // These are common Flutter/Android patterns
                        WebElement emailField = driver.findElement(AppiumBy.xpath("//android.widget.EditText[1]"));
                        emailField.clear(); // Clear existing text
                        emailField.sendKeys("trimiotest+client_qa3@gmail.com");

                        WebElement passwordField = driver.findElement(AppiumBy.xpath("//android.widget.EditText[2]"));
                        passwordField.clear(); // Clear existing text
                        passwordField.sendKeys("Christopher1!");

                        WebElement loginButton = driver.findElement(AppiumBy.xpath("//android.widget.Button"));
                        loginButton.click();

                        System.out.println("✅ Login completed (method 3)");

                    } catch (Exception e3) {
                        System.err.println("❌ All methods failed. Let's debug...");
                        debugFlutterElements();
                    }
                }
            }

            // Wait a moment to see what happens
            Thread.sleep(3000);
            System.out.println("✅ Login test completed");

        } catch (Exception e) {
            System.err.println("❌ Login failed: " + e.getMessage());
            debugFlutterElements();
        }
    }

    private void debugFlutterElements() {
        try {
            System.out.println("🔍 Debugging Flutter elements...");

            // Find all View elements (Flutter often renders as Views)
            var views = driver.findElements(AppiumBy.className("android.view.View"));
            System.out.println("Found " + views.size() + " android.view.View elements");

            // Check for elements with Login-related attributes
            int count = 0;
            for (WebElement view : views) {
                String text = view.getAttribute("text");
                String contentDesc = view.getAttribute("content-desc");
                String clickable = view.getAttribute("clickable");

                if ((text != null && !text.isEmpty()) ||
                        (contentDesc != null && !contentDesc.isEmpty()) ||
                        "true".equals(clickable)) {
                    count++;
                    System.out.println("Element " + count + ": text='" + text + "', content-desc='" + contentDesc + "', clickable=" + clickable);

                    if (count >= 10) break; // Limit output
                }
            }

        } catch (Exception e) {
            System.err.println("Flutter debug failed: " + e.getMessage());
        }
    }

    public void tearDown() {
        if (driver != null) {
            driver.quit();
            System.out.println("✅ Test session ended");
        }
    }
}