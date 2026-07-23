package org.example.utils;

import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.constants.FrameworkConstants;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.nio.file.Paths;
import java.util.Base64;

/**
 * Screenshot helpers used by the reporting listener (e.g. capture on failure).
 */
public final class ScreenshotUtil {

    private static final Logger LOG = LogManager.getLogger(ScreenshotUtil.class);

    private ScreenshotUtil() {
        // static utility
    }

    /**
     * Saves a full-page PNG under {@code reports/screenshots} and returns the absolute path,
     * or {@code null} if the page is unavailable.
     */
    public static String capture(Page page, String name) {
        if (page == null) {
            return null;
        }
        String file = FrameworkConstants.SCREENSHOTS_DIR + "/" + name + ".png";
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get(file))
                .setFullPage(true));
        LOG.info("Saved screenshot: {}", file);
        return file;
    }

    /**
     * Captures a screenshot as a Base64 string for embedding directly into the HTML report
     * (no external file dependency). Returns {@code null} if the page is unavailable.
     */
    public static String captureBase64(Page page) {
        if (page == null) {
            return null;
        }
        byte[] bytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Captures a Base64 screenshot from any Selenium/Appium {@link WebDriver} (e.g. the Appium
     * {@code AndroidDriver}) for embedding into the HTML report. Returns {@code null} if the
     * driver is unavailable or cannot take a screenshot.
     */
    public static String captureBase64(WebDriver driver) {
        if (!(driver instanceof TakesScreenshot)) {
            return null;
        }
        try {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
        } catch (RuntimeException e) {
            LOG.warn("Failed to capture mobile screenshot: {}", e.getMessage());
            return null;
        }
    }
}
