package com.trimio.tests.Base;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
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
                options.setAppPackage("com.trimio.tests");
                options.setNoReset(true);

                androidDriver = new AndroidDriver(new URL("http://127.0.0.1:4723"), options);
                webDriver = androidDriver; // Point generic driver to Android driver

            } else if ("ios".equals(this.platform)) {
                XCUITestOptions options = new XCUITestOptions();
                options.setPlatformName("iOS");
                options.setDeviceName("iPhone 15 Simulator");
                options.setAutomationName("XCUITest");
                options.setBundleId("com.trimio.tests");
                options.setNoReset(true);

                iosDriver = new IOSDriver(new URL("http://127.0.0.1:4723"), options);
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

    public abstract void executeTestSuite();
}