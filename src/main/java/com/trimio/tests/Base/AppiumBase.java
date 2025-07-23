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

public abstract class AppiumBase {
    protected AndroidDriver androidDriver;
    protected IOSDriver iosDriver;
    protected WebDriver webDriver; // Generic reference for common operations
    protected WebDriverWait wait;
    protected String platform;

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
                webDriver.quit();
            } catch (Exception e) {
                System.err.println("Error during teardown: " + e.getMessage());
            }
        }
    }

    // Common operations using the generic webDriver
    protected void clickElement(By locator) {
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            element.click();
        } catch (Exception e) {
            throw new RuntimeException("Failed to click element: " + locator, e);
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
    protected void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    protected void logError(String message) {
        System.err.println("[ERROR] " + message);
    }

    public abstract void executeTestSuite();

    // DEBUGGING HELP TO FIND ELEMENTS
    private void debugFlutterElements() {
        try {
            System.out.println("🔍 Debugging Flutter elements...");

            // Find all View elements (Flutter often renders as Views)
            var views = androidDriver.findElements(AppiumBy.className("android.view.View"));
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
}