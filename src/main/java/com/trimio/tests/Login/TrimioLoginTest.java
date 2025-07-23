package com.trimio.tests.Login;

import com.trimio.tests.Base.AppiumBase;
import com.trimio.tests.ScreenOperations;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URL;
import java.time.Duration;
import java.util.List;

public class TrimioLoginTest extends AppiumBase {

    private WebDriverWait wait;
    private static final String PROFESSIONAL_USERNAME = "trimiotest+professional_appium1@gmail.com";
    private static final String PASSWORD = "AppiumTesting1$";


    public static void main(String[] args) {
        TrimioLoginTest test = new TrimioLoginTest();
        try {
            test.setUp();
            test.executeTestSuite();
            Thread.sleep(3000); // Wait to see the result
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            test.tearDown();
        }
    }
    @Override
    public void executeTestSuite(){
        performProfessionalLogin(PROFESSIONAL_USERNAME, PASSWORD);
//        performClientLogin(CLIENT_USERNAME, CLIENT_PASSWORD);
//        performAdminLogin(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    public void performProfessionalLogin(String user, String pass) {
        try {
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
                textFields.get(1).sendKeys(pass);
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

//    private void debugFlutterElements() {
//        try {
//            System.out.println("🔍 Debugging Flutter elements...");
//
//            // Find all View elements (Flutter often renders as Views)
//            var views = androidDriver.findElements(AppiumBy.className("android.view.View"));
//            System.out.println("Found " + views.size() + " android.view.View elements");
//
//            // Check for elements with Login-related attributes
//            int count = 0;
//            for (WebElement view : views) {
//                String text = view.getAttribute("text");
//                String contentDesc = view.getAttribute("content-desc");
//                String clickable = view.getAttribute("clickable");
//
//                if ((text != null && !text.isEmpty()) ||
//                        (contentDesc != null && !contentDesc.isEmpty()) ||
//                        "true".equals(clickable)) {
//                    count++;
//                    System.out.println("Element " + count + ": text='" + text + "', content-desc='" + contentDesc + "', clickable=" + clickable);
//
//                    if (count >= 10) break; // Limit output
//                }
//            }
//
//        } catch (Exception e) {
//            System.err.println("Flutter debug failed: " + e.getMessage());
//        }
//    }
}