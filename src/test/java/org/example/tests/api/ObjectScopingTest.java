package org.example.tests.api;

import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * API-02 / SEC-08 — broken object-level authorization (IDOR): whether naming someone else's id
 * gets you their data.
 *
 * <p>This is the vulnerability class that survives a perfect role matrix. Every route here already
 * refuses the wrong <em>role</em>; the question is whether it distinguishes one client from
 * another. Trimio's routes are full of caller-supplied identifiers —
 * {@code /reports/client/:clientId}, {@code ?userId=}, {@code ?professionalId=} — and each one is
 * either a parameter the server trusts or a leftover the server ignores. Both look identical from
 * the outside, and only a request that supplies a <em>different</em> id can tell them apart.
 *
 * <p><b>The method.</b> Call the same route twice as the same caller, once naming their own id and
 * once naming another user's, and compare the responses byte for byte. Identical means the
 * parameter is inert and the server resolved the caller from the token. Different means the
 * parameter reached the query — which is the finding, whether or not the data returned happens to
 * be sensitive today.
 *
 * <p>Comparing against a control rather than asserting a status is what makes this reliable: a
 * route that returns 200 with the caller's own data for any id is secure, and a naive "assert 403"
 * would report it as a defect.
 *
 * <p>Verified on 2026-09-21: the routes below all resolve from the token
 * ({@code resolveScopedClientId} in {@code reportController}, and the equivalents elsewhere), and
 * {@code /userprofile/getUserProfile} goes further and answers an explicit
 * {@code 403 "You can only access your own profile."}
 */
public class ObjectScopingTest extends ApiBaseTest {

    /**
     * A real user id that is NOT the test client's.
     *
     * <p>Has to be a genuine row, or every route answers "not found" and the comparison proves
     * nothing about scoping. 41507 is the seeded second client ({@code trimiotest+client2}), which
     * has reports, addresses and a profile — exactly the data an IDOR would expose.
     */
    private static final String OTHER_USER_ID = "41507";

    /**
     * Routes that take an identifier the caller could tamper with.
     *
     * <p>Each row is {@code {description, ownPath, spoofedPath}}. The pair is written out rather
     * than templated so that each one names the parameter it is actually attacking — they are not
     * all spelled the same way, and a route that reads {@code clientId} while the test sends
     * {@code userId} would pass for the wrong reason.
     */
    @DataProvider(name = "scopedRoutes")
    public Object[][] scopedRoutes() {
        return new Object[][]{
                {"support reports (path segment)",
                        "/reports/client/41501", "/reports/client/" + OTHER_USER_ID},
                {"report statistics (path segment)",
                        "/reports/stats/41501", "/reports/stats/" + OTHER_USER_ID},
                {"saved addresses (query)",
                        "/api/user-addresses/", "/api/user-addresses/?userId=" + OTHER_USER_ID},
                {"family members (query)",
                        "/api/family-members/", "/api/family-members/?userId=" + OTHER_USER_ID},
                {"membership overview (query)",
                        "/memberships/overview", "/memberships/overview?userId=" + OTHER_USER_ID},
                {"account credit (query)",
                        "/memberships/account-credit",
                        "/memberships/account-credit?userId=" + OTHER_USER_ID},
                {"store cart (query)",
                        "/store/client/cart", "/store/client/cart?userId=" + OTHER_USER_ID},
                {"store orders (query)",
                        "/store/client/orders", "/store/client/orders?userId=" + OTHER_USER_ID},
                {"notifications (query)",
                        "/notifications/", "/notifications/?userId=" + OTHER_USER_ID},
                {"pending actions (query)",
                        "/appointment/pending-actions",
                        "/appointment/pending-actions?userId=" + OTHER_USER_ID},
        };
    }

    @Test(dataProvider = "scopedRoutes",
            description = "API-020: naming another user's id does not change what is returned")
    public void routesIgnoreACallerSuppliedUserId(String what, String ownPath, String spoofedPath) {
        TrimioApi.Caller client = api.as(TrimioApi.CLIENT);

        ApiClient.Response own = client.get(ownPath);
        ApiClient.Response spoofed = client.get(spoofedPath);

        SoftAssert soft = new SoftAssert();
        // A refusal is a perfectly good answer — better than an inert parameter, in fact, because
        // it says the server noticed. Only compare bodies when the spoofed call succeeded.
        if (spoofed.status() == 401 || spoofed.status() == 403) {
            LOG.info("API-020 {}: refused outright ({}), which is the strongest form of scoping",
                    what, spoofed.status());
            soft.assertAll();
            return;
        }

        soft.assertEquals(spoofed.status(), own.status(),
                "BROKEN OBJECT-LEVEL AUTHORIZATION (" + what + "): " + spoofedPath + " answered "
                        + spoofed.status() + " where the caller's own id answered " + own.status()
                        + ". The identifier is reaching the handler rather than being ignored in "
                        + "favour of the token.");
        soft.assertEquals(spoofed.body(), own.body(),
                "BROKEN OBJECT-LEVEL AUTHORIZATION (" + what + "): the response changed when the "
                        + "caller named user " + OTHER_USER_ID + " instead of themselves. This "
                        + "route returns one user's data to another. Own response: "
                        + truncate(own.body()) + "  |  spoofed: " + truncate(spoofed.body()));
        soft.assertAll();
    }

    /**
     * API-021 — the profile route, which refuses rather than ignoring.
     *
     * <p>Called out separately because it takes the stricter of the two correct approaches:
     * instead of quietly substituting the caller's own id it answers
     * {@code 403 "You can only access your own profile."} That is better, and it is worth pinning
     * so a later refactor towards "just ignore the parameter" is a visible decision rather than an
     * accident.
     */
    @Test(description = "API-021: the profile route refuses another user's id outright")
    public void profileRouteRefusesAnotherUsersId() {
        ApiClient.Response response = api.as(TrimioApi.CLIENT)
                .get("/userprofile/getUserProfile?userId=" + OTHER_USER_ID);

        org.testng.Assert.assertTrue(response.status() == 403 || response.status() == 401,
                "/userprofile/getUserProfile answered " + response.status() + " when asked for "
                        + "another user's profile. It used to refuse with 403 'You can only access "
                        + "your own profile.' — if that refusal has been replaced by silently "
                        + "returning the caller's own profile the test should be relaxed "
                        + "deliberately; if it now RETURNS the other profile, this is an IDOR. "
                        + "Body: " + truncate(response.body()));
    }

    /**
     * API-022 — a professional cannot read a client's records by asking as themselves.
     *
     * <p>The cross-role half. The matrix proves a professional is refused routes reserved to other
     * tiers; this proves that on the routes they <em>are</em> allowed, they see their own data and
     * not a client's.
     */
    @Test(description = "API-022: a professional naming a client's id gets their own scope")
    public void professionalCannotReadAClientsRecordsById() {
        if (!api.has(TrimioApi.PROFESSIONAL)) {
            throw new org.testng.SkipException("No 'professional' account configured.");
        }
        TrimioApi.Caller pro = api.as(TrimioApi.PROFESSIONAL);
        SoftAssert soft = new SoftAssert();

        for (String path : new String[]{"/reports/client/41501", "/reports/stats/41501"}) {
            ApiClient.Response asPro = pro.get(path);
            ApiClient.Response asClient = api.as(TrimioApi.CLIENT).get(path);
            if (asPro.status() == 401 || asPro.status() == 403) {
                continue;
            }
            soft.assertNotEquals(asPro.body(), asClient.body(),
                    "CROSS-ROLE IDOR: " + path + " returned the SAME body to a professional as to "
                            + "the client who owns those records. Each caller should see their own "
                            + "scope. Body: " + truncate(asPro.body()));
        }
        soft.assertAll();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<empty>";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }
}
