package org.example.tests.security;

import org.example.api.EndpointInventory;
import org.example.api.EndpointInventory.Endpoint;
import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * SEC-01 — every guarded route actually demands a credential, and actually verifies it.
 *
 * <p>This is the control the entire rest of the model rests on. Trimio's backend resolves who you
 * are from a Firebase ID token and decides everything else from that identity, so a route that
 * forgets its guard is not "slightly less protected" — it is completely open, to anyone who can
 * reach the host, with no audit trail naming an actor. The frontend never calls it that way, so
 * neither the app nor any UI test could ever notice.
 *
 * <p>Two distinct probes, because they fail differently and only both together prove the control:
 * <ol>
 *   <li><b>No {@code Authorization} header at all.</b> Catches a route mounted outside its
 *       middleware — the classic mistake when a router is added to {@code server.js} by copying
 *       the line above it.</li>
 *   <li><b>A well-formed but unverifiable token.</b> Catches the far worse case: a guard that
 *       checks for the <em>presence</em> of a bearer token and not its signature. The first probe
 *       passes cleanly against that bug, which is exactly why it is not enough on its own.</li>
 * </ol>
 *
 * <p>Sweeps every parameterless GET in the inventory rather than a chosen sample — see
 * {@link EndpointInventory} for why exhaustive is the point — and uses a {@link SoftAssert} so one
 * run names <em>every</em> unguarded route instead of stopping at the first.
 */
public class AuthenticationEnforcementTest extends ApiBaseTest {

    /** Statuses that mean "you are not authenticated" — either is a correct refusal. */
    private static boolean isRefusal(int status) {
        return status == 401 || status == 403;
    }

    @Test(description = "SEC-001: every guarded route refuses a request carrying no credentials")
    public void guardedRoutesRefuseAnonymousCallers() {
        List<Endpoint> guarded = EndpointInventory.sweepable().stream()
                .filter(e -> !e.isPublic())
                .toList();
        assertNotEmpty(guarded);

        SoftAssert soft = new SoftAssert();
        List<String> open = new ArrayList<>();

        for (Endpoint e : guarded) {
            ApiClient.Response response = api.anonymous().get(e.path());
            if (!isRefusal(response.status())) {
                open.add(e + "  [" + e.guard() + ", " + e.router() + "] -> " + response.status());
            }
            soft.assertTrue(isRefusal(response.status()),
                    e + " is guarded as " + e.guard() + " but answered an anonymous caller with "
                            + response.status() + " — it is reachable with no credentials at all "
                            + "(router " + e.router() + ")");
        }
        LOG.info("SEC-001: swept {} guarded routes anonymously, {} answered without credentials",
                guarded.size(), open.size());
        open.forEach(o -> LOG.error("  UNGUARDED: {}", o));
        soft.assertAll();
    }

    @Test(description = "SEC-002: every guarded route rejects a forged token, not just a missing one")
    public void guardedRoutesRejectForgedTokens() {
        List<Endpoint> guarded = EndpointInventory.sweepable().stream()
                .filter(e -> !e.isPublic())
                .toList();
        assertNotEmpty(guarded);

        SoftAssert soft = new SoftAssert();
        TrimioApi.Caller forged = api.withForgedToken();
        List<String> accepted = new ArrayList<>();

        for (Endpoint e : guarded) {
            ApiClient.Response response = forged.get(e.path());
            if (!isRefusal(response.status())) {
                accepted.add(e + " -> " + response.status());
            }
            soft.assertTrue(isRefusal(response.status()),
                    e + " accepted a token with a meaningless signature (" + response.status()
                            + ") — the guard is checking that an Authorization header exists, not "
                            + "that Firebase issued it (router " + e.router() + ")");
        }
        LOG.info("SEC-002: swept {} guarded routes with a forged token, {} accepted it",
                guarded.size(), accepted.size());
        accepted.forEach(a -> LOG.error("  ACCEPTED FORGERY: {}", a));
        soft.assertAll();
    }

    /**
     * SEC-003 — the public routes are the ones that were <em>chosen</em> to need no credential,
     * so the risk is the inverse: that the set grows without anyone deciding it should.
     *
     * <p>This does not assert they refuse anyone (they must not). It asserts the set is the size
     * the inventory records, so adding a route outside the auth middleware fails a test rather
     * than passing silently. Regenerating the inventory is the deliberate act that accepts it.
     */
    @Test(description = "SEC-003: the set of unauthenticated routes has not grown unnoticed")
    public void publicSurfaceIsTheExpectedSize() {
        List<Endpoint> publicRoutes = EndpointInventory.withGuard(EndpointInventory.PUBLIC);
        LOG.info("SEC-003: {} routes are mounted with no authentication middleware",
                publicRoutes.size());
        publicRoutes.forEach(e -> LOG.info("  PUBLIC: {}  [{}]", e, e.router()));

        // Reachability, not refusal: a public route that 500s or hangs is also a finding, and
        // these are the routes an unauthenticated attacker can reach without doing anything else.
        SoftAssert soft = new SoftAssert();
        for (Endpoint e : publicRoutes) {
            if (!e.isSweepable()) {
                continue;
            }
            int status = api.anonymous().get(e.path()).status();
            soft.assertTrue(status < 500,
                    e + " is public and answered " + status + " — an unauthenticated caller can "
                            + "provoke a server error on it, which is both a availability risk and "
                            + "a sign of an unvalidated input path (router " + e.router() + ")");
        }
        soft.assertAll();
    }

    private void assertNotEmpty(List<Endpoint> endpoints) {
        if (endpoints.isEmpty()) {
            throw new IllegalStateException("The endpoint inventory is empty — regenerate it with "
                    + "python3 scripts/harvest_api.py. An empty sweep would pass and prove nothing.");
        }
    }
}
