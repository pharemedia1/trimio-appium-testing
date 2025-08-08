package com.trimio.tests.util;

import org.testng.asserts.SoftAssert;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TestLogger {
    private static PrintWriter logWriter;
    private static final String LOG_FILE = "src/main/java/com/trimio/tests/TestCaseReports/test-logs.txt";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SoftAssert softAssert;
    private String currentTestSuite;
    private List<String> assertions;

    public TestLogger(String testSuiteName) {
        this.currentTestSuite = testSuiteName;
        this.softAssert = new SoftAssert();
        this.assertions = new ArrayList<>();
        initializeLogWriter();
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

    public void softAssertTrue(boolean condition, String description) {
        String result = condition ? "PASSED" : "FAILED";
        String logEntry = String.format("  %s - %s", description, result);

        assertions.add(logEntry);
        softAssert.assertTrue(condition, description);
    }

    public void softAssertEquals(String actual, String expected, String description) {
        boolean condition = expected.equals(actual);
        String result = condition ? "PASSED" : "FAILED";
        String logEntry = String.format("  %s - %s", description, result);
        if (!condition) {
            logEntry += String.format(" (Expected: '%s', Actual: '%s')", expected, actual);
        }

        assertions.add(logEntry);
        softAssert.assertEquals(actual, expected, description);
    }

    public void softAssertNotNull(Object object, String description) {
        boolean condition = object != null;
        String result = condition ? "PASSED" : "FAILED";
        String logEntry = String.format("  %s - %s", description, result);

        assertions.add(logEntry);
        softAssert.assertNotNull(object, description);
    }

    public void assertAll() {
        try {
            // Write test suite header
            logWriter.println("\n" + "=".repeat(80));
            logWriter.println("TEST SUITE: " + currentTestSuite);
            logWriter.println("TIMESTAMP: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
            logWriter.println("=".repeat(80));

            // Write all assertions
            for (String assertion : assertions) {
                logWriter.println(assertion);
            }

            // Write summary
            long passCount = assertions.stream().mapToLong(a -> a.contains("PASSED") ? 1 : 0).sum();
            long failCount = assertions.stream().mapToLong(a -> a.contains("FAILED") ? 1 : 0).sum();

            logWriter.println("-".repeat(40));
            logWriter.printf("SUMMARY: %d PASSED, %d FAILED%n", passCount, failCount);
            logWriter.println("-".repeat(40));
            logWriter.flush();

            // Clear assertions for next test
            assertions.clear();

        } catch (Exception e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }

        // This will throw AssertionError if any soft assertions failed
        softAssert.assertAll();
    }

    public static void closeLogger() {
        if (logWriter != null) {
            logWriter.close();
        }
    }
}