package org.example.tests.web;

import org.example.base.WebBaseTest;
import org.example.data.TestAccounts;
import org.example.pages.web.WebLoginPage;
import org.example.pages.web.WebShellPage;
import org.example.utils.ApiClient;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * <b>The staff-only rule.</b> Clients and professionals use the phone apps; the browser portal is for
 * admin, support and vendor only.
 *
 * <p>The rule is enforced twice and this class tests both halves, because each covers for a
 * different failure:
 * <ul>
 *   <li><b>Client side</b> ({@code utils/web_portal_access.dart}) — shows "Use the Trimio app" and,
 *       critically, tears the session down: Firebase sign-out plus removal of
 *       {@code userId/userType/userPhone/userEmail/vendor_id}. Without that teardown a refused login
 *       still leaves a usable session behind, and the next screen treats the person as signed in.</li>
 *   <li><b>Server side</b> ({@code backend/services/webPortalAccess.js}) — refuses the login itself
 *       with <b>403 {@code WEB_PORTAL_NOT_AVAILABLE}</b>, so the block does not depend on the UI
 *       being honest. A browser is recognised by its {@code Origin}/{@code Sec-Fetch-Site} headers,
 *       which is a control rather than a proof — and the code says so.</li>
 * </ul>
 *
 * <p>The header check cuts both ways, so the negative test has a mandatory partner: the same client
 * credentials <em>must still work</em> without those headers. A block that also locks the mobile apps
 * out would be a far worse defect than the one it prevents — which is why
 * {@link #nativeClientLoginStillWorks()} exists.
 */
public class WebPortalAccessTest extends WebBaseTest {

    private static final String BROWSER_ORIGIN = "http://localhost:8080";

    // ---- UI half ------------------------------------------------------------

    @Test(description = "A client is refused at the web portal")
    public void clientIsBlockedFromPortal() {
        requireAccount(RoleNames.CLIENT);

        WebLoginPage login = openPortal();
        login.login(TestAccounts.emailFor(RoleNames.CLIENT), TestAccounts.passwordFor(RoleNames.CLIENT));

        Assert.assertTrue(login.isBlockedFromPortal(),
                "A client signing in through the browser must be refused with the '"
                        + WebLoginPage.BLOCKED_TITLE + "' dialog");
        Assert.assertFalse(new WebShellPage(page).isLoaded(),
                "No portal shell should be reachable for a client");
    }

    @Test(description = "A professional is refused at the web portal")
    public void professionalIsBlockedFromPortal() {
        requireAccount(RoleNames.PROFESSIONAL);

        WebLoginPage login = openPortal();
        login.login(TestAccounts.emailFor(RoleNames.PROFESSIONAL),
                TestAccounts.passwordFor(RoleNames.PROFESSIONAL));

        Assert.assertTrue(login.isBlockedFromPortal(),
                "A professional signing in through the browser must be refused");
        Assert.assertTrue(login.showsProfessionalBlockCopy() || login.isBlockedFromPortal(),
                "The refusal should use the professional-specific copy about schedule, offers and "
                        + "earnings");
    }

    @Test(description = "A refused login leaves no usable session behind")
    public void blockedLoginLeavesNoSession() {
        requireAccount(RoleNames.CLIENT);

        WebLoginPage login = openPortal();
        login.login(TestAccounts.emailFor(RoleNames.CLIENT), TestAccounts.passwordFor(RoleNames.CLIENT));
        Assert.assertTrue(login.isBlockedFromPortal(), "The client should be refused");
        login.acknowledgeBlock();

        Assert.assertTrue(login.leftNoSessionBehind(),
                "After a refusal, reloading must land back on the login form — the deny path signs "
                        + "Firebase out and clears the stored prefs precisely so a half-established "
                        + "session cannot be picked up by a later screen");
    }

    // ---- allowed roles ------------------------------------------------------

    @Test(description = "An admin can sign in to the portal")
    public void adminCanSignIn() {
        WebShellPage shell = signInAs(RoleNames.ADMIN);

        Assert.assertTrue(shell.isAdminShell(),
                "An admin should reach the admin console shell");
    }

    @Test(description = "A vendor can sign in to the portal")
    public void vendorCanSignIn() {
        WebShellPage shell = signInAs(RoleNames.VENDOR);

        Assert.assertTrue(shell.isVendorShell(),
                "A vendor should reach the vendor portal shell");
    }

    // ---- server half --------------------------------------------------------

    @Test(description = "The backend refuses a browser-shaped client login with 403")
    public void backendRefusesBrowserClientLogin() {
        requireAccount(RoleNames.CLIENT);
        ApiClient api = requireBackend();

        ApiClient.Response response = api.loginAsBrowser(
                TestAccounts.emailFor(RoleNames.CLIENT),
                TestAccounts.passwordFor(RoleNames.CLIENT),
                BROWSER_ORIGIN);

        Assert.assertEquals(response.status(), 403,
                "A client login carrying an Origin header must be refused by the server, not just "
                        + "by the UI — otherwise the block is bypassable from a browser console");
        Assert.assertEquals(response.errorCode(), "WEB_PORTAL_NOT_AVAILABLE",
                "The refusal should carry the documented error code");
    }

    @Test(description = "The same client credentials still work from a native client")
    public void nativeClientLoginStillWorks() {
        requireAccount(RoleNames.CLIENT);
        ApiClient api = requireBackend();

        ApiClient.Response response = api.loginAsNativeClient(
                TestAccounts.emailFor(RoleNames.CLIENT),
                TestAccounts.passwordFor(RoleNames.CLIENT));

        Assert.assertNotEquals(response.status(), 403,
                "A login with no Origin/Sec-Fetch-Site header is the mobile app — it must NOT be "
                        + "caught by the portal block. This is the guard against the block "
                        + "accidentally locking every client out of the product.");
        Assert.assertTrue(response.isSuccess(),
                "The native client login should succeed (status was " + response.status() + ")");
    }

    // ---- helpers ------------------------------------------------------------

    private void requireAccount(String role) {
        if (!TestAccounts.hasAccountFor(role)) {
            throw new SkipException("No '" + role + "' account configured — add roleAccounts." + role
                    + " to testdata/mobile/test-accounts.json to run this access-control test.");
        }
    }

    private ApiClient requireBackend() {
        ApiClient api = new ApiClient();
        if (!api.isReachable()) {
            throw new SkipException("The Trimio backend is not reachable — start it (npm start in "
                    + "backend/) and set -Dapi.baseUrl if it is not on http://localhost:3000.");
        }
        return api;
    }

    /** Role keys as stored in {@code test-accounts.json}. */
    private static final class RoleNames {
        static final String CLIENT = "client";
        static final String PROFESSIONAL = "professional";
        static final String ADMIN = "admin";
        static final String VENDOR = "vendor";
    }
}
