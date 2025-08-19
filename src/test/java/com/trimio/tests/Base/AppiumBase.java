package com.trimio.tests.Base;

import com.trimio.tests.util.TestLogger;
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
import org.testng.annotations.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;

public abstract class AppiumBase {
    protected AndroidDriver androidDriver;
    protected IOSDriver iosDriver;
    protected WebDriver webDriver;
    protected WebDriverWait wait;
    protected String platform;
    protected TestLogger testLogger;

    protected final String PROFESSIONAL_USERNAME = "trimiotest+professional_appium1@gmail.com";
    protected final String PASSWORD = "AppiumTesting1$";
    protected final String CLIENT_USERNAME = "trimiotest+client_appium@gmail.com";
    protected final String ADMIN_USERNAME = "trimiotest+admin_appium1@gmail.com";

    @BeforeClass
    public void setUpClass() {
        String testSuiteName = this.getClass().getSimpleName();
        testLogger = new TestLogger(testSuiteName);
        logInfo("🚀 Starting test class: " + testSuiteName);
    }

    @BeforeMethod
    public void setUpMethod(Method method) {
        // Auto-detect platform and setup
        String detectedPlatform = detectPlatform();
        setUp(detectedPlatform);
        testLogger.setCurrentTestMethod(method.getName());
        logInfo("🔧 Starting test method: " + method.getName());
    }

    @AfterMethod
    public void tearDownMethod(Method method) {
        logInfo("🧹 Finishing test method: " + method.getName());
        tearDown();
        testLogger.assertAll();
    }

    @AfterClass
    public void tearDownClass() {
        logInfo("🏁 Finished test class: " + this.getClass().getSimpleName());
    }

    @AfterSuite
    public void tearDownSuite() {
        TestLogger.closeLogger();
    }

    /**
     * Auto-detect connected devices and return platform type
     */
    private String detectPlatform() {
        try {
            logInfo("🔍 Auto-detecting connected devices...");

            // Check for Android devices
            List<String> androidDevices = getAndroidDevices();
            logInfo("Found " + androidDevices.size() + " Android device(s): " + androidDevices);

            // Check for iOS devices
            List<String> iosDevices = getIOSDevices();
            logInfo("Found " + iosDevices.size() + " iOS device(s): " + iosDevices);

            // Decide which platform to use
            if (!androidDevices.isEmpty() && !iosDevices.isEmpty()) {
                logInfo("⚠️ Both Android and iOS devices found. Defaulting to Android.");
                return "android";
            } else if (!androidDevices.isEmpty()) {
                logInfo("✅ Using Android device: " + androidDevices.get(0));
                return "android";
            } else if (!iosDevices.isEmpty()) {
                logInfo("✅ Using iOS device: " + iosDevices.get(0));
                return "ios";
            } else {
                logInfo("❌ No devices found. Defaulting to Android emulator.");
                return "android";
            }

        } catch (Exception e) {
            logError("Error detecting platform: " + e.getMessage());
            logInfo("Defaulting to Android");
            return "android";
        }
    }

    /**
     * Get list of connected Android devices
     */
    private List<String> getAndroidDevices() {
        List<String> devices = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec("adb devices");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("device") && !line.contains("List of devices")) {
                    String deviceId = line.split("\\s+")[0];
                    devices.add(deviceId);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            logError("Error checking Android devices: " + e.getMessage());
        }
        return devices;
    }

    /**
     * Get list of connected iOS devices/simulators
     */
    private List<String> getIOSDevices() {
        List<String> devices = new ArrayList<>();
        try {
            // Check for iOS simulators
            Process process = Runtime.getRuntime().exec("xcrun simctl list devices --json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            // Simple check for "Booted" simulators
            if (output.toString().contains("Booted")) {
                devices.add("iOS Simulator");
            }

            process.waitFor();
        } catch (Exception e) {
            // xcrun not available (probably not on Mac)
            logInfo("iOS detection not available (not on Mac or Xcode not installed)");
        }
        return devices;
    }

    public void setUp() {
        setUp("android"); // Fallback
    }

    public void setUp(String platform) {
        this.platform = platform.toLowerCase();
        try {
            URI appiumServerURI = URI.create("http://127.0.0.1:4723");

            if ("android".equals(this.platform)) {
                setupAndroidDriver(appiumServerURI);
            } else if ("ios".equals(this.platform)) {
                setupIOSDriver(appiumServerURI);
            } else {
                logError("Unknown platform: " + platform + ". Defaulting to Android.");
                setupAndroidDriver(appiumServerURI);
            }

            wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            TestLogger.setDriver(webDriver);
            logInfo("✅ " + platform.toUpperCase() + " driver initialized successfully");

        } catch (Exception e) {
            logError("Failed to initialize driver: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupAndroidDriver(URI appiumServerURI) throws Exception {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");

        // Try to get actual device name
        List<String> androidDevices = getAndroidDevices();
        if (!androidDevices.isEmpty()) {
            options.setDeviceName(androidDevices.get(0));
            logInfo("Using Android device: " + androidDevices.get(0));
        } else {
            options.setDeviceName("emulator-5554"); // Default
            logInfo("Using default Android emulator");
        }

        options.setAutomationName("UiAutomator2");
        options.setNoReset(true);

        // Add your app package if testing specific app
        // options.setAppPackage("com.your.app.package");
        // options.setAppActivity("com.your.app.MainActivity");

        androidDriver = new AndroidDriver(appiumServerURI.toURL(), options);
        webDriver = androidDriver;
    }

    private void setupIOSDriver(URI appiumServerURI) throws Exception {
        XCUITestOptions options = new XCUITestOptions();
        options.setPlatformName("iOS");
        options.setDeviceName("iPhone 15 Simulator"); // Default simulator
        options.setAutomationName("XCUITest");
        options.setNoReset(true);

        // Add your app bundle ID if testing specific app
        // options.setBundleId("com.your.app.bundle");

        // For simulator
        options.setPlatformVersion("17.0"); // Adjust to your iOS version

        iosDriver = new IOSDriver(appiumServerURI.toURL(), options);
        webDriver = iosDriver;
    }

    public void tearDown() {
        if (webDriver != null) {
            try {
                logInfo("Beginning teardown...");
                webDriver.quit();
                logInfo("✅ Driver closed successfully");
            } catch (Exception e) {
                logError("Error during teardown: " + e.getMessage());
            }
        }
    }

    // Assertion Helpers
    protected void assertElementPresent(By locator, String description) {
        boolean isPresent = isElementPresent(locator);
        testLogger.softAssertTrue(isPresent, description);
    }

    protected void assertElementText(By locator, String expectedText, String description) {
        String actualText = getElementText(locator);
        testLogger.softAssertEquals(actualText, expectedText, description);
    }

    protected void assertElementVisible(By locator, String description) {
        boolean isVisible = false;
        try {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            isVisible = element.isDisplayed();
        } catch (Exception e) {
            // Element not visible
        }
        testLogger.softAssertTrue(isVisible, description);
    }

    protected void assertElementClickable(By locator, String description) {
        boolean isClickable = false;
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            isClickable = element.isEnabled();
        } catch (Exception e) {
            // Element not clickable
        }
        testLogger.softAssertTrue(isClickable, description);
    }

    protected void assertLoginSuccess() {
        assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Schedule') or contains(@content-desc, 'Notification')]"),
                "Should navigate to dashboard after successful login");
        assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Account') or contains(@content-desc, 'account')]"),
                "Account Tab should be visible upon login");
    }

    protected void assertLoginFailure() {
        assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]"),
                "Should remain on login screen after failed login");
        assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'login_error_message') or contains(@content-desc, 'error')]"),
                "Error message should appear for invalid credentials");
    }

    protected void assertEmptyFieldLoginError() {
        assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Welcome') or contains(@content-desc, 'Luxury beauty')]"),
                "Should remain on login screen after failed login");
        assertElementPresent(AppiumBy.xpath("//*[contains(@content-desc, 'Please') or contains(@content-desc, 'enter')]"),
        "Inline error message should appear for empty required field");
    }

    protected boolean isElementPresent(By locator) {
        try {
            webDriver.findElement(locator);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    protected String getElementText(By locator) {
        try {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            return element.getText();
        } catch (Exception e) {
            logError("Failed to get text from element: " + locator);
            return "";
        }
    }

    protected void clickElement(By locator) {
        try {
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
            element.click();
        } catch (Exception e) {
            logError("Failed to click element: " + locator);
            throw new RuntimeException("Could not click element: " + locator, e);
        }
    }

    protected void enterText(By locator, String text) {
        try {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            element.clear();
            element.sendKeys(text);
        } catch (Exception e) {
            logError("Failed to enter text in element: " + locator);
            throw new RuntimeException("Could not enter text: " + locator, e);
        }
    }

    protected void scrollToBottomOfPage() {
        try {
            if ("android".equals(platform) && androidDriver != null) {
                androidDriver.findElement(AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().scrollable(true)).scrollToEnd(10)"
                ));
                logInfo("✅ Scrolled to bottom of page");
            }
        } catch (Exception e) {
            logError("Could not scroll to bottom: " + e.getMessage());
        }
    }

    protected void logInfo(String msg) {
        System.out.println("[INFO] " + msg);
    }

    protected void logError(String msg) {
        System.err.println("[ERROR] " + msg);
    }

    protected void debugFlutterElements() {
        try {
            System.out.println("🔍 Debugging Flutter elements...");

            logInfo("Searching for all Flutter View elements");
            var views = androidDriver.findElements(AppiumBy.className("android.view.View"));
            System.out.println("Found " + views.size() + " android.view.View elements");

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
            logError("Flutter debug failed: " + e.getMessage());
        }
    }
}