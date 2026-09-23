package org.example.tests.security;

import org.example.api.EndpointInventory;
import org.example.api.EndpointInventory.Endpoint;
import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SEC-02 — the role matrix: for every guarded route, every role that should be refused is refused.
 *
 * <p>Authentication (SEC-01) only establishes <em>who</em> is calling. This is the half that
 * decides what they may do, and it is where the interesting failures live, because a
 * privilege-escalation bug looks like nothing at all from the UI: a client has no screen that
 * calls {@code /admin/all-professionals}, so no amount of clicking the client app will ever
 * discover that the route would have answered them. Only a request the app would never make can
 * find it.
 *
 * <p>Trimio has five privilege tiers and they are genuinely distinct — see
 * {@code backend/middleware/}: {@code firebaseAuth} (anyone signed in), {@code requireProAuth},
 * {@code requireVendor}, {@code requireStaff} (admin, super admin and support tiers 5-8),
 * {@code adminAuth} (user_type_id 3 or 4 only) and {@code requireSuperAdmin} (the single seat).
 * Support is the tier most worth having in the matrix: it is staff, so it passes
 * {@code requireStaff} and reaches the refund queue, and it is not an admin, so
 * {@code adminAuth} must refuse it — a boundary with exactly one account on either side of it.
 *
 * <p>The assertion is deliberately asymmetric. Where a role <b>should be refused</b> the test is
 * strict: anything other than 401/403 fails. Where a role <b>should be admitted</b> it only
 * requires "not 401/403" — a 400 or a 404 from a route that wanted a query parameter is the route
 * doing its job, and demanding a 200 would turn this into a brittle contract test instead of an
 * authorization one.
 */
public class RoleAuthorizationMatrixTest extends ApiBaseTest {

    /** Roles to drive the matrix with, lowest privilege first. */
    private static final String[] ROLES = {
            TrimioApi.CLIENT, TrimioApi.PROFESSIONAL, TrimioApi.VENDOR,
            TrimioApi.SUPPORT, TrimioApi.ADMIN, TrimioApi.SUPER_ADMIN,
    };

    private static boolean isRefusal(int status) {
        return status == 401 || status == 403;
    }

    /** One guard class per row, so a failure names the tier rather than a single route. */
    @DataProvider(name = "guards")
    public Object[][] guards() {
        return new Object[][]{
                {EndpointInventory.PRO},
                {EndpointInventory.VENDOR},
                {EndpointInventory.STAFF},
                {EndpointInventory.ADMIN},
                {EndpointInventory.SUPER_ADMIN},
        };
    }

    @Test(dataProvider = "guards",
            description = "SEC-010: routes at each privilege tier refuse every role below it")
    public void routesRefuseUnderprivilegedRoles(String guard) {
        List<Endpoint> routes = EndpointInventory.sweepable(guard);
        if (routes.isEmpty()) {
            throw new IllegalStateException("No sweepable routes for guard " + guard
                    + " — regenerate the inventory (python3 scripts/harvest_api.py)");
        }

        SoftAssert soft = new SoftAssert();
        Map<String, List<String>> leaks = new LinkedHashMap<>();

        for (String role : ROLES) {
            if (!api.has(role)) {
                LOG.warn("SEC-010: no '{}' account configured — that tier is not being proved "
                        + "against {} routes", role, guard);
                continue;
            }
            boolean shouldPass = EndpointInventory.roleSatisfies(role, guard);
            TrimioApi.Caller caller = api.as(role);

            for (Endpoint e : routes) {
                ApiClient.Response response = caller.get(e.path());
                boolean refused = isRefusal(response.status());

                if (shouldPass) {
                    soft.assertFalse(refused,
                            e + " is guarded as " + guard + " and refused '" + role + "' with "
                                    + response.status() + " — that role is supposed to be admitted, "
                                    + "so this is a lockout, not a leak (router " + e.router() + ")");
                } else {
                    if (!refused) {
                        leaks.computeIfAbsent(role, k -> new ArrayList<>())
                                .add(e + " -> " + response.status());
                    }
                    soft.assertTrue(refused,
                            "PRIVILEGE ESCALATION: " + e + " is guarded as " + guard + " but "
                                    + "answered a '" + role + "' token with " + response.status()
                                    + " — that role is below the tier this route reserves "
                                    + "(router " + e.router() + ")");
                }
            }
        }
        LOG.info("SEC-010 [{}]: swept {} routes × {} roles", guard, routes.size(), ROLES.length);
        leaks.forEach((role, list) ->
                list.forEach(l -> LOG.error("  ESCALATION as {}: {}", role, l)));
        soft.assertAll();
    }

    /**
     * SEC-011 — the tier that is easiest to get wrong, called out on its own.
     *
     * <p>Support is staff and is not an admin. Those two facts are enforced by different
     * middleware, and the id that carries them moved once already: {@code user_type_id} 4 used to
     * mean support and now means super admin, which silently promoted every support account in
     * existence at the moment of the change. A test that only ever compares "client vs admin"
     * cannot see that class of mistake; this one is the reason the support account was seeded
     * into {@code test-accounts.json} at all.
     */
    @Test(description = "SEC-011: support is admitted to staff routes and refused by admin routes")
    public void supportIsStaffButNotAdmin() {
        if (!api.has(TrimioApi.SUPPORT)) {
            throw new org.testng.SkipException("No 'support' account configured — add "
                    + "roleAccounts.support to test-accounts.json (a user_type_id in 5-8) to prove "
                    + "the staff/admin boundary.");
        }
        TrimioApi.Caller support = api.as(TrimioApi.SUPPORT);
        SoftAssert soft = new SoftAssert();

        for (Endpoint e : EndpointInventory.sweepable(EndpointInventory.STAFF)) {
            int status = support.get(e.path()).status();
            soft.assertFalse(isRefusal(status),
                    e + " is a requireStaff route and refused support with " + status
                            + " — support is staff (STAFF_TYPE_IDS includes 5-8), so this locks the "
                            + "support desk out of its own queue");
        }
        for (Endpoint e : EndpointInventory.sweepable(EndpointInventory.ADMIN)) {
            int status = support.get(e.path()).status();
            soft.assertTrue(isRefusal(status),
                    "PRIVILEGE ESCALATION: " + e + " is an adminAuth route and answered a support "
                            + "token with " + status + " — support is not an admin, and the two "
                            + "have shared a user_type_id before");
        }
        soft.assertAll();
    }

    /**
     * SEC-012 — a client token must not reach the professional's money.
     *
     * <p>Singled out from the matrix because of what is behind these particular routes: the pro
     * wallet, the payout schedule and the earnings summary. An authorization slip here is not an
     * information leak in the abstract, it is one user reading another's balance.
     */
    @Test(description = "SEC-012: non-professionals cannot reach professional wallet and payout routes")
    public void clientCannotReachProfessionalMoneyRoutes() {
        SoftAssert soft = new SoftAssert();
        List<Endpoint> proRoutes = EndpointInventory.sweepable(EndpointInventory.PRO);

        for (String role : new String[]{TrimioApi.CLIENT, TrimioApi.VENDOR}) {
            if (!api.has(role)) {
                continue;
            }
            TrimioApi.Caller caller = api.as(role);
            for (Endpoint e : proRoutes) {
                int status = caller.get(e.path()).status();
                soft.assertTrue(isRefusal(status),
                        "PRIVILEGE ESCALATION: " + e + " answered a '" + role + "' token with "
                                + status + " — these routes carry a professional's wallet balance, "
                                + "payout schedule and earnings");
            }
        }
        soft.assertAll();
    }
}
