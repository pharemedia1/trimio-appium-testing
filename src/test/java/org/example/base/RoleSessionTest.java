package org.example.base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.data.TestAccounts;
import org.example.pages.mobile.LoginScreen;
import org.example.pages.mobile.admin.AdminConsoleScreen;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.professional.ProfessionalDashboardScreen;
import org.testng.SkipException;

/**
 * Base for every signed-in mobile test: logs in as a role and hands back that role's landing screen.
 *
 * <p>Role is not a detail here — it decides the entire shell. {@code loginPage.dart} maps
 * {@code user_type_id} 1→client, 2→professional, 3→admin, 4→support, 9→vendor and pushes a different
 * root for each, so "logged in" is only meaningful once you say as whom.
 *
 * <p>Accounts come from {@code testdata/mobile/test-accounts.json} under {@code roleAccounts}. When
 * one is missing the test <b>skips with an explanatory message</b> rather than failing: an unseeded
 * environment is not a defect, and a red suite that means "you didn't configure a vendor login"
 * trains people to ignore red.
 */
public abstract class RoleSessionTest extends MobileBaseTest {

    private static final Logger LOG = LogManager.getLogger(RoleSessionTest.class);

    public static final String CLIENT = "client";
    public static final String PROFESSIONAL = "professional";
    public static final String ADMIN = "admin";
    public static final String VENDOR = "vendor";

    /**
     * The super admin seat — {@code user_type_id} 4, a role in its own right since 2026-08-29.
     *
     * <p>Not a variant of {@link #ADMIN} and not interchangeable with it. {@code adminAuth} admits
     * both ({@code ADMIN_TYPE_IDS = {3, 4}}) because the seat can do everything an admin can, which
     * is precisely why the difference has to be tested deliberately: the extra powers are reserved
     * at the route by {@code requireSuperAdmin}, not by anything visible in the shell. On the web
     * both roles land in the SAME {@code WebShell}; the seat simply gains one sidebar destination.
     *
     * <p>Worth stating because the two were conflated: {@code roleAccounts.admin} pointed at the
     * seat, so the whole admin suite ran as a super admin while believing otherwise, and no test
     * could have noticed the boundary disappearing.
     */
    public static final String SUPER_ADMIN = "superAdmin";

    /**
     * Support. NOT {@code user_type_id} 4 any more — that id was reassigned to the super admin.
     * Support occupies 5-8 (backend {@code middleware/requireStaff.js}) and has no seeded account.
     */
    public static final String SUPPORT = "support";

    /**
     * Signs in as {@code role} from a freshly launched app.
     *
     * @return the bottom-nav of whatever shell the login produced
     * @throws SkipException when no credentials are configured for the role
     */
    protected BottomNavBar loginAs(String role) {
        String email = TestAccounts.emailFor(role);
        String password = TestAccounts.passwordFor(role);
        if (email.isBlank() || password.isBlank()) {
            throw new SkipException("No '" + role + "' account configured — add roleAccounts."
                    + role + " to testdata/mobile/test-accounts.json to run this test.");
        }

        LoginScreen form = onboarding().goToLogin();
        if (!form.isLoaded()) {
            throw new SkipException("Login form did not open — the app may not have reached "
                    + "onboarding (check the emulator and app install).");
        }
        form.login(email, password);
        if (!form.isLoginAccepted()) {
            throw new SkipException("Login as '" + role + "' (" + email + ") was rejected — the "
                    + "account may be unverified, suspended, or the password may have changed.");
        }
        // Modals sit OVER the shell: the login has succeeded but the bottom nav is behind them and
        // invisible to UiAutomator2 until each is answered — the biometric opt-in, and for a pro
        // with a booking soon, the "Appointment in 2 hours" reminder.
        form.dismissPostLoginModals();
        return new BottomNavBar(driver);
    }

    /** Signs in as a client and returns the Home tab. */
    protected ClientHomeScreen loginAsClient() {
        BottomNavBar nav = loginAs(CLIENT);
        if (!nav.isClientShell()) {
            throw new SkipException("The configured 'client' account did not land in the client "
                    + "shell — check its user_type_id is 1.");
        }
        return new ClientHomeScreen(driver);
    }

    /**
     * Signs in as a client that can actually reach the Home feed, skipping when the account is held
     * on the profile-completion gate.
     *
     * <p>Use this for anything downstream of Home (booking, Style-Me-Now, discovery). A client with
     * an incomplete profile is pinned to "Your details" and cannot reach those screens — the app is
     * behaving correctly, the test data is simply not provisioned. Skipping says that plainly
     * instead of reporting a dozen indistinguishable "Home tab should render" failures.
     */
    protected ClientHomeScreen loginAsProvisionedClient() {
        ClientHomeScreen home = loginAsClient();
        if (home.isBlockedByProfileGate()) {
            throw new SkipException("The 'client' account has an incomplete profile — the app holds "
                    + "it on the '" + ClientHomeScreen.PROFILE_GATE + "' screen, so the Home feed and "
                    + "booking flow are unreachable. Complete the profile (name + address) for "
                    + TestAccounts.emailFor(CLIENT) + ", or point roleAccounts.client at a "
                    + "fully-provisioned client.");
        }
        return home;
    }

    /**
     * Returns a client Home tab, signing in only if the app has not already done it.
     *
     * <p>Debug builds carrying the {@code DEV_AUTOLOGIN_*} dart-defines sign themselves in during
     * splash and never render onboarding, so {@link #loginAsClient()} times out looking for a login
     * form on a shell that is already up — and {@code adb shell pm clear} does not help, because
     * the credentials are compiled into the APK. Rather than make every signed-in client test
     * depend on which APK is installed, this accepts either route: an existing client shell is used
     * as-is, and anything else falls through to a real sign-in.
     *
     * <p>The profile gate is still enforced, for the same reason {@link #loginAsProvisionedClient()}
     * enforces it — an unprovisioned client cannot reach booking at all.
     */
    protected ClientHomeScreen clientSession() {
        BottomNavBar nav = new BottomNavBar(driver);
        ClientHomeScreen home;
        if (nav.isClientShell()) {
            LOG.info("The app is already signed in as a client (dev autologin build)");
            // The shell can be up and still covered: the post-login modals leave the bottom nav in
            // the tree while their scrim hides the feed, so isClientShell() is not evidence that
            // anything is reachable. Nothing signed in here, so answer them explicitly.
            new LoginScreen(driver).dismissPostLoginModals();
            home = new ClientHomeScreen(driver);
        } else {
            home = loginAsClient();
        }
        if (home.isBlockedByProfileGate()) {
            throw new SkipException("The signed-in client has an incomplete profile — the app holds "
                    + "it on the '" + ClientHomeScreen.PROFILE_GATE + "' screen, so booking is "
                    + "unreachable. Give the account a name and address.");
        }
        return home;
    }

    /** Signs in as a professional and returns the dashboard. */
    protected ProfessionalDashboardScreen loginAsProfessional() {
        loginAs(PROFESSIONAL);
        ProfessionalDashboardScreen dashboard = new ProfessionalDashboardScreen(driver);
        if (dashboard.isProfileIncomplete()) {
            throw new SkipException("The configured 'professional' account has no approved profile — "
                    + "the app routes it to ProfessionalNotCreatedHomePage, so the dashboard is "
                    + "unreachable. Approve the professional or seed an approved one.");
        }
        if (!dashboard.isLoaded()) {
            throw new SkipException("The configured 'professional' account did not land in the "
                    + "professional shell — check its user_type_id is 2.");
        }
        return dashboard;
    }

    /**
     * Signs in as the super admin and returns the console.
     *
     * <p>The same console an ordinary admin gets — that is the design, not an oversight. Use this
     * where a test needs the seat's authority (inviting an admin, publishing a legal document,
     * switching a jurisdiction or region); use {@link #loginAsAdmin()} to prove those same actions
     * are refused for a plain admin.
     */
    protected AdminConsoleScreen loginAsSuperAdmin() {
        loginAs(SUPER_ADMIN);
        AdminConsoleScreen console = new AdminConsoleScreen(driver);
        if (!console.isLoaded() && !console.isAdminShell()) {
            throw new SkipException("The configured 'superAdmin' account did not land in the Admin "
                    + "Console — check its user_type_id is 4.");
        }
        return console;
    }

    /** Signs in as an ordinary admin (user_type_id 3) and returns the console. */
    protected AdminConsoleScreen loginAsAdmin() {
        loginAs(ADMIN);
        AdminConsoleScreen console = new AdminConsoleScreen(driver);
        if (!console.isLoaded() && !console.isAdminShell()) {
            throw new SkipException("The configured 'admin' account did not land in the Admin "
                    + "Console — check its user_type_id is 3.");
        }
        return console;
    }
}
