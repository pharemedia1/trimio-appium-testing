package org.example.api;

import org.example.data.TestAccounts;
import org.example.utils.ApiClient;
import org.example.utils.FirebaseTokens;
import org.testng.SkipException;

import java.util.HashMap;
import java.util.Map;

/**
 * The role-aware façade the API, security and performance suites call the backend through.
 *
 * <p>It exists because "call the API" is not a meaningful instruction for this backend: every
 * protected route resolves the caller from a Firebase ID token and then decides what they may do
 * from {@code users.user_type_id}. The same request is a 200, a 403 or a 404 depending purely on
 * who sent it, so a test has to say <em>as whom</em> — and comparing the answers across roles is
 * how the authorization tests work at all.
 *
 * <p>So the unit here is (role, request): {@code api.as(ADMIN).get("/admin/…")}. Roles map to the
 * seeded accounts in {@code test-accounts.json}, tokens are minted once per role and cached by
 * {@link FirebaseTokens}, and {@link #anonymous()} deliberately sends none.
 *
 * <p><b>Skipping vs failing.</b> A missing role account or a missing Firebase key is an
 * unprovisioned environment, not a defect, so it raises {@link SkipException} with the fix in the
 * message — the same convention as {@code RoleSessionTest}. Firebase <em>refusing</em> known
 * credentials is the opposite: the seeded account has gone or its password changed, which a run
 * should report loudly, so that propagates as a failure.
 */
public final class TrimioApi {

    /** Role keys, matching {@code roleAccounts} in test-accounts.json. */
    public static final String CLIENT = "client";
    public static final String PROFESSIONAL = "professional";
    public static final String ADMIN = "admin";
    public static final String SUPER_ADMIN = "superAdmin";
    public static final String VENDOR = "vendor";
    public static final String SUPPORT = "support";

    /** Every role a suite can iterate over when it needs "any authenticated non-staff caller". */
    public static final String[] NON_STAFF_ROLES = {CLIENT, PROFESSIONAL, VENDOR};

    private final ApiClient http = new ApiClient();

    /** True when the backend answers /health — suites skip rather than fail when it does not. */
    public boolean isBackendUp() {
        return http.isReachable();
    }

    /** Raises the standard skip when the backend or Firebase is missing. Call from {@code @BeforeClass}. */
    public void requireEnvironment() {
        if (!isBackendUp()) {
            throw new SkipException("The Trimio backend is not reachable at its configured "
                    + "api.baseUrl — start it (backend/scripts/dev-backend.sh restart) to run the "
                    + "API suites.");
        }
        if (!FirebaseTokens.isConfigured()) {
            throw new SkipException(FirebaseTokens.notConfiguredReason());
        }
    }

    /** True when {@code role} has a seeded account configured. */
    public boolean has(String role) {
        return TestAccounts.hasAccountFor(role);
    }

    /** A request builder that signs every call as {@code role}. */
    public Caller as(String role) {
        if (!has(role)) {
            throw new SkipException("No '" + role + "' account configured — add roleAccounts."
                    + role + " to testdata/mobile/test-accounts.json to run this test.");
        }
        return new Caller(http, role, bearerFor(role));
    }

    /** A request builder that sends no credentials at all. */
    public Caller anonymous() {
        return new Caller(http, "anonymous", Map.of());
    }

    /**
     * A request builder carrying a syntactically valid but unverifiable bearer token.
     *
     * <p>Distinct from {@link #anonymous()} on purpose: "no Authorization header" and "an
     * Authorization header the server cannot verify" travel different branches of
     * {@code firebaseAuth}, and only the second one proves the signature is actually checked.
     */
    public Caller withForgedToken() {
        return new Caller(http, "forged", Map.of("Authorization", "Bearer " + FORGED_JWT));
    }

    /** The underlying client, for the few probes that need to bypass the role machinery. */
    public ApiClient raw() {
        return http;
    }

    /** The email behind {@code role} — needed where a payload must name its own caller. */
    public String emailOf(String role) {
        return TestAccounts.emailFor(role);
    }

    private Map<String, String> bearerFor(String role) {
        if (!FirebaseTokens.isConfigured()) {
            throw new SkipException(FirebaseTokens.notConfiguredReason());
        }
        String token = FirebaseTokens.idTokenFor(
                TestAccounts.emailFor(role), TestAccounts.passwordFor(role));
        return Map.of("Authorization", "Bearer " + token);
    }

    /**
     * A well-formed RS256 JWT with a plausible Firebase payload and a meaningless signature.
     *
     * <p>Hand-built rather than minted: the point is a token that gets past any shape check and
     * fails only at signature verification.
     */
    private static final String FORGED_JWT =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImZvcmdlZCIsInR5cCI6IkpXVCJ9."
            + "eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20vb3VyLXBhcm9qZWN0Iiwi"
            + "YXVkIjoib3VyLXBhcm9qZWN0Iiwic3ViIjoiZm9yZ2VkLXVpZCIsImVtYWlsIjoiYXR0YWNr"
            + "ZXJAZXhhbXBsZS5jb20iLCJleHAiOjk5OTk5OTk5OTl9."
            + "c2lnbmF0dXJlLXRoYXQtd2FzLW5ldmVyLWlzc3VlZC1ieS1nb29nbGU";

    /**
     * One caller identity, bound to a role's credentials.
     *
     * <p>Every method returns the raw {@link ApiClient.Response} rather than asserting: the whole
     * value of the security suite is comparing what different callers get from the same route, and
     * a helper that threw on a non-2xx would make the interesting cases unreachable.
     */
    public static final class Caller {

        private final ApiClient http;
        private final String role;
        private final Map<String, String> auth;
        private final Map<String, String> extraHeaders = new HashMap<>();

        private Caller(ApiClient http, String role, Map<String, String> auth) {
            this.http = http;
            this.role = role;
            this.auth = auth;
        }

        /** The role this caller signs as — used in assertion messages. */
        public String role() {
            return role;
        }

        /**
         * Adds a header to every subsequent call, e.g. an {@code Origin} to look like a browser.
         * Mutates and returns this caller, so it reads as part of the call chain.
         */
        public Caller withHeader(String name, String value) {
            extraHeaders.put(name, value);
            return this;
        }

        /** Makes this caller look like a request from a browser at {@code origin}. */
        public Caller fromBrowser(String origin) {
            return withHeader("Origin", origin).withHeader("Sec-Fetch-Site", "same-origin");
        }

        public ApiClient.Response get(String path) {
            return http.get(path, headers());
        }

        public ApiClient.Response post(String path, Map<String, Object> payload) {
            return http.postJson(path, payload, headers());
        }

        public ApiClient.Response put(String path, Map<String, Object> payload) {
            return http.put(path, payload, headers());
        }

        public ApiClient.Response patch(String path, Map<String, Object> payload) {
            return http.patch(path, payload, headers());
        }

        public ApiClient.Response delete(String path) {
            return http.delete(path, headers());
        }

        /** Issues {@code method} against {@code path}, for table-driven sweeps over the route list. */
        public ApiClient.Response call(String method, String path, Map<String, Object> payload) {
            return http.send(method, path, payload, headers());
        }

        /** Sends a body this caller chooses verbatim, with its own content type. */
        public ApiClient.Response raw(String method, String path, String body, String contentType) {
            return http.sendRaw(method, path, body, contentType, headers());
        }

        private Map<String, String> headers() {
            Map<String, String> all = new HashMap<>(auth);
            all.putAll(extraHeaders);
            return all;
        }
    }
}
