package org.example.listeners;

import org.example.config.ConfigReader;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries a failed test up to {@code retryCount} times (config key, default 1) to absorb
 * flaky UI failures. Wired per-test via {@link TestListener} so individual @Test methods
 * don't need to declare it.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private int attempt = 0;
    private final int maxRetries = ConfigReader.getInt("retryCount", 1);

    @Override
    public boolean retry(ITestResult result) {
        if (attempt < maxRetries) {
            attempt++;
            return true;
        }
        return false;
    }
}
