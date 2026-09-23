package org.example.base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.api.TrimioApi;
import org.example.data.TestAccounts;
import org.testng.annotations.BeforeClass;

/**
 * Base for the API, security and performance suites — everything that talks to the backend
 * directly rather than through a device or a browser.
 *
 * <p>These suites are deliberately separate from the UI ones. They need no emulator, no Appium and
 * no served web build, they run in seconds rather than minutes, and they can assert things the UI
 * physically cannot reach: a request with a forged token, a role that has no screen for the route
 * it is attacking, a header the app would never send. Keeping them on their own base means a
 * machine with no Android SDK can still run the whole security sweep.
 *
 * <p>{@link #api} is shared per class and holds the minted Firebase tokens, so a suite of two
 * hundred assertions signs in once per role rather than once per assertion — which matters,
 * because Firebase rate-limits sign-ins and would otherwise start refusing mid-suite.
 */
public abstract class ApiBaseTest {

    protected static final Logger LOG = LogManager.getLogger(ApiBaseTest.class);

    protected final TrimioApi api = new TrimioApi();

    /**
     * Skips the whole class when the backend or the Firebase key is missing.
     *
     * <p>Skip rather than fail, for the reason the UI suites use the same convention: an
     * unprovisioned machine is not a defect, and a red suite that means "you didn't export a key"
     * teaches people to ignore red.
     */
    @BeforeClass(alwaysRun = true)
    public void requireBackend() {
        TestAccounts.load();
        api.requireEnvironment();
        LOG.info("==== API suite: backend reachable, Firebase tokens available ====");
    }
}
