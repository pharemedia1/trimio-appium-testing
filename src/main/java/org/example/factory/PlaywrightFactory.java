package org.example.factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.config.ConfigReader;
import org.example.constants.FrameworkConstants;
import org.example.enums.BrowserType;
import org.example.enums.MobileDevice;

import java.util.Optional;

/**
 * Owns the Playwright lifecycle. All four objects ({@link Playwright}, {@link Browser},
 * {@link BrowserContext}, {@link Page}) are held in {@link ThreadLocal}s so TestNG can run
 * tests in parallel (per-method/per-class threads) without state bleeding between threads.
 *
 * <p>Typical usage from {@code BaseTest}:
 * <pre>
 *   Page page = PlaywrightFactory.createPage();   // @BeforeMethod
 *   ...
 *   PlaywrightFactory.closePage();                // @AfterMethod
 * </pre>
 */
public final class PlaywrightFactory {

    private static final Logger LOG = LogManager.getLogger(PlaywrightFactory.class);

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    private PlaywrightFactory() {
        // static factory
    }

    /** @return the {@link Page} bound to the current thread (null if not yet created). */
    public static Page getPage() {
        return PAGE.get();
    }

    /** @return the {@link BrowserContext} bound to the current thread. */
    public static BrowserContext getContext() {
        return CONTEXT.get();
    }

    /**
     * Launches a browser according to config and returns a fresh page. Reads:
     * <ul>
     *   <li>{@code browser} — chromium | firefox | webkit (default chromium)</li>
     *   <li>{@code headless} — true | false (default true)</li>
     *   <li>{@code slowMo} — milliseconds to slow each action (default 0)</li>
     *   <li>{@code timeout} — default action/navigation timeout in ms</li>
     *   <li>{@code device} — none | pixel7 | iphone14 (mobile-web emulation; the
     *       device's engine overrides {@code browser})</li>
     * </ul>
     */
    public static Page createPage() {
        String browserName = ConfigReader.get("browser", "chromium");
        boolean headless = ConfigReader.getBoolean("headless", true);
        double slowMo = ConfigReader.getInt("slowMo", 0);
        int timeout = ConfigReader.getInt("timeout", FrameworkConstants.DEFAULT_TIMEOUT_MS);
        Optional<MobileDevice> device = MobileDevice.from(ConfigReader.get("device", "none"));

        // A mobile profile needs isMobile/touch, which only chromium & webkit honour, so the
        // device dictates the engine — overriding `browser` when they differ (never Firefox).
        BrowserType engine = device.map(MobileDevice::engine).orElseGet(() -> BrowserType.from(browserName));
        if (device.isPresent() && !engine.name().equalsIgnoreCase(browserName)) {
            LOG.info("device='{}' requires {} — overriding browser='{}'",
                    device.get(), engine, browserName);
        }

        LOG.info("Launching engine='{}' headless={} slowMo={}ms device='{}'",
                engine, headless, slowMo, device.map(Enum::name).orElse("none"));

        Playwright playwright = Playwright.create();
        PLAYWRIGHT.set(playwright);

        LaunchOptions options = new LaunchOptions().setHeadless(headless).setSlowMo(slowMo);
        Browser browser = launch(playwright, engine, options);
        BROWSER.set(browser);

        BrowserContext context = device
                .map(d -> browser.newContext(d.contextOptions()))
                .orElseGet(browser::newContext);
        context.setDefaultTimeout(timeout);
        CONTEXT.set(context);

        Page page = context.newPage();
        PAGE.set(page);
        return page;
    }

    private static Browser launch(Playwright playwright, BrowserType type, LaunchOptions options) {
        return switch (type) {
            case FIREFOX -> playwright.firefox().launch(options);
            case WEBKIT -> playwright.webkit().launch(options);
            case CHROMIUM -> playwright.chromium().launch(options);
        };
    }

    /** Closes the page/context/browser and disposes Playwright for the current thread. */
    public static void closePage() {
        try {
            if (CONTEXT.get() != null) {
                CONTEXT.get().close();
            }
            if (BROWSER.get() != null) {
                BROWSER.get().close();
            }
            if (PLAYWRIGHT.get() != null) {
                PLAYWRIGHT.get().close();
            }
        } catch (RuntimeException e) {
            LOG.warn("Error while closing Playwright resources: {}", e.getMessage());
        } finally {
            PAGE.remove();
            CONTEXT.remove();
            BROWSER.remove();
            PLAYWRIGHT.remove();
        }
    }
}
