package org.example.enums;

import com.microsoft.playwright.Browser.NewContextOptions;

import java.util.Optional;

/**
 * Mobile-web emulation profiles. Playwright for Java has no {@code devices()} registry
 * (unlike JS/Python), so each profile carries the descriptor values verbatim from
 * Playwright's {@code deviceDescriptorsSource.json} (pinned to the bundled driver version).
 *
 * <p>{@code isMobile}/{@code hasTouch} emulation is only honoured by Chromium and WebKit —
 * <b>not Firefox</b> — so every profile declares the engine it must run on. The factory uses
 * {@link #engine()} to launch the right browser, which makes a Firefox + device combo
 * impossible (rather than silently dropping {@code isMobile}).
 *
 * @see org.example.factory.PlaywrightFactory
 */
public enum MobileDevice {

    /** Google Pixel 7 — Android 14, Chrome. */
    PIXEL7(
            BrowserType.CHROMIUM,
            412, 839, 2.625,
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/148.0.7778.96 Mobile Safari/537.36"),

    /** Apple iPhone 14 — iOS 16, Mobile Safari. */
    IPHONE14(
            BrowserType.WEBKIT,
            390, 664, 3.0,
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/26.4 Mobile/15E148 Safari/604.1");

    private final BrowserType engine;
    private final int viewportWidth;
    private final int viewportHeight;
    private final double deviceScaleFactor;
    private final String userAgent;

    MobileDevice(BrowserType engine, int viewportWidth, int viewportHeight,
                 double deviceScaleFactor, String userAgent) {
        this.engine = engine;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.deviceScaleFactor = deviceScaleFactor;
        this.userAgent = userAgent;
    }

    /** The browser engine this profile must run on ({@code isMobile} needs chromium/webkit). */
    public BrowserType engine() {
        return engine;
    }

    /** Context options that emulate this device (viewport, UA, scale, mobile + touch). */
    public NewContextOptions contextOptions() {
        return new NewContextOptions()
                .setViewportSize(viewportWidth, viewportHeight)
                .setUserAgent(userAgent)
                .setDeviceScaleFactor(deviceScaleFactor)
                .setIsMobile(true)
                .setHasTouch(true);
    }

    /**
     * Resolves a config string to a device. Returns empty for {@code null}/blank/{@code "none"}
     * or any unknown value, so a typo degrades to a normal desktop context rather than crashing.
     * Matching ignores case, spaces, hyphens and underscores ({@code "iPhone 14"} == {@code "iphone14"}).
     */
    public static Optional<MobileDevice> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String key = value.trim().toUpperCase().replaceAll("[\\s_-]", "");
        if (key.equals("NONE")) {
            return Optional.empty();
        }
        try {
            return Optional.of(MobileDevice.valueOf(key));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
