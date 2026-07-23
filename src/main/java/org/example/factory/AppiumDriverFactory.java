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

        LOG.info("Starting AndroidDriver on {} (app {}/{})",
                serverUrl, options.getAppPackage().orElse("?"), options.getAppActivity().orElse("?"));

        AndroidDriver driver = new AndroidDriver(serverUrl, options);
        // Use explicit waits everywhere; no implicit wait to avoid compounding delays.
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        DRIVER.set(driver);
        return driver;
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
