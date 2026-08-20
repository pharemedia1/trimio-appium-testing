package org.example.base;

import com.microsoft.playwright.Page;
import org.example.factory.PlaywrightFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Parent of all test classes. Owns the per-test browser lifecycle via {@link PlaywrightFactory}.
 * A fresh browser/context/page is created before each test method and torn down afterwards,
 * which keeps tests isolated and parallel-safe.
 */
public abstract class BaseTest {

    /** The page for the current test thread; subclasses use it to build page objects. */
    protected Page page;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        page = PlaywrightFactory.createPage();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        PlaywrightFactory.closePage();
    }
}
