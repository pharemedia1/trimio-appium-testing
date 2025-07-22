package com.trimio.tests.Login;

import com.trimio.tests.ScreenOperations;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URL;
import java.time.Duration;

public class TrimioLoginTest extends ScreenOperations {

    private WebDriverWait wait;
    private static final String PROFESSIONAL_USERNAME = "trimiotest+professional_appium1@gmail.com";
    private static final String PASSWORD = "AppiumTesting1$";

    public TrimioLoginTest(){
        super(null);
    }
    public static void main(String[] args) {
        TrimioLoginTest test = new TrimioLoginTest();
        try {
            test.setUp();
            test.performLogin(PROFESSIONAL_USERNAME, PASSWORD);
            Thread.sleep(3000); // Wait to see the result
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            test.tearDown();
        }
    }

    @Override
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

    public void performLogin(String user, String pass) {
        LogIn(user, pass);
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