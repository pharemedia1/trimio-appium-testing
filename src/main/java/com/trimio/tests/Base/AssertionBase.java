package com.trimio.tests.Base;

import java.io.FileNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
public abstract class AssertionBase {
    ArrayList<String> failedAssertions = new ArrayList<>();


    // Basic Assertions
    protected void assertTrue(boolean condition, String message) {
        if (!condition) {
            String error = "Assertion failed: " + message;
            logError(error);
            failedAssertions.add(error);
        } else {
            logInfo("Assertion passed: " + message);
        }
    }

    protected void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            String errorMsg = message + " - Expected: '" + expected + "', Actual: '" + actual + "'";
            logError("Assertion failed: " + errorMsg);
            failedAssertions.add(errorMsg);
        } else {
            logInfo("Assertion passed: " + message);
        }
    }

    protected void assertNotNull(Object object, String message) {
        if (object == null) {
            logError("Assertion failed: " + message);
            failedAssertions.add("Assertion failed: " + message);
        } else {
            logInfo("Assertion passed: " + message);
        }
    }

    protected void returnFails(){
        if(!failedAssertions.isEmpty()) {
            try(PrintWriter writer = new PrintWriter(new FileWriter("/Reports/reports.txt", true))) {
                String fails = String.join("\n", failedAssertions);
                LocalDate today = LocalDate.now();
                writer.println("===============================================");
                writer.println("Failed Assertions | " + today);
                writer.println("===============================================");
                writer.println(fails);
                throw new AssertionError(fails);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }else{
            System.out.println("No assertions failed.");
        }
    }


    // Logging
    protected void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    protected void logError(String message) {
        System.err.println("[ERROR] " + message);
    }

}
