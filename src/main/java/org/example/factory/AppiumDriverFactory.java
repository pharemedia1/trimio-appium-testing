package org.example.factory;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.config.ConfigReader;

import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Owns the Appium lifecycle for the Trimio Android (Flutter) app.
 *
 * <p>The {@link AndroidDriver} is held in a {@link ThreadLocal} so TestNG could run tests in
 * parallel; in practice the mobile suite runs serially against a single emulator.
 *
 * <p>All settings are read through {@link ConfigReader}, so each can be overridden with
 * {@code -Dkey=value} or an {@code UPPER_SNAKE} env var without editing any file:
 * <ul>
 *   <li>{@code appium.autostart} — boot an embedded Appium server (default true)</li>
 *   <li>{@code appium.url} — server URL when autostart is false</li>
 *   <li>{@code appium.nodePath} / {@code appium.jsPath} — used to launch the embedded server</li>
 *   <li>{@code appium.deviceName} / {@code appium.udid} — target emulator (default emulator-5554)</li>
 *   <li>{@code app.package} / {@code app.activity} — Trimio app entry point</li>
 *   <li>{@code appium.noReset} — clear app data each session (default false = fresh onboarding)</li>
 * </ul>
 */
public final class AppiumDriverFactory {

    private static final Logger LOG = LogManager.getLogger(AppiumDriverFactory.class);

    private static final ThreadLocal<AndroidDriver> DRIVER = new ThreadLocal<>();

    /** One embedded Appium server for the whole JVM (started lazily, stopped on shutdown). */
    private static AppiumDriverLocalService service;

    private AppiumDriverFactory() {
        // static factory
    }

    /** @return the {@link AndroidDriver} bound to the current thread (null if none). */
    public static AndroidDriver getDriver() {
        return DRIVER.get();
    }

    /** Starts the embedded Appium server up-front (no-op if autostart is disabled). Call once. */
    public static void startServer() {
        if (ConfigReader.getBoolean("appium.autostart", true)) {
            startEmbeddedServer();
        }
    }

    /** Stops the embedded Appium server, if this process started one. */
    public static synchronized void stopServer() {
        if (service != null && service.isRunning()) {
            service.stop();
        }
        service = null;
    }

    /** Builds capabilities, ensures a server is up, and returns a fresh session. */
    public static AndroidDriver createDriver() {
        URL serverUrl = resolveServerUrl();

        UiAutomator2Options options = new UiAutomator2Options()
                .setPlatformName(ConfigReader.get("appium.platformName", "Android"))
                .setAutomationName(ConfigReader.get("appium.automationName", "UiAutomator2"))
                .setDeviceName(ConfigReader.get("appium.deviceName", "emulator-5554"))
                .setUdid(ConfigReader.get("appium.udid", "emulator-5554"))
                .setAppPackage(ConfigReader.get("app.package", "com.trimio.trimio"))
                .setAppActivity(ConfigReader.get("app.activity", ".MainActivity"))
                // noReset=false => clear app data each session: every run starts fresh at
                // onboarding, forcing the registration/login flow with no stale session.
                .setNoReset(ConfigReader.getBoolean("appium.noReset", false))
                .setAutoGrantPermissions(true)
                .setNewCommandTimeout(Duration.ofSeconds(
                        ConfigReader.getInt("appium.newCommandTimeout", 120)));
        // Flutter's first frame after a cold start can land on any activity.
        options.setAppWaitActivity("*");

        String udid = ConfigReader.get("appium.udid", "emulator-5554");
        grantRuntimePermissions(udid);
        seedLocation(udid);

        LOG.info("Starting AndroidDriver on {} (app {}/{})",
                serverUrl, options.getAppPackage().orElse("?"), options.getAppActivity().orElse("?"));

        AndroidDriver driver = new AndroidDriver(serverUrl, options);
        // Use explicit waits everywhere; no implicit wait to avoid compounding delays.
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        relaxIdleWaiting(driver);
        DRIVER.set(driver);
        return driver;
    }

    /**
     * Opens a session against a <em>named</em> device, outside the thread-local single-session
     * model above.
     *
     * <p><b>Why a second entry point.</b> {@link #createDriver()} takes its target from
     * configuration and stores the result in a {@link ThreadLocal}, which is exactly right for the
     * suites where "the device" is a singular thing. It cannot express the case this method
     * exists for: one test driving <em>two</em> devices at once, because the behaviour under test
     * only happens between them. An on-demand booking is a conversation — a client asks, a
     * professional is offered the job, the professional accepts, the client sees a pro on the way
     * — and no single-device test can observe more than one side of it. Splitting it across two
     * runs proves neither direction, because the interesting failures are the ones where the
     * message does not arrive.
     *
     * <p><b>systemPort is not optional.</b> UiAutomator2 opens a local port per session to talk to
     * its on-device server, and the default is the same number for every session. Two concurrent
     * sessions that both take it do not fail cleanly: the second attaches to the first device's
     * server, so commands aimed at the professional's phone are executed on the client's. That
     * presents as a bafflingly wrong screen rather than as a port conflict, which is why each
     * caller passes its own.
     *
     * <p>The returned driver is <b>not</b> registered in the ThreadLocal and is not closed by
     * {@link #quitDriver()} — the caller owns it and must quit it.
     *
     * @param udid       the device, e.g. {@code emulator-5554}
     * @param systemPort a port unique to this session (see above)
     */
    public static AndroidDriver createDriverFor(String udid, int systemPort) {
        URL serverUrl = resolveServerUrl();

        UiAutomator2Options options = new UiAutomator2Options()
                .setPlatformName(ConfigReader.get("appium.platformName", "Android"))
                .setAutomationName(ConfigReader.get("appium.automationName", "UiAutomator2"))
                .setDeviceName(udid)
                .setUdid(udid)
                .setSystemPort(systemPort)
                .setAppPackage(ConfigReader.get("app.package", "com.trimio.trimio"))
                .setAppActivity(ConfigReader.get("app.activity", ".MainActivity"))
                // noReset=TRUE here, unlike the single-device default. A paired run needs both
                // apps signed in as different people for the whole test; clearing app data would
                // drop each session back to onboarding and there would be nothing to pair.
                .setNoReset(ConfigReader.getBoolean("appium.pairedNoReset", true))
                .setAutoGrantPermissions(true)
                // MUCH longer than the single-device default, and it is not padding. In a paired
                // test exactly one session is being driven at any moment and the other is idle by
                // construction — the professional's phone receives no command for as long as the
                // client takes to walk a booking flow, and vice versa. Appium measures its command
                // timeout per session, so the ordinary 300s killed the professional's session while
                // the client was still choosing a service. The symptom is brutal: "Ending session,
                // cause was 'New Command Timeout of 300 seconds expired'" on the device the test
                // had not touched yet, so the failure lands on the NEXT command sent to it and
                // looks like the app crashed.
                .setNewCommandTimeout(Duration.ofSeconds(
                        ConfigReader.getInt("appium.pairedCommandTimeout", 1800)));
        options.setAppWaitActivity("*");

        grantRuntimePermissions(udid);
        seedLocation(udid);

        LOG.info("Starting AndroidDriver on {} for device {} (systemPort {})",
                serverUrl, udid, systemPort);
        AndroidDriver driver = new AndroidDriver(serverUrl, options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        relaxIdleWaiting(driver);
        return driver;
    }

    /**
     * Stops UiAutomator waiting for an idle screen that never comes.
     *
     * <p><b>The Trimio client Home feed animates continuously.</b> It carries a marquee banner
     * ("4 visits to review — tap to see them", "Next one tomorrow at 2:30 PM") that scrolls
     * forever, so the accessibility framework never observes an idle window. UiAutomator's default
     * behaviour is to wait for one before every single command, up to 10 seconds, and then proceed
     * anyway — which turns each interaction into a ten-second pause and a screen dump into a flat
     * failure. Confirmed from adb, where {@code uiautomator dump} on that screen answers
     * {@code ERROR: could not get idle state.}
     *
     * <p>That cost is not theoretical: client journeys were taking five to six minutes each, and
     * locators were timing out on controls that were plainly on screen, because the 25-second
     * budget was being spent waiting for quiescence rather than looking.
     *
     * <p>100 ms is the usual remedy — long enough to let a settling frame land, short enough that
     * a permanently animating screen costs nothing. Applied as a session setting, so it survives
     * for the whole session and needs no change in any page object.
     */
    /**
     * Grants the runtime permissions the app asks for, before the session opens.
     *
     * <p>{@code autoGrantPermissions} covers a fresh INSTALL and nothing after it. Clearing app
     * data — which {@code PairedDeviceTest.signIn} does deliberately, to get past a session left
     * signed in as another role — revokes every grant, and the next cold start comes up behind
     * {@code GrantPermissionsActivity}.
     *
     * <p>That is not merely in the way, it fails session creation outright: the foreground
     * activity belongs to {@code com.android.permissioncontroller}, so UiAutomator2 decides the
     * app never started and answers "Cannot start the 'com.trimio.trimio' application". The paired
     * suite runs {@code noReset=true} and therefore inherits the cleared state from the previous
     * run, which is why all four of its tests skipped on a device where the app launched perfectly
     * by hand.
     *
     * <p>Granting up front is better than dismissing the dialog afterwards, because by the time
     * the dialog is dismissable the session has already failed to open.
     *
     * @param udid the target device.
     */
    public static void grantRuntimePermissions(String udid) {
        if (!ConfigReader.getBoolean("device.grantPermissions.enabled", true)) {
            return;
        }
        if (udid == null || udid.isBlank()) {
            return;
        }
        String pkg = ConfigReader.get("app.package", "com.trimio.trimio");
        for (String permission : ConfigReader.get("device.grantPermissions",
                "android.permission.POST_NOTIFICATIONS,"
                        + "android.permission.ACCESS_FINE_LOCATION,"
                        + "android.permission.ACCESS_COARSE_LOCATION").split(",")) {
            String name = permission.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                Process p = new ProcessBuilder("adb", "-s", udid, "shell", "pm", "grant", pkg, name)
                        .redirectErrorStream(true)
                        .start();
                if (!p.waitFor(10, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException | java.io.IOException e) {
                // Best-effort: a permission this build does not declare is refused, which is fine.
                LOG.debug("pm grant {} on {}: {}", name, udid, e.getMessage());
            }
        }
    }

    /**
     * Pushes a GPS fix to an emulator before the session opens.
     *
     * <p>Style-Me-Now needs a location: without one the app raises a manual-address dialog instead
     * of the flow, and the tests that drive it skip with "the Style-Me-Now flow did not open from
     * its 'Style Me Now' CTA".
     *
     * <p><b>Why per session, rather than once before the run.</b> {@code adb emu geo fix} sets a
     * SINGLE fix, not a standing location, and it does not survive a long run: set once at the
     * start of a 97-minute regression, all four Style-Me-Now tests skipped for want of a location
     * they had had at the beginning. A session is created per test here, so this puts the fix at
     * most a test away from the flow that reads it — which is both more reliable than a timer and
     * cheaper than one, since it costs a single adb call per session and no background thread.
     *
     * <p>Best-effort and quiet about it. A device that will not take a fix is not a reason to fail
     * a session — the tests that need it already skip with a message naming exactly this.
     *
     * @param udid the target device; ignored unless it is an emulator, since {@code emu} is the
     *     emulator console and a physical phone has a real GPS.
     */
    private static void seedLocation(String udid) {
        if (!ConfigReader.getBoolean("device.geoFix.enabled", true)) {
            return;
        }
        if (udid == null || !udid.startsWith("emulator-")) {
            return;
        }
        // Longitude first: `geo fix <lon> <lat>`. Reversed puts the client in the Indian Ocean,
        // where every professional is correctly out of range.
        String lon = ConfigReader.get("device.geoFix.lon", "-96.7970");
        String lat = ConfigReader.get("device.geoFix.lat", "32.7767");
        try {
            Process p = new ProcessBuilder("adb", "-s", udid, "emu", "geo", "fix", lon, lat)
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                LOG.warn("geo fix on {} timed out", udid);
                return;
            }
            if (p.exitValue() == 0) {
                LOG.debug("geo fix on {}: {},{}", udid, lat, lon);
            } else {
                LOG.warn("geo fix on {} exited {}", udid, p.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | java.io.IOException e) {
            LOG.warn("Could not set a geo fix on {}: {}", udid, e.getMessage());
        }
    }

    private static void relaxIdleWaiting(AndroidDriver driver) {
        try {
            driver.setSetting("waitForIdleTimeout",
                    ConfigReader.getInt("appium.waitForIdleTimeout", 100));
            driver.setSetting("actionAcknowledgmentTimeout",
                    ConfigReader.getInt("appium.actionAcknowledgmentTimeout", 100));
        } catch (RuntimeException e) {
            // Not fatal: without it the suite is slow, not wrong.
            LOG.warn("Could not relax UiAutomator idle waiting: {}", e.getMessage());
        }
    }


    /** Quits the session for the current thread and clears the ThreadLocal. */
    public static void quitDriver() {
        AndroidDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
            } catch (RuntimeException e) {
                LOG.warn("Error quitting AndroidDriver: {}", e.getMessage());
            } finally {
                DRIVER.remove();
            }
        }
    }

    // ---- server management --------------------------------------------------

    private static URL resolveServerUrl() {
        if (ConfigReader.getBoolean("appium.autostart", true)) {
            try {
                return startEmbeddedServer();
            } catch (RuntimeException e) {
                LOG.warn("Embedded Appium start failed ({}); falling back to appium.url", e.getMessage());
            }
        }
        return toUrl(ConfigReader.get("appium.url", "http://127.0.0.1:4723"));
    }

    private static synchronized URL startEmbeddedServer() {
        if (service != null && service.isRunning()) {
            return service.getUrl();
        }
        AppiumServiceBuilder builder = new AppiumServiceBuilder()
                .withIPAddress("127.0.0.1")
                .usingPort(4723)
                .withArgument(GeneralServerFlag.RELAXED_SECURITY)
                .withArgument(GeneralServerFlag.LOG_LEVEL, "warn");

        String nodePath = ConfigReader.get("appium.nodePath", "/usr/local/bin/node");
        String jsPath = ConfigReader.get("appium.jsPath",
                System.getProperty("user.home") + "/.npm-global/lib/node_modules/appium/index.js");
        if (new File(nodePath).exists()) {
            builder.usingDriverExecutable(new File(nodePath));
        }
        if (new File(jsPath).exists()) {
            builder.withAppiumJS(new File(jsPath));
        }

        service = builder.build();
        service.start();
        LOG.info("Embedded Appium server started at {}", service.getUrl());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (service != null && service.isRunning()) {
                service.stop();
            }
        }));
        return service.getUrl();
    }

    private static URL toUrl(String url) {
        try {
            return new URL(url);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid appium.url: " + url, e);
        }
    }
}
