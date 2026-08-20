package org.example.base;

import io.appium.java_client.android.AndroidDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.data.TestAccounts;
import org.example.factory.AppiumDriverFactory;
import org.example.pages.mobile.OnboardingScreen;
import org.example.reports.ExtentManager;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;

/**
 * Parent of all mobile (Appium) test classes, wiring the full TestNG lifecycle:
 *
 * <ul>
 *   <li>{@code @BeforeSuite} — start the Appium server once and load externalized test data</li>
 *   <li>{@code @BeforeTest}  — per {@code <test>} tag in the suite XML</li>
 *   <li>{@code @BeforeClass} — per test class</li>
 *   <li>{@code @BeforeMethod}— a fresh {@link AndroidDriver} per test (app data cleared → onboarding)</li>
 *   <li>matching {@code @After*} hooks tear down in reverse order; {@code @AfterSuite} stops the
 *       server and flushes the report.</li>
 * </ul>
 *
 * Suite/test/class hooks are idempotent so inheritance across several test classes is safe.
 */
public abstract class MobileBaseTest {

    private static final Logger LOG = LogManager.getLogger(MobileBaseTest.class);

    /**
     * Set once the mobile environment has been shown to be absent, so the remaining tests skip
     * immediately instead of each spending a session-creation timeout discovering the same thing.
     *
     * <p>Only latched when <em>no</em> session has ever succeeded — i.e. the environment never came
     * up at all. A failure after a working session is a real failure (a crashed emulator, a genuine
     * bug) and is reported as one rather than quietly swallowed.
     */
    private static volatile String mobileUnavailable;

    /** Guards the latch above: proof that the environment did work at least once. */
    private static volatile boolean sessionEverCreated;

    protected AndroidDriver driver;

    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {
        LOG.info("==== @BeforeSuite: starting Appium server + loading test data ====");
        // A failure here is NOT fatal: autostart may be off, or a server may already be running at
        // appium.url, and createDriver() falls back to it. Letting this throw would fail the whole
        // suite in @BeforeSuite — a configuration error that reports as neither pass, fail nor a
        // usable skip — before a single test has had the chance to say what it needs.
        try {
            AppiumDriverFactory.startServer();
        } catch (RuntimeException e) {
            LOG.warn("Could not start the embedded Appium server ({}). Falling back to appium.url; "
                    + "tests will skip if no server answers there.", e.getMessage());
        }
        TestAccounts.load();
    }

    @BeforeTest(alwaysRun = true)
    public void beforeTest(ITestContext context) {
        LOG.info("---- @BeforeTest: {} ----", context.getCurrentXmlTest().getName());
    }

    @BeforeClass(alwaysRun = true)
    public void beforeClass() {
        LOG.info("-- @BeforeClass: {} --", getClass().getSimpleName());
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeMethod() {
        driver = openSessionOrSkip();
    }

    /**
     * Opens an Appium session, or raises {@link SkipException} when there is no mobile environment
     * to open one against.
     *
     * <p>Why a skip and not a failure: an absent emulator says nothing about the app under test, and
     * a suite run on a machine without one should report "not checked", not "broken". Letting the
     * session error escape a {@code @Before*} hook produces a TestNG <em>configuration failure</em>
     * instead — which fails the build while telling the reader nothing about what to install.
     *
     * <p>Shared with {@link RegisteredAccountTest}, which opens its own short-lived session in
     * {@code @BeforeClass}; without this it would hard-fail one hook earlier than the tests it sets
     * up, and skip the whole class as a configuration error.
     */
    protected static AndroidDriver openSessionOrSkip() {
        if (mobileUnavailable != null) {
            throw new SkipException(mobileUnavailable);
        }
        try {
            AndroidDriver session = AppiumDriverFactory.createDriver();
            sessionEverCreated = true;
            return session;
        } catch (RuntimeException e) {
            if (sessionEverCreated) {
                // The environment demonstrably works, so this is a real failure — a crashed
                // emulator or a genuine defect. Report it rather than hiding it behind a skip.
                throw e;
            }
            mobileUnavailable = "No Trimio mobile environment — could not open an Appium session. "
                    + "Boot an emulator (emulator -avd Pixel_7_API_35), install the app "
                    + "(com.trimio.trimio), and start the backend on :3000. Override the target with "
                    + "-Dappium.udid=… / -Dappium.url=… (" + e.getMessage() + ")";
            throw new SkipException(mobileUnavailable);
        }
    }

    /** Convenience: a fresh-launched app always opens on the onboarding screen. */
    protected OnboardingScreen onboarding() {
        return new OnboardingScreen(driver);
    }

    /** Reads a string field from a data-provider row (empty string if absent/null). */
    protected static String str(java.util.Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : value.toString();
    }

    /** Cold-restarts the app back to the onboarding screen (keeps app data; clears navigation). */
    protected void restartApp() {
        String pkg = org.example.config.ConfigReader.get("app.package", "com.trimio.trimio");
        driver.terminateApp(pkg);
        driver.activateApp(pkg);
    }

    @AfterMethod(alwaysRun = true)
    public void afterMethod() {
        AppiumDriverFactory.quitDriver();
    }

    @AfterClass(alwaysRun = true)
    public void afterClass() {
        LOG.info("-- @AfterClass: {} --", getClass().getSimpleName());
    }

    @AfterTest(alwaysRun = true)
    public void afterTest(ITestContext context) {
        LOG.info("---- @AfterTest: {} ----", context.getCurrentXmlTest().getName());
    }

    @AfterSuite(alwaysRun = true)
    public void afterSuite() {
        LOG.info("==== @AfterSuite: stopping Appium server + flushing report ====");
        AppiumDriverFactory.stopServer();
        ExtentManager.flush();
    }
}
