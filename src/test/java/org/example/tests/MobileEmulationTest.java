package org.example.tests;

import com.microsoft.playwright.options.ViewportSize;
import org.example.base.BaseTest;
import org.example.config.ConfigReader;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Sanity test for the mobile-web emulation path ({@code -Ddevice=pixel7|iphone14}). It is
 * independent of the Trimio placeholders: it hits a real, responsive public site and asserts
 * the device signals that prove an emulated mobile context is in effect.
 *
 * <p>Engine-agnostic by design — touch is checked via {@code 'ontouchstart' in window} rather
 * than {@code navigator.maxTouchPoints}, because Playwright's WebKit reports {@code 0} for the
 * latter even though touch is enabled (see README "WebKit touch quirk").
 *
 * <p>When no device is configured (the default desktop run) the test {@link SkipException skips}
 * itself, so a plain {@code mvn test} is unaffected; run it with {@code -Ddevice=iphone14}.
 */
public class MobileEmulationTest extends BaseTest {

    /** A real, responsive site (has a {@code <meta viewport>} tag) — no Trimio dependency. */
    private static final String RESPONSIVE_URL = "https://playwright.dev";

    @Test
    public void mobileEmulationIsApplied() {
        String device = ConfigReader.get("device", "none");
        if (device == null || device.isBlank() || "none".equalsIgnoreCase(device)) {
            throw new SkipException(
                    "No mobile device configured — run with -Ddevice=pixel7 or -Ddevice=iphone14.");
        }

        page.navigate(RESPONSIVE_URL);

        // 1) The context has a fixed, phone-sized viewport, and the responsive page adopts it.
        ViewportSize viewport = page.viewportSize();
        Assert.assertNotNull(viewport, "An emulated mobile context should have a fixed viewport");
        Assert.assertTrue(viewport.width < 600,
                "Expected a phone-width context viewport, got " + viewport.width + "px");
        int innerWidth = ((Number) page.evaluate("() => window.innerWidth")).intValue();
        Assert.assertTrue(innerWidth < 600,
                "Responsive page should lay out at phone width, got " + innerWidth + "px");

        // 2) Mobile user-agent (both Pixel 7 and iPhone 14 UAs contain "Mobile").
        String userAgent = (String) page.evaluate("() => navigator.userAgent");
        Assert.assertTrue(userAgent.contains("Mobile"),
                "Expected a mobile user-agent, got: " + userAgent);

        // 3) Touch is enabled (engine-agnostic check — see class Javadoc).
        boolean hasTouch = (Boolean) page.evaluate("() => 'ontouchstart' in window");
        Assert.assertTrue(hasTouch, "Expected touch support ('ontouchstart' in window)");
        boolean coarsePointer = (Boolean) page.evaluate(
                "() => matchMedia('(pointer: coarse)').matches");
        Assert.assertTrue(coarsePointer, "Expected a coarse (touch) primary pointer");

        // 4) High-density mobile screen (Pixel 7 = 2.625, iPhone 14 = 3).
        double dpr = ((Number) page.evaluate("() => window.devicePixelRatio")).doubleValue();
        Assert.assertTrue(dpr > 1.0, "Expected a mobile device-pixel-ratio > 1, got " + dpr);
    }
}
