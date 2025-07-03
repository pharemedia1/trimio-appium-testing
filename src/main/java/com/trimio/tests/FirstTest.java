package com.trimio.tests;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

import java.net.URL;

public class FirstTest {
    public static void main(String[] args) throws Exception {
        // Minimal setup - just connect to device
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setDeviceName("emulator-5554");
        UiAutomator2Options uiAutomator2 = options.setAutomationName("UiAutomator2");

        // Launch your trimio app (replace with your actual package/activity names)
        options.setAppPackage("com.trimio.trimio");  // Your app's package name
        options.setAppActivity("com.trimio.trimio.MainActivity");  // Your main activity
        options.setNoReset(true); //

        AndroidDriver driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), options);

        // Simple validation
        System.out.println("✅ Appium Session Started: " + driver.getSessionId());
        System.out.println("📱 Device Name: " + driver.getCapabilities().getCapability("deviceName"));
        System.out.println("🤖 Platform Version: " + driver.getCapabilities().getCapability("platformVersion"));
        System.out.println("YAAAAY IT WORKSS!!!");

        // Clean up
        driver.quit();
        System.out.println("✅ Session ended successfully!");
    }
}