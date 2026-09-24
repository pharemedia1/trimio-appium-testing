package org.example.base;

import io.appium.java_client.android.AndroidDriver;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.config.ConfigReader;
import org.example.data.TestAccounts;
import org.example.factory.AppiumDriverFactory;
import org.example.pages.mobile.LoginScreen;
import org.example.pages.mobile.OnboardingScreen;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.professional.ProfessionalDashboardScreen;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * Base for tests that drive <b>two emulators at once</b> — a client on one, a professional on the
 * other — because the behaviour under test happens <em>between</em> them.
 *
 * <p><b>Why this cannot be done one device at a time.</b> Trimio's on-demand booking is a
 * conversation, not a form: the client dispatches a request, the matching service scores and
 * offers it to nearby professionals, one accepts, and only then does the client's screen change to
 * "Your pro is on the way". Every interesting failure in that sequence is a failure of <em>one
 * side to hear the other</em> — an offer that never arrives, an acceptance the client never sees,
 * two professionals who both think they got the job. A single-device test cannot observe any of
 * them: it sees a request go out and a spinner, and has no way to tell "nobody was free" from
 * "the offer was never delivered". Those are the same screen and completely different defects.
 *
 * <p>So this base holds both sessions open simultaneously, and the tests assert on the causal link:
 * an action on one device produces a visible consequence on the other.
 *
 * <h2>What it needs, and what it does when it is not there</h2>
 * Two emulators, each with the Trimio app, each signed in as a different role. The pairing is
 * conventional in this project and matches {@code ~/StudioProjects/trimio/CLAUDE.md}:
 * {@code emulator-5554} runs the client, {@code emulator-5556} the professional. Both are
 * overridable ({@code -Ddevices.client=…}, {@code -Ddevices.professional=…}).
 *
 * <p>When the second device is absent the class <b>skips</b> rather than failing, with a message
 * naming the missing device — one emulator is an unprovisioned machine, not a defect in Trimio.
 *
 * <h2>Sessions are opened once per class, not per method</h2>
 * Against the single-device convention elsewhere in this framework, and deliberately. Signing two
 * apps in takes the better part of a minute, and — more importantly — a paired test's setup is
 * itself stateful: the professional has to be <em>on duty</em> to be offered anything, and tearing
 * that down between methods would mean re-establishing it each time, with a fresh chance to fail
 * for reasons that have nothing to do with the test.
 */
public abstract class PairedDeviceTest {

    protected static final Logger LOG = LogManager.getLogger(PairedDeviceTest.class);

    /**
     * UiAutomator2 system ports, one per session.
     *
     * <p>Not cosmetic. Each session runs an instrumentation server on the device and is reached
     * through a forwarded local port; the default is the same number for every session. Two
     * concurrent sessions that both take it do not collide loudly — the second quietly attaches to
     * the first device's server, so every command meant for the professional's phone lands on the
     * client's. The symptom is a screen that makes no sense, not an error.
     */
    private static final int CLIENT_SYSTEM_PORT = 8210;
    private static final int PRO_SYSTEM_PORT = 8211;

    protected AndroidDriver clientDriver;
    protected AndroidDriver proDriver;

    protected String clientDevice;
    protected String proDevice;

    @BeforeClass(alwaysRun = true)
    public void openBothSessions() {
        TestAccounts.load();
        clientDevice = ConfigReader.get("devices.client", "emulator-5554");
        proDevice = ConfigReader.get("devices.professional", "emulator-5556");

        if (clientDevice.equals(proDevice)) {
            throw new SkipException("devices.client and devices.professional are both '"
                    + clientDevice + "'. A paired test needs two devices — the whole point is that "
                    + "one side's action is observed on the other.");
        }

        try {
            AppiumDriverFactory.startServer();
        } catch (RuntimeException e) {
            LOG.warn("Embedded Appium start failed ({}); falling back to appium.url", e.getMessage());
        }

        clientDriver = openOrSkip(clientDevice, CLIENT_SYSTEM_PORT, "client");
        try {
            proDriver = openOrSkip(proDevice, PRO_SYSTEM_PORT, "professional");
        } catch (RuntimeException e) {
            // The first session is already open; leaving it behind would hold the device for the
            // rest of the run and make the NEXT suite's failure look unrelated to this one.
            quitQuietly(clientDriver, clientDevice);
            clientDriver = null;
            throw e;
        }
        // Both apps back to their own landing screen before anything is asserted.
        //
        // The single-device suites get this for free: their driver is created with noReset=false,
        // which clears app data, so every test starts at onboarding. A paired run cannot do that
        // — clearing would sign both apps out and there would be nothing to pair — so whatever
        // screen the previous run left behind is still there. That is not hypothetical: an
        // interrupted run left the client half-way through the Style-Me-Now flow, and the next
        // run's first assertion failed with "did not reach the Home feed" while the app was
        // working perfectly and simply showing a different screen.
        //
        // A cold restart is the cheap, deterministic fix, and it is honest about what it costs:
        // it discards any in-flight state, which is exactly what a fresh class wants.
        relaunch(clientDriver, clientDevice);
        relaunch(proDriver, proDevice);

        LOG.info("==== Paired session up: client on {}, professional on {} ====",
                clientDevice, proDevice);
    }

    /**
     * Clears this app's data and relaunches it, so the device is at onboarding.
     *
     * <p>Stronger than {@link #relaunch}, which only cold-restarts: a persisted session survives a
     * restart and is precisely what this has to get rid of.
     */
    private static void resetToOnboarding(AndroidDriver driver) {
        String pkg = ConfigReader.get("app.package", "com.trimio.trimio");
        try {
            driver.terminateApp(pkg);
            driver.executeScript("mobile: clearApp", Map.of("appId", pkg));
            driver.activateApp(pkg);
            LOG.info("Cleared {} and relaunched it to reach onboarding", pkg);
            // Clearing revokes every runtime grant, so the relaunch comes up behind the
            // permission prompts again — notifications first. autoGrantPermissions does not
            // help: it grants at INSTALL time, and nothing is being installed here. Left
            // unanswered, onboarding is on screen but covered, and the sign-in tap below times
            // out naming a locator that is perfectly correct.
            new OnboardingScreen(driver).allowAllPermissionPrompts();
        } catch (RuntimeException e) {
            // Best-effort. If the app was already at onboarding this changes nothing, and the
            // login-form check immediately below is the real verdict either way.
            LOG.warn("Could not clear app data before signing in: {}", e.getMessage());
        }
    }

    /** Cold-restarts the Trimio app so the device is on its role's landing screen. */
    private static void relaunch(AndroidDriver driver, String device) {
        String pkg = ConfigReader.get("app.package", "com.trimio.trimio");
        try {
            driver.terminateApp(pkg);
            driver.activateApp(pkg);
            LOG.info("Relaunched {} on {}", pkg, device);
        } catch (RuntimeException e) {
            // Not fatal: the app may simply not have been running. The screen assertions that
            // follow are the real check, and they say something more useful than this would.
            LOG.warn("Could not relaunch the app on {}: {}", device, e.getMessage());
        }
    }

    @AfterClass(alwaysRun = true)
    public void closeBothSessions() {
        quitQuietly(clientDriver, clientDevice);
        quitQuietly(proDriver, proDevice);
        clientDriver = null;
        proDriver = null;
    }

    /**
     * The client's Home tab, signing in only if the app has not already done it.
     *
     * <p>Both routes are accepted because both are real: the paired emulators are usually left
     * signed in (a debug build carrying the {@code DEV_AUTOLOGIN_*} defines signs itself in during
     * splash and never shows onboarding), while a freshly installed build lands on the carousel.
     * Making a paired test depend on which APK someone happened to install last would mean it
     * broke for a reason that has nothing to do with on-demand booking.
     */
    protected ClientHomeScreen clientHome() {
        BottomNavBar nav = new BottomNavBar(clientDriver);
        if (!nav.isClientShell()) {
            signIn(clientDriver, "client");
        }
        // The shell being in the tree is not evidence the feed is reachable: the post-login
        // modals leave the bottom nav visible behind their scrim. Answer them either way.
        new LoginScreen(clientDriver).dismissPostLoginModals();

        ClientHomeScreen home = new ClientHomeScreen(clientDriver);
        if (home.isBlockedByProfileGate()) {
            throw new SkipException("The client on " + clientDevice + " has an incomplete profile, "
                    + "so it is pinned to '" + ClientHomeScreen.PROFILE_GATE + "' and cannot reach "
                    + "booking at all. Give " + TestAccounts.emailFor("client") + " a name and "
                    + "address.");
        }
        return home;
    }

    /** The professional's dashboard, signing in only if the app has not already done it. */
    protected ProfessionalDashboardScreen professionalDashboard() {
        ProfessionalDashboardScreen dashboard = new ProfessionalDashboardScreen(proDriver);
        if (!dashboard.isLoaded() && !dashboard.isProfileIncomplete()) {
            signIn(proDriver, "professional");
            new LoginScreen(proDriver).dismissPostLoginModals();
            dashboard = new ProfessionalDashboardScreen(proDriver);
        }
        if (dashboard.isProfileIncomplete()) {
            throw new SkipException("The professional on " + proDevice + " has no approved profile "
                    + "— the app routes it to ProfessionalNotCreatedHomePage, so it can never be "
                    + "offered a job. Approve " + TestAccounts.emailFor("professional") + ".");
        }
        if (!dashboard.isLoaded()) {
            throw new SkipException("The app on " + proDevice + " did not reach the professional "
                    + "dashboard. Check that its account's user_type_id is 2.");
        }
        return dashboard;
    }

    /**
     * Puts the professional on duty, which is the precondition for receiving any offer at all.
     *
     * <p>Stated as a skip rather than an assertion when it cannot be achieved: a professional who
     * is off duty is <em>correctly</em> offered nothing, so a paired test that ran anyway would
     * report "no offer arrived" and mean "the setup did not happen".
     */
    protected ProfessionalDashboardScreen professionalOnDuty() {
        ProfessionalDashboardScreen dashboard = professionalDashboard();
        if (!dashboard.setOnDuty(true)) {
            throw new SkipException("Could not put the professional on " + proDevice + " on duty. "
                    + "Nothing is dispatched to an off-duty professional, so an on-demand test "
                    + "would fail for the wrong reason.");
        }
        return dashboard;
    }

    private void signIn(AndroidDriver driver, String role) {
        String email = TestAccounts.emailFor(role);
        String password = TestAccounts.passwordFor(role);
        if (email.isBlank() || password.isBlank()) {
            throw new SkipException("No '" + role + "' account configured — add roleAccounts."
                    + role + " to test-accounts.json.");
        }
        // We only get here because the shell this role needs is not on screen, and that has two
        // causes which look nothing alike. The app may be at onboarding, which this method can
        // drive. Or it may be signed in as a DIFFERENT role — which is exactly what a preceding
        // single-device suite leaves behind, since those run with noReset=false and the last one
        // to touch this emulator signs in as whoever it was testing. Onboarding never renders for
        // a signed-in app, so driving it in that state waits 30s for a "Sign in" button that
        // cannot appear and then fails as a TimeoutException naming the locator — which reads as
        // a broken selector and sends the next person looking at OnboardingScreen, where nothing
        // is wrong. Clearing this app's data collapses both cases to the one handled below.
        //
        // This is safe in a way that clearing during session bring-up would not be: there, both
        // apps would be signed out with nothing to sign them back in. Here, signing in is the
        // very next thing that happens.
        resetToOnboarding(driver);

        LoginScreen form = new OnboardingScreen(driver).goToLogin();
        if (!form.isLoaded()) {
            throw new SkipException("The login form did not open on this device — it may be on an "
                    + "unexpected screen. Relaunch the app.");
        }
        form.login(email, password);
        LoginScreen.LoginOutcome outcome = form.awaitLoginOutcome();
        if (outcome == LoginScreen.LoginOutcome.RATE_LIMITED) {
            // Same rolling window that RoleSessionTest absorbs, and worth absorbing here too: a
            // paired run needs only two sign-ins, so it is almost always a suite that ran BEFORE
            // it that spent the budget. One wait is much cheaper than losing the only end-to-end
            // coverage of the on-demand handshake.
            long waitSeconds = ConfigReader.getInt("auth.rateLimitCooldownSeconds", 90);
            LOG.warn("Signing in as '{}' was RATE LIMITED, not refused. Waiting {}s and retrying.",
                    role, waitSeconds);
            try {
                Thread.sleep(waitSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            form = new OnboardingScreen(driver).goToLogin();
            form.login(email, password);
            outcome = form.awaitLoginOutcome();
        }
        if (outcome != LoginScreen.LoginOutcome.ACCEPTED) {
            throw new SkipException("Signing in as '" + role + "' (" + email + ") was rejected — "
                    + (outcome == LoginScreen.LoginOutcome.RATE_LIMITED
                       ? "the backend is still rate-limiting sign-ins ('" + LoginScreen.RATE_LIMITED
                         + "'). The account is fine; see backend/middleware/authRateLimit.js."
                       : "the account may be locked, unverified, or the password may have changed."));
        }
        form.dismissPostLoginModals();
    }

    private static AndroidDriver openOrSkip(String udid, int systemPort, String role) {
        try {
            return AppiumDriverFactory.createDriverFor(udid, systemPort);
        } catch (RuntimeException e) {
            throw new SkipException("Could not open a session on '" + udid + "' (the " + role
                    + " device). A paired test needs TWO emulators: boot a second one and install "
                    + "com.trimio.trimio on it, or point -Ddevices." + role + "=… at an existing "
                    + "device. (" + e.getMessage() + ")");
        }
    }

    private static void quitQuietly(AndroidDriver driver, String device) {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (RuntimeException e) {
            LOG.warn("Error quitting the session on {}: {}", device, e.getMessage());
        }
    }
}
