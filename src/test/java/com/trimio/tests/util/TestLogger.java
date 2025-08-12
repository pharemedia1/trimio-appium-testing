package com.trimio.tests.util;

import org.testng.asserts.SoftAssert;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TestLogger {
    private static PrintWriter logWriter;
    private static WebDriver driver;
    private static final String LOG_FILE = "src/test/java/com/trimio/tests/TestCaseReports/test-logs.txt";
    private static final String SCREENSHOT_DIR = "src/test/java/com/trimio/tests/TestCaseReports/screenshots";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private SoftAssert softAssert;
    private String currentTestSuite;
    private String currentTestMethod;
    private List<String> assertions;
    private static int globalScreenshotCounter = 0;

    public TestLogger(String testSuiteName) {
        this.currentTestSuite = testSuiteName;
        this.softAssert = new SoftAssert();
        this.assertions = new ArrayList<>();
        initializeLogWriter();
        createScreenshotDirectory();
    }

    public static void setDriver(WebDriver webDriver) {
        driver = webDriver;
    }

    public void setCurrentTestMethod(String methodName) {
        this.currentTestMethod = methodName;
    }

    private static void initializeLogWriter() {
        if (logWriter == null) {
            try {
                logWriter = new PrintWriter(new FileWriter(LOG_FILE, true));
            } catch (IOException e) {
                System.err.println("Could not initialize log writer: " + e.getMessage());
            }
        }
    }

    private static void createScreenshotDirectory() {
        try {
            File screenshotDir = new File(SCREENSHOT_DIR);
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
                System.out.println("Created screenshot directory: " + SCREENSHOT_DIR);
            }
        } catch (Exception e) {
            System.err.println("Could not create screenshot directory: " + e.getMessage());
        }
    }

    private String takeScreenshot(String description) {
        try {
            if (driver == null) {
                System.err.println("Driver is null - cannot take screenshot");
                return null;
            }

            TakesScreenshot takesScreenshot = (TakesScreenshot) driver;
            byte[] screenshot = takesScreenshot.getScreenshotAs(OutputType.BYTES);

            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT);
            globalScreenshotCounter++;

            String filename = String.format("assertion-fail-%d_%s.png",
                    globalScreenshotCounter, timestamp);

            File screenshotFile = new File(SCREENSHOT_DIR, filename);
            Files.write(screenshotFile.toPath(), screenshot);

            System.out.println("Screenshot saved: " + screenshotFile.getAbsolutePath());
            return screenshotFile.getName();

        } catch (Exception e) {
            System.err.println("Failed to take screenshot: " + e.getMessage());
            return null;
        }
    }

    private String takeDebugScreenshotInternal(String description) {
        try {
            if (driver == null) {
                System.err.println("Driver is null - cannot take screenshot");
                return null;
            }

            TakesScreenshot takesScreenshot = (TakesScreenshot) driver;
            byte[] screenshot = takesScreenshot.getScreenshotAs(OutputType.BYTES);

            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT);
            globalScreenshotCounter++;

            String filename = String.format("debug-%d_%s.png",
                    globalScreenshotCounter, timestamp);

            File screenshotFile = new File(SCREENSHOT_DIR, filename);
            Files.write(screenshotFile.toPath(), screenshot);

            System.out.println("Debug screenshot saved: " + screenshotFile.getAbsolutePath());
            return screenshotFile.getName();

        } catch (Exception e) {
            System.err.println("Failed to take debug screenshot: " + e.getMessage());
            return null;
        }
    }

    public void softAssertTrue(boolean condition, String description) {
        String result = condition ? "PASSED" : "FAILED";
        String logEntry = String.format("  %s - %s", description, result);

        if (!condition) {
            String screenshotName = takeScreenshot(description);
            if (screenshotName != null) {
                logEntry += " [Screenshot: " + screenshotName + "]";
            }
        }

        assertions.add(logEntry);
        softAssert.assertTrue(condition, description);
    }

    public void softAssertEquals(String actual, String expected, String description) {
        boolean condition = expected.equals(actual);
        String result = condition ? "PASSED" : "FAILED";
        String logEntry = String.format("  %s - %s", description, result);

        if (!condition) {
            logEntry += String.format(" (Expected: '%s', Actual: '%s')", expected, actual);

            String screenshotName = takeScreenshot(description);
            if (screenshotName != null) {
                logEntry += " [Screenshot: " + screenshotName + "]";
            }
        }

        assertions.add(logEntry);
        softAssert.assertEquals(actual, expected, description);
    }

    public void softAssertNotNull(Object object, String description) {
        boolean condition = object != null;
        String result = condition ? "PASSED" : "FAILED";
        String logEntry = String.format("  %s - %s", description, result);

        if (!condition) {
            String screenshotName = takeScreenshot(description);
            if (screenshotName != null) {
                logEntry += " [Screenshot: " + screenshotName + "]";
            }
        }

        assertions.add(logEntry);
        softAssert.assertNotNull(object, description);
    }

    public void takeDebugScreenshot(String description) {
        String screenshotName = takeDebugScreenshotInternal(description);
        if (screenshotName != null) {
            String logEntry = String.format("  DEBUG SCREENSHOT: %s [Screenshot: %s]", description, screenshotName);
            assertions.add(logEntry);
        }
    }

    public void assertAll() {
        try {
            logWriter.println("\n" + "=".repeat(80));
            logWriter.println("TEST SUITE: " + currentTestSuite);
            if (currentTestMethod != null) {
                logWriter.println("TEST METHOD: " + currentTestMethod);
            }
            logWriter.println("TIMESTAMP: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
            logWriter.println("=".repeat(80));

            for (String assertion : assertions) {
                logWriter.println(assertion);
            }

            long passCount = assertions.stream().mapToLong(a -> a.contains("PASSED") ? 1 : 0).sum();
            long failCount = assertions.stream().mapToLong(a -> a.contains("FAILED") ? 1 : 0).sum();
            long screenshotCount = assertions.stream().mapToLong(a -> a.contains("[Screenshot:") ? 1 : 0).sum();

            logWriter.println("-".repeat(40));
            logWriter.printf("SUMMARY: %d PASSED, %d FAILED, %d SCREENSHOTS%n", passCount, failCount, screenshotCount);
            logWriter.println("-".repeat(40));
            logWriter.flush();

            assertions.clear();

        } catch (Exception e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }

        softAssert.assertAll();
    }

    public static void closeLogger() {
        if (logWriter != null) {
            logWriter.close();
        }
    }
}