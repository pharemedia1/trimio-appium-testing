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
import java.net.URI;
import java.time.Duration;


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
    public void setUpMethod() {
        setUp("android");
    }

    @AfterMethod
    public void tearDownMethod() {
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
                options.setNoReset(true);

                androidDriver = new AndroidDriver(URI.create("http://127.0.0.1:4723").toURL(), options);
                webDriver = androidDriver;

            } else if ("ios".equals(this.platform)) {
                XCUITestOptions options = new XCUITestOptions();
                options.setPlatformName("iOS");
                options.setDeviceName("iPhone 16 Pro Max");
                options.setAutomationName("XCUITest");
                options.setBundleId("com.trimio.tests");
                options.setNoReset(true);

                iosDriver = new IOSDriver(URI.create("http://127.0.0.1:4723").toURL(), options);
                webDriver = iosDriver;
            }

            wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            logInfo("✅ Driver initialized successfully");

        } catch (Exception e) {
            logError("Failed to initialize driver: " + e.getMessage());
            e.printStackTrace();
        }
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
        assertElementPresent(AppiumBy.accessibilityId("Dashboard"),
                "Should navigate to dashboard after successful login");
        assertElementPresent(AppiumBy.xpath("//android.widget.TextView[contains(@text, 'Supreme Barber')]"),
                "User profile should be visible on dashboard");
    }

    protected void assertLoginFailure() {
        assertElementPresent(AppiumBy.accessibilityId("Login Button"),
                "Should remain on login screen after failed login");
        assertElementPresent(AppiumBy.accessibilityId("Error Message"),
                "Error message should appear for invalid credentials");
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