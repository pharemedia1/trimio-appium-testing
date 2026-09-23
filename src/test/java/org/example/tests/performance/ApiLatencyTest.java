package org.example.tests.performance;

import org.example.api.LatencySample;
import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.config.ConfigReader;
import org.example.utils.ApiClient;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

/**
 * PERF-01 — response-time budgets for the reads that sit in front of a user who is waiting.
 *
 * <p><b>What this is and is not.</b> It is a regression guard, not a capacity model. Run on a
 * developer machine against a local Postgres it cannot tell you what Trimio does under production
 * load, and it does not try: the budgets are set well above the measured baseline so that normal
 * variation never fails a build. What it catches is the change of <em>shape</em> — the query that
 * lost its index, the endpoint that grew an N+1, the handler that started calling Stripe
 * synchronously. Those move a p95 by an order of magnitude, not by 20%.
 *
 * <p>Measured baseline on 2026-09-21 (12 samples each, local backend): reference reads 2-82 ms,
 * store and membership reads 55-151 ms, admin counts 3-15 ms. The default budget of 1500 ms is
 * roughly ten times the slowest of those, which is the margin needed for a machine that is also
 * running an emulator and a Flutter web build — as this one was.
 *
 * <p>Override per run with {@code -Dperf.budgetMillis=…} when measuring somewhere quieter.
 *
 * <p>The endpoints are chosen because a user is blocked on each: the catalogue the booking flow
 * opens with, the shop's first page, the membership plans, the notification badge, the admin
 * dashboard counts. A slow report nobody waits for is not on this list.
 */
public class ApiLatencyTest extends ApiBaseTest {

    /** Samples per endpoint — enough for a p95 to mean something without making the suite slow. */
    private static final int SAMPLES = 15;

    /** Discarded before measuring: the first call pays for the JIT, the pool and any lazy cache. */
    private static final int WARMUP = 3;

    private static long budgetMillis() {
        return ConfigReader.getInt("perf.budgetMillis", 1500);
    }

    /**
     * The blocking reads, with the role that actually makes each call.
     *
     * <p>Role matters for timing as much as for authorization: {@code /store/client/products}
     * answered to a client runs the client catalogue query, and the same path is not even reachable
     * for anyone else.
     */
    @DataProvider(name = "blockingReads")
    public Object[][] blockingReads() {
        return new Object[][]{
                {"reference catalogue (booking step 1)", TrimioApi.CLIENT, "/reference/catalog"},
                {"services list", TrimioApi.CLIENT, "/reference/services"},
                {"booking options", TrimioApi.CLIENT, "/appointment/booking-options"},
                {"store products (shop tab)", TrimioApi.CLIENT, "/store/client/products"},
                {"store categories", TrimioApi.CLIENT, "/store/client/categories"},
                {"store cart", TrimioApi.CLIENT, "/store/client/cart"},
                {"membership catalogue", TrimioApi.CLIENT, "/memberships/catalog"},
                {"membership overview", TrimioApi.CLIENT, "/memberships/overview"},
                {"notifications", TrimioApi.CLIENT, "/notifications/"},
                {"saved addresses", TrimioApi.CLIENT, "/api/user-addresses/"},
                {"pro listing", TrimioApi.PROFESSIONAL, "/professionalProfile/listing"},
                {"pro wallet", TrimioApi.PROFESSIONAL, "/api/pro/wallet"},
                {"pro performance summary", TrimioApi.PROFESSIONAL, "/api/performance/summary"},
                {"admin user counts", TrimioApi.ADMIN, "/admin/user-counts"},
                {"admin professional list", TrimioApi.ADMIN, "/admin/all-professionals"},
                {"admin professional status counts", TrimioApi.ADMIN, "/admin/professional-status-counts"},
                {"vendor overview", TrimioApi.VENDOR, "/store/vendor/overview"},
        };
    }

    @Test(dataProvider = "blockingReads",
            description = "PERF-001: a blocking read answers inside its budget at p95")
    public void blockingReadStaysWithinBudget(String what, String role, String path) {
        if (!api.has(role)) {
            throw new org.testng.SkipException("No '" + role + "' account configured.");
        }
        TrimioApi.Caller caller = api.as(role);
        LatencySample sample = measure(caller, path, what);

        LOG.info("PERF-001 {}", sample);

        Assert.assertEquals(sample.failures(), 0,
                what + " (" + path + ") returned " + sample.failures() + " error responses out of "
                        + sample.count() + " — last status " + sample.lastStatus() + ". A timing "
                        + "measurement over failing requests is meaningless, so this is reported as "
                        + "the failure it is rather than as a fast p95.");
        Assert.assertTrue(sample.p95() <= budgetMillis(),
                what + " (" + path + ", as " + role + ") took " + sample.p95() + " ms at p95 "
                        + "against a budget of " + budgetMillis() + " ms (p50 " + sample.p50()
                        + " ms, max " + sample.max() + " ms over " + sample.count() + " samples). "
                        + "The budget is roughly ten times the measured baseline, so this is a "
                        + "change of shape — a lost index, an N+1, or a synchronous call to "
                        + "something remote — not normal variation.");
    }

    /**
     * PERF-002 — the whole set, reported together.
     *
     * <p>The per-endpoint tests above fail one at a time and say nothing about the others. This
     * one exists for the output: a single table of every measured read, which is what someone
     * comparing two branches actually wants to look at. It asserts only the aggregate, so it does
     * not double-report the same regression.
     */
    @Test(dependsOnMethods = "blockingReadStaysWithinBudget", alwaysRun = true,
            description = "PERF-002: the blocking-read profile as one table")
    public void blockingReadProfile() {
        SoftAssert soft = new SoftAssert();
        LOG.info("PERF-002 — blocking-read latency profile (budget {} ms at p95):", budgetMillis());

        for (Object[] row : blockingReads()) {
            String what = (String) row[0];
            String role = (String) row[1];
            String path = (String) row[2];
            if (!api.has(role)) {
                continue;
            }
            LatencySample sample = measure(api.as(role), path, what);
            LOG.info("  {}", sample);
            soft.assertTrue(sample.p95() <= budgetMillis(),
                    sample.label() + " p95 " + sample.p95() + " ms exceeds " + budgetMillis() + " ms");
        }
        soft.assertAll();
    }

    /**
     * PERF-003 — list endpoints must not return unbounded payloads.
     *
     * <p>A list route with no page size is fine on seeded data and fatal on real data: it is the
     * same code path either way, so no functional test can tell them apart, and the symptom only
     * appears once the table is large. Measuring the body size now is the cheapest way to notice
     * that a route returns everything it has.
     *
     * <p>The threshold is about shape rather than bytes — a few hundred kilobytes for one screen's
     * worth of list means the route is not paginating.
     */
    @Test(description = "PERF-003: list endpoints return a bounded payload")
    public void listEndpointsReturnBoundedPayloads() {
        int maxBytes = ConfigReader.getInt("perf.maxListBytes", 512 * 1024);
        SoftAssert soft = new SoftAssert();

        record Listing(String role, String path) { }
        List<Listing> listings = List.of(
                new Listing(TrimioApi.CLIENT, "/store/client/products"),
                new Listing(TrimioApi.CLIENT, "/notifications/"),
                new Listing(TrimioApi.ADMIN, "/admin/all-professionals"),
                new Listing(TrimioApi.ADMIN, "/admin/reports"),
                new Listing(TrimioApi.ADMIN, "/store/admin/orders"),
                new Listing(TrimioApi.ADMIN, "/store/admin/products"),
                new Listing(TrimioApi.VENDOR, "/store/vendor/orders"));

        for (Listing l : listings) {
            if (!api.has(l.role())) {
                continue;
            }
            ApiClient.Response response = api.as(l.role()).get(l.path());
            int size = response.body() == null ? 0 : response.body().length();
            LOG.info("PERF-003 {} (as {}) -> {} bytes, status {}",
                    l.path(), l.role(), size, response.status());
            soft.assertTrue(size <= maxBytes,
                    l.path() + " returned " + size + " bytes in one response (limit " + maxBytes
                            + "). On seeded data that is merely large; on production data a route "
                            + "that returns everything it has is an outage. Add a page size.");
        }
        soft.assertAll();
    }

    private LatencySample measure(TrimioApi.Caller caller, String path, String label) {
        for (int i = 0; i < WARMUP; i++) {
            caller.get(path);
        }
        LatencySample sample = new LatencySample(label + "  " + path);
        for (int i = 0; i < SAMPLES; i++) {
            long started = System.nanoTime();
            ApiClient.Response response = caller.get(path);
            sample.record((System.nanoTime() - started) / 1_000_000, response.status());
        }
        return sample;
    }
}
