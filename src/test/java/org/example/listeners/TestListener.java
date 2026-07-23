package org.example.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.factory.AppiumDriverFactory;
import org.example.factory.PlaywrightFactory;
import org.example.reports.ExtentManager;
import org.example.utils.ScreenshotUtil;
import org.testng.IAnnotationTransformer;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Bridges TestNG events to ExtentReports and attaches a screenshot on failure. Also acts as an
 * {@link IAnnotationTransformer} to apply {@link RetryAnalyzer} to every @Test automatically.
 *
 * <p>Registered once in {@code testng.xml} so no test/base class needs to reference it.
 */
public class TestListener implements ITestListener, IAnnotationTransformer {

    private static final Logger LOG = LogManager.getLogger(TestListener.class);

    @Override
    public void onStart(ITestContext context) {
        LOG.info("==== Test suite started: {} ====", context.getName());
    }

    @Override
    public void onTestStart(ITestResult result) {
        ExtentTest test = ExtentManager.getInstance().createTest(result.getMethod().getMethodName());
        ExtentManager.setTest(test);
        LOG.info(">>> START: {}", result.getMethod().getMethodName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentManager.getTest().log(Status.PASS, "Test passed");
        LOG.info("<<< PASS: {}", result.getMethod().getMethodName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOG.error("<<< FAIL: {}", result.getMethod().getMethodName(), result.getThrowable());
        ExtentTest test = ExtentManager.getTest();
        if (test != null) {
            test.log(Status.FAIL, result.getThrowable());
            // Capture from whichever driver is active for this test (mobile Appium or web Playwright).
            String base64 = AppiumDriverFactory.getDriver() != null
                    ? ScreenshotUtil.captureBase64(AppiumDriverFactory.getDriver())
                    : ScreenshotUtil.captureBase64(PlaywrightFactory.getPage());
            if (base64 != null) {
                test.fail("Screenshot on failure",
                        MediaEntityBuilder.createScreenCaptureFromBase64String(base64).build());
            }
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        ExtentTest test = ExtentManager.getTest();
        if (test != null) {
            test.log(Status.SKIP, "Test skipped");
        }
        LOG.warn("<<< SKIP: {}", result.getMethod().getMethodName());
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentManager.flush();
        LOG.info("==== Test suite finished: {} ====", context.getName());
    }

    /** Applies {@link RetryAnalyzer} to every @Test method in the suite. */
    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
