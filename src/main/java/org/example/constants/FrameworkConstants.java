package org.example.constants;

/**
 * Central place for framework-wide paths and defaults so they are not
 * scattered as magic strings across the codebase.
 */
public final class FrameworkConstants {

    private FrameworkConstants() {
        // static holder
    }

    /** Project root (current working directory when Maven/IntelliJ runs). */
    public static final String USER_DIR = System.getProperty("user.dir");

    public static final String CONFIG_FILE = "config.properties";

    public static final String REPORTS_DIR = USER_DIR + "/reports";
    public static final String SCREENSHOTS_DIR = USER_DIR + "/reports/screenshots";
    public static final String LOGS_DIR = USER_DIR + "/logs";

    public static final String EXTENT_REPORT_NAME = "Trimio-Automation-Report.html";

    /** Default explicit timeout in milliseconds for element actions/waits. */
    public static final int DEFAULT_TIMEOUT_MS = 30_000;
}
