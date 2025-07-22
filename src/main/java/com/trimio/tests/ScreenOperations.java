package com.trimio.tests;

// Imports

import org.openqa.selenium.WebElement;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;

public abstract class ScreenOperations {
    protected AndroidDriver driver;

    public ScreenOperations(AndroidDriver driver){
        this.driver = driver;
    }
    public void LogIn(String user, String pass){
        try {
            System.out.println("Locating text fields by android widgets");
            // Find text fields by class
            var textFields = driver.findElements(AppiumBy.className("android.widget.EditText"));

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

                boolean buttonClicked = false;
                if (!buttonClicked) {
                    try {
                        WebElement loginButton = driver.findElement(AppiumBy.xpath("//*[contains(@content-desc, 'Login') or contains(@content-desc, 'login')]"));
                        loginButton.click();
                        System.out.println("✅ Login button pressed by content-desc");
                        buttonClicked = true;
                    } catch (Exception e) {
                        System.out.println("Button not found by content-desc");
                        System.out.println("Error message: " + e.getMessage());
                    }
                }
            }
        }catch (Exception e){
            System.out.println("Exception caught: " + e.getMessage());
        }catch (Throwable e){
            System.out.println("Throwable caught: " + e.getMessage());
        }
    }
    public abstract void setUp() throws Exception;
}
