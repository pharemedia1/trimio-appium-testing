package org.example.base;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parent of every page object. Wraps the most common Playwright element actions so page
 * objects read as business steps and logging/waiting is consistent across the suite.
 *
 * <p>Playwright auto-waits for actionability on click/fill/etc., so explicit waits are rarely
 * needed — but helpers like {@link #waitForVisible(String)} are provided for assertions and
 * synchronization points.
 */
public abstract class BasePage {

    protected static final Logger LOG = LogManager.getLogger(BasePage.class);

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /** Navigates to a fully-qualified URL and waits for the network to settle. */
    protected void navigate(String url) {
        LOG.info("Navigating to: {}", url);
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    protected Locator locator(String selector) {
        return page.locator(selector);
    }

    protected void click(String selector) {
        LOG.debug("Click: {}", selector);
        page.locator(selector).click();
    }

    protected void fill(String selector, String value) {
        LOG.debug("Fill '{}' into: {}", value, selector);
        page.locator(selector).fill(value);
    }

    protected String getText(String selector) {
        return page.locator(selector).innerText();
    }

    protected boolean isVisible(String selector) {
        return page.locator(selector).isVisible();
    }

    protected void waitForVisible(String selector) {
        page.locator(selector).waitFor();
    }

    public String getTitle() {
        return page.title();
    }

    public String getCurrentUrl() {
        return page.url();
    }
}
