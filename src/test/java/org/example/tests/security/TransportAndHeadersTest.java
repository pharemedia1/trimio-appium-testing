package org.example.tests.security;

import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * SEC-07 — the response headers, CORS, and the staff-only web-portal rule at the protocol level.
 *
 * <p>These are the controls that live between the client and the handler, so they are invisible to
 * every test that goes through the app: the app cannot omit an {@code Origin}, a browser cannot
 * add one, and neither can read the headers that decide whether a page may frame Trimio or run an
 * injected script.
 *
 * <p>The web-portal rule is the one with real product consequence. Trimio's browser portal is for
 * admin, support and vendor only, and clients and professionals are blocked <em>twice</em> —
 * client-side in {@code utils/web_portal_access.dart} and server-side in
 * {@code services/webPortalAccess.js}. The server decides a request is "from a browser" purely
 * from its {@code Origin} / {@code Sec-Fetch-Site} headers, which makes this the only layer where
 * both halves of the rule can be stated: a browser-shaped client login must be refused, and the
 * same credentials from a native-shaped request must still work. A block that catches the mobile
 * app is worse than the leak it prevents.
 */
public class TransportAndHeadersTest extends ApiBaseTest {

    private static final String PORTAL_ORIGIN = "http://localhost:8080";
    private static final String HOSTILE_ORIGIN = "https://trimio-phish.example.com";

    @Test(description = "SEC-070: responses carry the standard hardening headers")
    public void responsesCarryHardeningHeaders() {
        Map<String, String> headers = headersOf("/health");
        SoftAssert soft = new SoftAssert();

        soft.assertTrue(headers.containsKey("x-content-type-options"),
                "X-Content-Type-Options is absent — a response can be MIME-sniffed into a script.");
        soft.assertEquals(headers.getOrDefault("x-content-type-options", ""), "nosniff",
                "X-Content-Type-Options should be 'nosniff'.");
        soft.assertTrue(headers.containsKey("x-frame-options")
                        || headers.getOrDefault("content-security-policy", "").contains("frame-ancestors"),
                "Neither X-Frame-Options nor a CSP frame-ancestors directive is set — the portal "
                        + "can be framed, which is what clickjacking needs.");
        soft.assertTrue(headers.containsKey("strict-transport-security"),
                "Strict-Transport-Security is absent — a first request can be downgraded to HTTP.");
        soft.assertTrue(headers.containsKey("content-security-policy"),
                "No Content-Security-Policy.");
        soft.assertFalse(headers.containsKey("x-powered-by"),
                "X-Powered-By is present (" + headers.get("x-powered-by") + ") — it names the "
                        + "framework and version to anyone who asks.");
        soft.assertAll();
    }

    /**
     * SEC-071 — CORS must not hand an arbitrary web page the right to read Trimio's API responses.
     *
     * <p>Observed on 2026-09-21: the preflight reflects whatever {@code Origin} it is given, with
     * no {@code Access-Control-Allow-Credentials}. The missing credentials flag is what keeps this
     * from being serious — Trimio authenticates with a bearer token the attacker's page cannot
     * read, not with a cookie the browser would attach automatically — so a hostile page gets the
     * unauthenticated view of the API and nothing more.
     *
     * <p>It is still worth a test, because the day anything here moves to cookie auth, a reflected
     * origin turns straight into cross-site read access. So: reflection is tolerated, reflection
     * <em>with credentials</em> is not.
     */
    @Test(description = "SEC-071: CORS does not grant a hostile origin credentialed access")
    public void corsDoesNotGrantCredentialedCrossOriginAccess() {
        Map<String, String> headers = preflightHeaders("/auth/login", HOSTILE_ORIGIN);
        String allowOrigin = headers.getOrDefault("access-control-allow-origin", "");
        String allowCredentials = headers.getOrDefault("access-control-allow-credentials", "");

        LOG.info("SEC-071: preflight from {} -> ACAO='{}' ACAC='{}'",
                HOSTILE_ORIGIN, allowOrigin, allowCredentials);

        boolean reflectsHostileOrigin =
                HOSTILE_ORIGIN.equals(allowOrigin) || "*".equals(allowOrigin);
        if (reflectsHostileOrigin) {
            Assert.assertNotEquals(allowCredentials, "true",
                    "CROSS-ORIGIN: the preflight reflects an arbitrary Origin (" + allowOrigin
                            + ") AND allows credentials. Any page on the internet can then make "
                            + "authenticated requests as a signed-in Trimio user and read the "
                            + "replies.");
            LOG.warn("SEC-071: CORS reflects any Origin. Tolerated only because Trimio "
                    + "authenticates with a bearer token rather than a cookie — revisit this the "
                    + "moment any endpoint accepts cookie auth.");
        }
    }

    /**
     * SEC-072 — a client is refused the browser portal, and is NOT refused from the app.
     *
     * <p>Both halves in one test on purpose. Each is trivially satisfiable alone — refuse
     * everybody and the first passes; refuse nobody and the second does — and the failure that
     * actually costs something is the one where a well-meaning tightening of the block starts
     * catching the mobile app, locking every client out of Trimio entirely.
     */
    @Test(description = "SEC-072: clients are blocked from the browser portal but not from the app")
    public void clientIsBlockedFromTheBrowserButNotTheApp() {
        String email = api.emailOf(TrimioApi.CLIENT);
        String password = org.example.data.TestAccounts.passwordFor(TrimioApi.CLIENT);
        if (email.isBlank() || password.isBlank()) {
            throw new org.testng.SkipException("No client account configured.");
        }

        ApiClient.Response fromBrowser = api.raw().loginAsBrowser(email, password, PORTAL_ORIGIN);
        if (fromBrowser.status() == 429 || fromBrowser.status() == 423) {
            throw new org.testng.SkipException("The login is rate-limited or the account is locked "
                    + "(" + fromBrowser.status() + ") — re-run after the window. A refusal for "
                    + "that reason says nothing about the portal rule.");
        }
        Assert.assertEquals(fromBrowser.status(), 403,
                "WEB PORTAL: a client login carrying a browser Origin returned "
                        + fromBrowser.status() + " instead of 403. The portal is admin/support/"
                        + "vendor only and the server is the half that cannot be bypassed. Body: "
                        + fromBrowser.body());
        Assert.assertEquals(fromBrowser.errorCode(), "WEB_PORTAL_NOT_AVAILABLE",
                "The browser login was refused, but not with WEB_PORTAL_NOT_AVAILABLE — the app "
                        + "shows specific copy for this case. Body: " + fromBrowser.body());

        ApiClient.Response fromApp = api.raw().loginAsNativeClient(email, password);
        Assert.assertNotEquals(fromApp.status(), 403,
                "REGRESSION: the same client credentials were refused 403 from a NATIVE-shaped "
                        + "request (no Origin, no fetch metadata). The portal block is catching "
                        + "the mobile app, which locks every client out of Trimio — worse than "
                        + "the leak it prevents. Body: " + fromApp.body());
    }

    /**
     * SEC-073 — staff are admitted to the portal from the browser.
     *
     * <p>The other side of SEC-072: the block must be about the role, not about the browser. An
     * over-broad rule that refused every browser request would pass SEC-072 completely.
     */
    @Test(description = "SEC-073: staff roles can sign in to the portal from a browser")
    public void staffCanSignInFromTheBrowser() {
        SoftAssert soft = new SoftAssert();
        for (String role : List.of(TrimioApi.ADMIN, TrimioApi.SUPER_ADMIN, TrimioApi.VENDOR)) {
            if (!api.has(role)) {
                continue;
            }
            ApiClient.Response response = api.raw().loginAsBrowser(
                    api.emailOf(role), org.example.data.TestAccounts.passwordFor(role),
                    PORTAL_ORIGIN);
            if (response.status() == 429 || response.status() == 423) {
                LOG.warn("SEC-073: '{}' login was rate-limited ({}) — not asserted", role,
                        response.status());
                continue;
            }
            soft.assertNotEquals(response.status(), 403,
                    "WEB PORTAL LOCKOUT: '" + role + "' is a portal role and was refused 403 from "
                            + "a browser. Body: " + response.body());
        }
        soft.assertAll();
    }

    // ---- helpers ------------------------------------------------------------

    /** Lower-cased response headers for a GET — the JDK client preserves case, HTTP does not care. */
    private Map<String, String> headersOf(String path) {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            return lowerCased(response);
        } catch (Exception e) {
            throw new IllegalStateException("Header probe of " + path + " failed: "
                    + e.getMessage(), e);
        }
    }

    /** Response headers for a CORS preflight ({@code OPTIONS} with the Origin a browser sends). */
    private Map<String, String> preflightHeaders(String path, String origin) {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type,authorization")
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            return lowerCased(response);
        } catch (Exception e) {
            throw new IllegalStateException("Preflight probe of " + path + " failed: "
                    + e.getMessage(), e);
        }
    }

    private static Map<String, String> lowerCased(HttpResponse<?> response) {
        return response.headers().map().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().toLowerCase(java.util.Locale.ROOT),
                        e -> String.join(", ", e.getValue()),
                        (a, b) -> a));
    }

    private static String baseUrl() {
        return org.example.config.ConfigReader.get("api.baseUrl", "http://localhost:3000")
                .replaceAll("/+$", "");
    }
}
