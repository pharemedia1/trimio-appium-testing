package org.example.tests.security;

import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.example.utils.DbHelper;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * SEC-04 — hostile input: injection, traversal, type confusion and oversized payloads.
 *
 * <p>The backend talks to Postgres through {@code pg} with bound parameters, so the expectation is
 * that none of this works. The value of the suite is that it says so <em>continuously</em>: the
 * day someone builds one query by concatenation, these go red, and nothing else would notice —
 * string-built SQL behaves identically to bound SQL for every input a real user can type, and the
 * UI cannot produce the ones that tell the difference.
 *
 * <p>What each probe is actually asserting is worth being precise about, because "no 500" is not
 * the same claim as "not injectable":
 * <ul>
 *   <li>a <b>500 with a driver message</b> means the payload reached the parser — the strongest
 *       signal of concatenation;</li>
 *   <li>a <b>200 whose body changes with the payload</b> means it altered the query;</li>
 *   <li>a <b>200 identical to the clean call</b> means it was bound as a literal, which is the
 *       pass.</li>
 * </ul>
 * So the assertions compare against a control request rather than just reading the status.
 */
public class InjectionAndInputHandlingTest extends ApiBaseTest {

    /**
     * Payloads chosen to cover the distinct ways a concatenated query breaks: boolean tautology,
     * statement stacking, a UNION, comment truncation, and a pre-encoded form that a naive
     * decode-then-concatenate would expand.
     */
    @DataProvider(name = "sqlPayloads")
    public Object[][] sqlPayloads() {
        return new Object[][]{
                {"tautology", "1' OR '1'='1"},
                {"stacked statement", "1; DROP TABLE users--"},
                {"union select", "1 UNION SELECT NULL,NULL--"},
                {"comment truncation", "' OR 1=1--"},
                {"pre-encoded", "%27%20OR%201=1--"},
                {"quote escape", "\\'; SELECT pg_sleep(0)--"},
        };
    }

    @Test(dataProvider = "sqlPayloads",
            description = "SEC-030: SQL payloads in a query parameter are bound, not interpreted")
    public void sqlPayloadsInQueryParametersAreBound(String name, String payload) {
        TrimioApi.Caller client = api.as(TrimioApi.CLIENT);

        // The control: the same route with a value that is merely wrong, not hostile. Anything the
        // payload does that this does not do is the payload doing it.
        ApiClient.Response control = client.get("/appointment/service-price-range?service_id=0");
        ApiClient.Response probe = client.get(
                "/appointment/service-price-range?service_id=" + encode(payload));

        Assert.assertTrue(probe.status() < 500,
                "[" + name + "] '" + payload + "' produced " + probe.status() + " — a server error "
                        + "from a SQL metacharacter is the signature of a concatenated query. Body: "
                        + probe.body());
        Assert.assertFalse(mentionsDatabaseInternals(probe.body()),
                "[" + name + "] the response quotes database internals, so the payload reached the "
                        + "SQL parser: " + probe.body());
        Assert.assertEquals(probe.status(), control.status(),
                "[" + name + "] the hostile value was handled differently from a merely invalid "
                        + "one (" + probe.status() + " vs " + control.status() + "), which means it "
                        + "was not simply bound as a literal.");
    }

    @Test(dataProvider = "sqlPayloads",
            description = "SEC-031: SQL payloads in a path segment are bound, not interpreted")
    public void sqlPayloadsInPathSegmentsAreBound(String name, String payload) {
        TrimioApi.Caller client = api.as(TrimioApi.CLIENT);

        ApiClient.Response control = client.get("/reports/client/0");
        ApiClient.Response probe = client.get("/reports/client/" + encode(payload));

        Assert.assertTrue(probe.status() < 500,
                "[" + name + "] a SQL payload in the :clientId segment produced " + probe.status()
                        + ": " + probe.body());
        Assert.assertEquals(probe.body(), control.body(),
                "[" + name + "] the response changed when the path segment carried SQL. This route "
                        + "resolves the client from the TOKEN and ignores the segment "
                        + "(resolveScopedClientId), so a payload there must make no difference at "
                        + "all — and a difference means the segment is reaching a query.");
    }

    /**
     * SEC-032 — the users table is still there.
     *
     * <p>Blunt, and the point. The stacked-statement payloads above include
     * {@code DROP TABLE users}; if one ever lands, a status-code assertion passes while the
     * database is gone. This is the check that does not depend on the API answering correctly.
     */
    @Test(dependsOnMethods = {"sqlPayloadsInQueryParametersAreBound", "sqlPayloadsInPathSegmentsAreBound"},
            alwaysRun = true,
            description = "SEC-032: the schema survived the injection probes")
    public void schemaSurvivedTheProbes() {
        if (!DbHelper.isConfigured()) {
            throw new SkipException("No database password configured (-Ddb.password=…), so the "
                    + "injection probes cannot be confirmed against the schema itself.");
        }
        long users = DbHelper.count("users");
        Assert.assertTrue(users > 0,
                "The users table is empty or missing after the injection probes — a stacked "
                        + "statement executed.");
        LOG.info("SEC-032: users table intact ({} rows) after the injection sweep", users);
    }

    /**
     * SEC-033 — path traversal against the document reader.
     *
     * <p>{@code GET /api/documents/:key} is public and takes a storage key, which is the shape
     * that reads a file off disk when it is implemented naively. The repo has already had one
     * storage IDOR fixed; this keeps the traversal half of that surface asserted.
     */
    @Test(description = "SEC-033: traversal sequences in a document key do not escape the store")
    public void documentKeysCannotTraverse() {
        List<String> traversals = List.of(
                "../../../etc/passwd",
                "..%2F..%2F..%2Fetc%2Fpasswd",
                "....//....//etc/passwd",
                "/etc/passwd",
                "..\\..\\windows\\win.ini");

        SoftAssert soft = new SoftAssert();
        for (String key : traversals) {
            ApiClient.Response response = api.anonymous().get("/api/documents/" + encode(key));
            soft.assertTrue(response.status() == 404 || response.status() == 400,
                    "Traversal key '" + key + "' returned " + response.status()
                            + " instead of a flat refusal: " + truncate(response.body()));
            soft.assertFalse(response.body() != null && response.body().contains("root:"),
                    "TRAVERSAL: '" + key + "' returned what looks like /etc/passwd content");
        }
        soft.assertAll();
    }

    /**
     * SEC-034 — a body whose fields are the wrong <em>type</em>.
     *
     * <p>Express parses JSON before any handler sees it, so a field the code expects to be a
     * string can arrive as an object or an array. The risk is not SQL here — it is a handler that
     * passes the object onward and crashes somewhere with no request context, which is both an
     * availability problem and the usual first step of a more interesting one.
     *
     * <p>Recorded as observed on 2026-09-21: {@code /auth/login} answers these with <b>500</b>
     * rather than a 400. Nothing leaks — the body is a fixed "Internal server error." with no
     * stack — but an unvalidated type reaching a crash is a gap worth holding a test against, so
     * this asserts the behaviour we want and will fail until the input is validated.
     */
    @Test(description = "SEC-034: wrongly-typed body fields are rejected, not crashed on")
    public void wronglyTypedFieldsAreRejected() {
        List<Map<String, Object>> bodies = List.of(
                Map.of("email", Map.of("$ne", ""), "password", Map.of("$ne", "")),
                Map.of("email", List.of("a", "b"), "password", "x"),
                Map.of("email", api.emailOf(TrimioApi.CLIENT), "password", Map.of("$gt", "")));

        SoftAssert soft = new SoftAssert();
        for (Map<String, Object> body : bodies) {
            ApiClient.Response response = api.anonymous().post("/auth/login", body);
            if (response.status() == 429) {
                throw new SkipException("The auth rate limiter is still cooling down from an "
                        + "earlier probe (429). Re-run this class on its own, or wait out the "
                        + "window — a 429 here proves nothing about type handling.");
            }
            soft.assertTrue(response.status() >= 400 && response.status() < 500,
                    "A login body with a non-string field answered " + response.status()
                            + " — an unvalidated type reached a crash. Expected a 4xx. Body sent: "
                            + body + "; response: " + truncate(response.body()));
            soft.assertFalse(leaksStackTrace(response.body()),
                    "The error response carries a stack trace: " + truncate(response.body()));
        }
        soft.assertAll();
    }

    /**
     * SEC-035 — an oversized body.
     *
     * <p>A 1 MB email address is not a realistic request; it is the cheapest way to find out
     * whether there is a body limit at all, because without one an unauthenticated caller decides
     * how much memory the process allocates. Observed on 2026-09-21: <b>500</b>, where a body
     * limit would give 413.
     */
    @Test(description = "SEC-035: an oversized request body is refused, not crashed on")
    public void oversizedBodyIsRefused() {
        String huge = "A".repeat(1_000_000);
        ApiClient.Response response = api.anonymous().post("/auth/login",
                Map.of("email", huge, "password", "x"));

        if (response.status() == 429) {
            throw new SkipException("The auth rate limiter is cooling down (429) — re-run this "
                    + "class on its own.");
        }
        Assert.assertTrue(response.status() == 413 || (response.status() >= 400 && response.status() < 500),
                "A 1 MB request body answered " + response.status() + " rather than 413 Payload "
                        + "Too Large (or another 4xx). Without a body limit an unauthenticated "
                        + "caller chooses how much memory the process allocates. Body: "
                        + truncate(response.body()));
    }

    /**
     * SEC-036 — errors must not describe the server.
     *
     * <p>Swept across the routes known to be able to fail, including the membership invoice route
     * that currently 500s for a member whose subscription carries no Stripe customer id. A 500 is
     * a defect; a 500 that prints a stack trace, a file path or a SQL statement is a different and
     * worse one, and this separates them.
     */
    @Test(description = "SEC-036: error responses disclose nothing about the server internals")
    public void errorsDoNotDiscloseInternals() {
        SoftAssert soft = new SoftAssert();
        record Probe(String role, String path) { }
        List<Probe> probes = List.of(
                new Probe(TrimioApi.PROFESSIONAL, "/memberships/invoices"),
                new Probe(TrimioApi.CLIENT, "/memberships/cancel/quote"),
                new Probe(TrimioApi.CLIENT, "/appointment/day-availability"),
                new Probe(TrimioApi.CLIENT, "/reviews/draft"),
                new Probe(TrimioApi.CLIENT, "/api/shop/slots"));

        for (Probe p : probes) {
            if (!api.has(p.role())) {
                continue;
            }
            ApiClient.Response response = api.as(p.role()).get(p.path());
            soft.assertFalse(leaksStackTrace(response.body()),
                    p.path() + " (as " + p.role() + ", " + response.status() + ") leaked internals: "
                            + truncate(response.body()));
            soft.assertFalse(mentionsDatabaseInternals(response.body()),
                    p.path() + " (as " + p.role() + ", " + response.status() + ") named database "
                            + "internals: " + truncate(response.body()));
        }
        soft.assertAll();
    }

    // ---- helpers ------------------------------------------------------------

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Markers of a JS/driver error escaping into a response body. */
    private static boolean leaksStackTrace(String body) {
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase();
        return lower.contains("at object.")
                || lower.contains("node_modules")
                || lower.contains("/users/")
                || lower.contains(".js:")
                || lower.contains("typeerror:")
                || lower.contains("referenceerror:");
    }

    /** Markers of Postgres or the query itself being quoted back. */
    private static boolean mentionsDatabaseInternals(String body) {
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase();
        return lower.contains("syntax error at or near")
                || lower.contains("pg_")
                || lower.contains("relation \"")
                || lower.contains("select ")
                || lower.contains("postgres");
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<empty>";
        }
        return body.length() <= 400 ? body : body.substring(0, 400) + "…";
    }
}
