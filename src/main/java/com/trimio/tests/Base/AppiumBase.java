package com.trimio.tests.Base;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

public abstract class AppiumBase extends Assertion{
    protected AndroidDriver androidDriver;
    protected IOSDriver iosDriver;
    protected WebDriver webDriver; // Generic reference for common operations
    protected WebDriverWait wait;
    protected String platform;
    protected final String PROFESSIONAL_USERNAME = "trimiotest+professional_appium1@gmail.com";
    protected final String PASSWORD = "AppiumTesting1$";
    protected final String CLIENT_USERNAME = "trimiotest+client_appium@gmail.com";
    protected final String ADMIN_USERNAME = "trimiotest+admin_appium1@gmail.com";

    public void setUp() {
        setUp("android");
    }

    public void setUp(String platform) {
        this.platform = platform.toLowerCase();
        try {
            if ("android".equals(this.platform)) {
                UiAutomator2Options options = new UiAutomator2Options();
                options.setPlatformName("Android");
                options.setDeviceName("emulator-5554");
                options.setAutomationName("UiAutomator2");
                //options.setAppPackage("com.android.trimio");
                options.setNoReset(true);

                androidDriver = new AndroidDriver(URI.create("http://127.0.0.1:4723").toURL(), options);
                webDriver = androidDriver; // Point generic driver to Android driver

            } else if ("ios".equals(this.platform)) { // No implementation yet
                XCUITestOptions options = new XCUITestOptions();
                options.setPlatformName("iOS");
                options.setDeviceName("iPhone 16 Pro Max");
                options.setAutomationName("XCUITest");
                options.setBundleId("com.trimio.tests");
                options.setNoReset(true);

                iosDriver = new IOSDriver(URI.create("http://127.0.0.1:4723").toURL(), options);
                webDriver = iosDriver; // Point generic driver to iOS driver
            }

            wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));

        } catch (Exception e) {
            System.err.println("Failed to initialize driver: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void tearDown() {
        if (webDriver != null) {
            try {
                logInfo("Beginning teardown . . .");
                webDriver.quit();
            } catch (Exception e) {
                System.err.println("Error during teardown: " + e.getMessage());
            }
        }
    }


    // Platform-specific operations available when needed
    protected void swipeUp() {
        if ("android".equals(platform) && androidDriver != null) {
            // Android-specific swipe using androidDriver
            // androidDriver.findElement(AppiumBy.androidUIAutomator("new UiScrollable..."));
        } else if ("ios".equals(platform) && iosDriver != null) {
            // iOS-specific swipe using iosDriver
        }
    }

    protected void hideKeyboard() {
        if ("android".equals(platform) && androidDriver != null) {
            androidDriver.hideKeyboard();
        } else if ("ios".equals(platform) && iosDriver != null) {
            iosDriver.hideKeyboard();
        }
    }
    protected void waitForElement(By locator) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }
    protected void scrollToBottomOfPage() {
        try {
            if ("android".equals(platform) && androidDriver != null) {
                // Scroll down using UiAutomator2
                androidDriver.findElement(AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().scrollable(true)).scrollToEnd(10)"
                ));
                logInfo("✅ Scrolled to bottom of page");
            }
        } catch (Exception e) {
            logError("Could not scroll to bottom: " + e.getMessage());
            // If scrolling fails, continue anyway
        }
    }

    public abstract void executeTestSuite();

    // DEBUGGING HELP TO FIND ELEMENTS
    protected void debugFlutterElements() {
        try {
            System.out.println("🔍 Debugging Flutter elements...");

            // Find all View elements (Flutter often renders as Views)
            logInfo("Searching for all Flutter View elements");
            var views = androidDriver.findElements(AppiumBy.className("android.view.View"));
            System.out.println("Found " + views.size() + " android.view.View elements");

            // Check for elements with Login-related attributes
            logInfo("Searching for all clickable attributes, and retrieving content descriptions");
            int count = 0;
            for (WebElement view : views) {
                String text = view.getAttribute("text");
                String contentDesc = view.getAttribute("content-desc");
                String clickable = view.getAttribute("clickable");

                if ((text != null && !text.isEmpty()) ||
                        (contentDesc != null && !contentDesc.isEmpty()) ||
                        "true".equals(clickable)) {
                    count++;
                    System.out.println("Flutter Element " + count + ": text='" + text + "', content-desc='" + contentDesc + "', clickable=" + clickable);

                    if (count >= 10) break;
                }
            }

            // Add Android widget debugging
            logInfo("Searching for all Android widgets");
            String[] androidWidgets = {
                    "android.widget.TextView",
                    "android.widget.Button",
                    "android.widget.EditText",
                    "android.widget.ImageView",
                    "android.widget.LinearLayout",
                    "android.widget.RelativeLayout",
                    "android.widget.FrameLayout",
                    "android.widget.ScrollView"
            };

            for (String widgetClass : androidWidgets) {
                try {
                    var widgets = androidDriver.findElements(AppiumBy.className(widgetClass));
                    if (!widgets.isEmpty()) {
                        System.out.println("\nFound " + widgets.size() + " " + widgetClass + " elements");

                        int widgetCount = 0;
                        for (WebElement widget : widgets) {
                            String text = widget.getAttribute("text");
                            String contentDesc = widget.getAttribute("content-desc");
                            String clickable = widget.getAttribute("clickable");
                            String resourceId = widget.getAttribute("resource-id");

                            if ((text != null && !text.isEmpty()) ||
                                    (contentDesc != null && !contentDesc.isEmpty()) ||
                                    (resourceId != null && !resourceId.isEmpty()) ||
                                    "true".equals(clickable)) {
                                widgetCount++;
                                System.out.println("Widget " + widgetCount + " (" + widgetClass + "): text='" + text +
                                        "', content-desc='" + contentDesc + "', resource-id='" + resourceId +
                                        "', clickable=" + clickable);

                                if (widgetCount >= 5) break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("No " + widgetClass + " elements found");
                }
            }

        } catch (Exception e) {
            System.err.println("Flutter debug failed: " + e.getMessage());
        }
    }

    // Advanced Assertion Methods
    protected void assertElementPresent(By locator, String message) {
        try {
            WebElement element = webDriver.findElement(locator);
            assertNotNull(element, message);
        } catch (Exception e) {
            throw new AssertionError(message + " - Element not found: " + locator);
        }
    }

    protected void assertElementVisible(By locator, String message) {
        try {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            assertTrue(element.isDisplayed(), message);
        } catch (Exception e) {
            throw new AssertionError(message + " - Element not visible: " + locator);
        }
    }

    protected void assertElementText(By locator, String expectedText, String message) {
        try {
            WebElement element = webDriver.findElement(locator);
            String actualText = element.getText();
            assertEquals(expectedText, actualText, message);
        } catch (Exception e) {
            throw new AssertionError(message + " - Could not get element text: " + locator);
        }
    }

    protected void assertElementClickable(By locator, String message) {
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            assertTrue(element.isEnabled(), message);
        } catch (Exception e) {
            throw new AssertionError(message + " - Element not clickable: " + locator);
        }
    }
}