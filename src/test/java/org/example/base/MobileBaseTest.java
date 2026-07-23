package org.example.base;

import io.appium.java_client.android.AndroidDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.data.TestAccounts;
import org.example.factory.AppiumDriverFactory;
import org.example.pages.mobile.OnboardingScreen;
import org.example.reports.ExtentManager;
import org.testng.ITestContext;
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

    protected AndroidDriver driver;

    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {
        LOG.info("==== @BeforeSuite: starting Appium server + loading test data ====");
        AppiumDriverFactory.startServer();
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
        driver = AppiumDriverFactory.createDriver();
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
