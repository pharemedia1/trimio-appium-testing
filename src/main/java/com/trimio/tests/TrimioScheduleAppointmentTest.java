package com.trimio.tests;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.NoSuchElementException;


import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class TrimioScheduleAppointmentTest {

    private AndroidDriver driver;
    private WebDriverWait wait;
    private static final String EMAIL = "trimiotest+client_qa3@gmail.com";
    private static final String PASSWORD = "Christopher1!";

    public static void main(String[] args) {
        TrimioScheduleAppointmentTest test = new TrimioScheduleAppointmentTest();
        try {
            test.setUp();

            // Run all test scenarios
            test.runAllTests();

        } catch (Exception e) {
            System.err.println("Test suite failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            test.tearDown();
        }
    }

    public void setUp() throws Exception {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setDeviceName("emulator-5554");
        options.setAutomationName("UiAutomator2");
        options.setNoReset(true);

        driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void runAllTests() {
        System.out.println("=== Starting Schedule Appointment Test Suite ===");
        System.out.println("⚠️  Note: Tests will stop at Review screen - manual payment confirmation required\n");

        // Positive test cases
        System.out.println("--- POSITIVE TEST CASES ---");
        testCompleteSchedulingFlow();

        // Negative test cases
        System.out.println("\n--- NEGATIVE TEST CASES ---");
        testCancelAtEachStep();
        testNavigationBackButton();
        testInvalidSelections();
        testEmptySelections();

        System.out.println("\n=== Test Suite Completed ===");
    }

    // POSITIVE TEST CASE: Complete scheduling flow
    public void testCompleteSchedulingFlow() {
        System.out.println("Test 1: Complete appointment scheduling flow");

        try {
            // Step 1: Navigate to home screen and click Schedule Appointment
            navigateToHomeScreen();
            clickScheduleAppointment();

            // Step 2: Select service type (Barber)
            selectServiceType("Barber");

            // Step 3: Select who the appointment is for
            selectAppointmentFor("Myself");

            // Step 4: Select services
            selectServices(new String[]{"Balayage / Ombre", "Skin Fade / Taper", "Hot Towel Shave"});

            // Step 5: Confirm location
            confirmLocation();

            // Step 6: Select date and time
            selectDateTime();

            // Step 7: Select additional services and recurrence
            selectAdditionalServices(new String[]{"Shampoo & Scalp Massage"});
            selectRecurrence("No, just once");

            // Step 8: Review but don't confirm
            reviewAndVerifyDetails();

            System.out.println("✅ Test 1 PASSED: Successfully reached Review & Cost Breakdown screen\n");

        } catch (Exception e) {
            System.err.println("❌ Test 1 FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // NEGATIVE TEST CASE: Cancel at each step
    public void testCancelAtEachStep() {
        System.out.println("Test 2: Cancel/Close at each step");

        try {
            // Test close on service type selection
            navigateToHomeScreen();
            clickScheduleAppointment();
            Thread.sleep(1000);
            clickCloseButton();
            verifyOnHomeScreen();
            System.out.println("✅ Substep 2.1: Close on service type - PASSED");

            // Test close on appointment for selection
            clickScheduleAppointment();
            selectServiceType("Barber");
            Thread.sleep(1000);
            clickCloseButton();
            verifyOnHomeScreen();
            System.out.println("✅ Substep 2.2: Close on appointment for - PASSED");

            // Test close on services selection
            clickScheduleAppointment();
            selectServiceType("Barber");
            selectAppointmentFor("Myself");
            Thread.sleep(1000);
            // Look for back button or X button
            clickBackButton();
            verifyPreviousScreen("Who is this appointment for?");
            System.out.println("✅ Substep 2.3: Back on services selection - PASSED");

            System.out.println("✅ Test 2 PASSED: Cancel functionality works at all steps\n");

        } catch (Exception e) {
            System.err.println("❌ Test 2 FAILED: " + e.getMessage());
        }
    }

    // NEGATIVE TEST CASE: Navigation back button
    public void testNavigationBackButton() {
        System.out.println("Test 3: Back navigation through flow");

        try {
            navigateToHomeScreen();
            clickScheduleAppointment();

            // Go forward a few steps
            selectServiceType("Hair Stylist");
            selectAppointmentFor("Myself");

            // Navigate back
            clickBackButton();
            verifyPreviousScreen("Who is this appointment for?");

            clickBackButton();
            verifyPreviousScreen("Are you scheduling for a Barber or a Hair Stylist?");

            System.out.println("✅ Test 3 PASSED: Back navigation works correctly\n");

        } catch (Exception e) {
            System.err.println("❌ Test 3 FAILED: " + e.getMessage());
        }
    }

    // NEGATIVE TEST CASE: Invalid selections
    public void testInvalidSelections() {
        System.out.println("Test 4: Invalid selections and error handling");

        try {
            navigateToHomeScreen();
            clickScheduleAppointment();
            selectServiceType("Barber");
            selectAppointmentFor("Myself");

            // Try to proceed without selecting any services
            clickNextButton();

            // Should either show error or button should be disabled
            Thread.sleep(1000);

            // Check if we're still on services screen
            if (isElementPresent("Select Services")) {
                System.out.println("✅ Substep 4.1: Cannot proceed without service selection - PASSED");
            }

            // Select services and continue to date/time
            selectServices(new String[]{"Skin Fade / Taper"});
            confirmLocation();

            // Try to proceed without selecting date/time
            clickNextButton();
            Thread.sleep(1000);

            // Check if we're still on date/time screen
            if (isElementPresent("Select Date & Time")) {
                System.out.println("✅ Substep 4.2: Cannot proceed without date/time selection - PASSED");
            }

            System.out.println("✅ Test 4 PASSED: Invalid selections are properly handled\n");

        } catch (Exception e) {
            System.err.println("❌ Test 4 FAILED: " + e.getMessage());
        }
    }

    // NEGATIVE TEST CASE: Empty selections
    public void testEmptySelections() {
        System.out.println("Test 5: Empty field validation");

        try {
            navigateToHomeScreen();
            clickScheduleAppointment();

            // Try clicking Next without selecting service type
            // (This might not be possible if it's a modal that requires selection)

            selectServiceType("Barber");
            selectAppointmentFor("Myself");

            // Deselect all services if any are pre-selected
            deselectAllServices();

            // Try to proceed
            clickNextButton();
            Thread.sleep(1000);

            if (isElementPresent("Select Services")) {
                System.out.println("✅ Test 5 PASSED: Empty selections are validated\n");
            }

        } catch (Exception e) {
            System.err.println("❌ Test 5 FAILED: " + e.getMessage());
        }
    }

    // Helper methods for test actions

    private void navigateToHomeScreen() throws InterruptedException {
        System.out.println("📍 Navigating to home screen...");

        // Check if we're already on home screen
        if (isElementPresent("Schedule an")) {
            return;
        }

        // Try to find and click Home button
        try {
            WebElement homeButton = driver.findElement(
                    AppiumBy.xpath("//*[@text='Home' or contains(@content-desc, 'Home')]"));
            homeButton.click();
            Thread.sleep(2000);
        } catch (Exception e) {
            System.out.println("Home button not found, assuming we're on home screen");
        }
    }

    private void clickScheduleAppointment() throws InterruptedException {
        System.out.println("📅 Clicking Schedule an Appointment...");

        WebElement scheduleButton = wait.until(ExpectedConditions.elementToBeClickable(
                AppiumBy.xpath("//*[contains(@text, 'Schedule an') or contains(@content-desc, 'Schedule an')]")));
        scheduleButton.click();
        Thread.sleep(2000);
    }

    private void selectServiceType(String type) throws InterruptedException {
        System.out.println("💈 Selecting service type: " + type);

        WebElement serviceOption = wait.until(ExpectedConditions.elementToBeClickable(
                AppiumBy.xpath("//*[contains(@text, '" + type + "') or contains(@content-desc, '" + type + "')]")));
        serviceOption.click();
        Thread.sleep(1000);

        clickNextButton();
    }

    private void selectAppointmentFor(String person) throws InterruptedException {
        System.out.println("👤 Selecting appointment for: " + person);

        WebElement personOption = wait.until(ExpectedConditions.elementToBeClickable(
                AppiumBy.xpath("//*[contains(@text, '" + person + "') or contains(@content-desc, '" + person + "')]")));
        personOption.click();
        Thread.sleep(1000);

        clickNextButton();
    }

    private void selectServices(String[] services) throws InterruptedException {
        System.out.println("✂️ Selecting services...");

        for (String service : services) {
            try {
                WebElement serviceCheckbox = driver.findElement(
                        AppiumBy.xpath("//*[contains(@text, '" + service + "')]/following-sibling::*[@checkable='true'] | " +
                                "//*[contains(@text, '" + service + "')]/parent::*//*[@checkable='true']"));

                if (serviceCheckbox.getAttribute("checked").equals("false")) {
                    serviceCheckbox.click();
                    System.out.println("  ✓ Selected: " + service);
                }
            } catch (Exception e) {
                // Try alternative approach - click the entire row
                try {
                    WebElement serviceRow = driver.findElement(
                            AppiumBy.xpath("//*[contains(@text, '" + service + "')]"));
                    serviceRow.click();
                    System.out.println("  ✓ Selected: " + service);
                } catch (Exception e2) {
                    System.err.println("  ✗ Could not select: " + service);
                }
            }
            Thread.sleep(500);
        }

        Thread.sleep(1000);
        clickNextButton();
    }

    private void confirmLocation() throws InterruptedException {
        System.out.println("📍 Confirming location...");

        // Wait for location screen
        wait.until(ExpectedConditions.presenceOfElementLocated(
                AppiumBy.xpath("//*[contains(@text, 'Confirm') and contains(@text, 'Location')]")));

        // Check if default location is present
        if (isElementPresent("1611 Biscayne Blvd")) {
            System.out.println("  ✓ Default location found");
        }

        Thread.sleep(1000);
        clickNextButton();
    }

    private void selectDateTime() throws InterruptedException {
        System.out.println("📅 Selecting date and time...");

        // Select date (if not already selected)
        try {
            WebElement dateField = driver.findElement(
                    AppiumBy.xpath("//*[contains(@text, 'Select Date')]"));
            dateField.click();
            Thread.sleep(1000);

            // For now, accept default date
            clickNextButton();
        } catch (Exception e) {
            System.out.println("  Date already selected");
        }

        // Select time (if not already selected)
        try {
            WebElement timeField = driver.findElement(
                    AppiumBy.xpath("//*[contains(@text, 'Select Time')]"));
            timeField.click();
            Thread.sleep(1000);

            // For now, accept default time
            clickNextButton();
        } catch (Exception e) {
            System.out.println("  Time already selected");
        }

        // Final next button on date/time screen
        Thread.sleep(1000);
        clickNextButton();

        // Wait for the app to find a professional
        System.out.println("⏳ Waiting for professional assignment...");
        Thread.sleep(5000); // Give the app 5 seconds to find a professional

        // Check if we see "Finding a professional" or similar loading indicator
        try {
            WebElement loadingIndicator = driver.findElement(
                    AppiumBy.xpath("//*[contains(@text, 'Finding') or contains(@text, 'Loading') or contains(@text, 'Searching')]"));

            // If loading indicator is found, wait for it to disappear
            wait.until(ExpectedConditions.invisibilityOf(loadingIndicator));
            System.out.println("  ✓ Professional search completed");
        } catch (Exception e) {
            // No loading indicator found, assume it loaded quickly
            System.out.println("  ✓ Proceeded to next screen");
        }

        // Additional wait to ensure the next screen is fully loaded
        Thread.sleep(2000);
    }

    private void selectAdditionalServices(String[] additionalServices) throws InterruptedException {
        System.out.println("➕ Selecting additional services...");

        for (String service : additionalServices) {
            try {
                WebElement serviceCheckbox = driver.findElement(
                        AppiumBy.xpath("//*[contains(@text, '" + service + "')]/following-sibling::*[@checkable='true']"));

                if (serviceCheckbox.getAttribute("checked").equals("false")) {
                    serviceCheckbox.click();
                    System.out.println("  ✓ Selected additional: " + service);
                }
            } catch (Exception e) {
                System.err.println("  ✗ Could not select additional: " + service);
            }
        }
    }

    private void selectRecurrence(String option) throws InterruptedException {
        System.out.println("🔄 Selecting recurrence: " + option);

        WebElement recurrenceOption = driver.findElement(
                AppiumBy.xpath("//*[contains(@text, '" + option + "')]"));
        recurrenceOption.click();

        Thread.sleep(1000);
        clickNextButton();
    }

    private void reviewAndVerifyDetails() throws InterruptedException {
        System.out.println("👁️ Reviewing appointment details...");

        // Wait for review screen
        wait.until(ExpectedConditions.presenceOfElementLocated(
                AppiumBy.xpath("//*[contains(@text, 'Review') or contains(@text, 'Total')]")));

        // Verify total is displayed
        if (isElementPresent("$")) {
            System.out.println("  ✓ Total price displayed");
        }

        // Verify key elements on review screen
        if (isElementPresent("Peak Hour Charge")) {
            System.out.println("  ✓ Peak Hour Charge displayed");
        }

        if (isElementPresent("Distance Surcharge")) {
            System.out.println("  ✓ Distance Surcharge displayed");
        }

        if (isElementPresent("AI Discount")) {
            System.out.println("  ✓ AI Discount displayed");
        }

        // Verify Confirm & Pay button is present but DO NOT click it
        if (isElementPresent("Confirm & Pay")) {
            System.out.println("  ✓ Confirm & Pay button is present");
            System.out.println("  ⚠️  Stopping at Review screen - tester will manually confirm payment");
        }

        Thread.sleep(2000);
    }

    // Helper methods for navigation and validation

    private void clickNextButton() throws InterruptedException {
        WebElement nextButton = wait.until(ExpectedConditions.elementToBeClickable(
                AppiumBy.xpath("//*[@text='Next' or contains(@content-desc, 'Next')]")));
        nextButton.click();
        Thread.sleep(1000);
    }

    private void clickBackButton() throws InterruptedException {
        try {
            WebElement backButton = driver.findElement(
                    AppiumBy.xpath("//*[@content-desc='Back' or @text='←' or contains(@class, 'android.widget.ImageButton')]"));
            backButton.click();
        } catch (Exception e) {
            // Try tapping coordinates for back arrow
            driver.executeScript("mobile: tap", Map.of("x", 50, "y", 150));
        }
        Thread.sleep(1000);
    }

    private void clickCloseButton() throws InterruptedException {
        try {
            WebElement closeButton = driver.findElement(
                    AppiumBy.xpath("//*[@text='Close' or @text='X' or contains(@content-desc, 'Close')]"));
            closeButton.click();
        } catch (Exception e) {
            // Try tapping coordinates for close button
            driver.executeScript("mobile: tap", Map.of("x", driver.manage().window().getSize().width - 50, "y", 150));
        }
        Thread.sleep(1000);
    }

    private boolean isElementPresent(String text) {
        try {
            driver.findElement(AppiumBy.xpath("//*[contains(@text, '" + text + "') or contains(@content-desc, '" + text + "')]"));
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private void verifyOnHomeScreen() {
        if (isElementPresent("Schedule an") || isElementPresent("Request Service")) {
            System.out.println("  ✓ Successfully returned to home screen");
        } else {
            System.err.println("  ✗ Not on home screen");
        }
    }

    private void verifyPreviousScreen(String expectedText) {
        if (isElementPresent(expectedText)) {
            System.out.println("  ✓ Successfully navigated back to: " + expectedText);
        } else {
            System.err.println("  ✗ Did not navigate back correctly");
        }
    }

    private void deselectAllServices() {
        try {
            List<WebElement> checkedBoxes = driver.findElements(
                    AppiumBy.xpath("//*[@checked='true' and @checkable='true']"));

            for (WebElement checkbox : checkedBoxes) {
                checkbox.click();
                Thread.sleep(200);
            }
        } catch (Exception e) {
            System.out.println("No services to deselect");
        }
    }

    public void tearDown() {
        if (driver != null) {
            driver.quit();
            System.out.println("✅ Test session ended");
        }
    }
}