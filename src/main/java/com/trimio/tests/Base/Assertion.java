package com.trimio.tests.Base;

public abstract class Assertion {
    // Basic Assertions
    protected void assertTrue(boolean condition, String message) {
        if (!condition) {
            logError("Assertion failed: " + message);
            throw new AssertionError(message);
        } else {
            logInfo("Assertion passed: " + message);
        }
    }

    protected void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            String errorMsg = message + " - Expected: '" + expected + "', Actual: '" + actual + "'";
            logError("Assertion failed: " + errorMsg);
            throw new AssertionError(errorMsg);
        } else {
            logInfo("Assertion passed: " + message);
        }
    }

    protected void assertNotNull(Object object, String message) {
        if (object == null) {
            logError("Assertion failed: " + message);
            throw new AssertionError(message);
        } else {
            logInfo("Assertion passed: " + message);
        }
    }
    protected void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    protected void logError(String message) {
        System.err.println("[ERROR] " + message);
    }
}
