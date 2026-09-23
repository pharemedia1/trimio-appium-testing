package org.example.tests.performance;

import org.example.api.LatencySample;
import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.config.ConfigReader;
import org.example.utils.ApiClient;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PERF-02 — behaviour under concurrent load, which is a different question from PERF-01's.
 *
 * <p>Serial latency measures the code path. This measures everything <em>around</em> it: the
 * Postgres connection pool, the event loop, and whatever the handler holds while it waits. Those
 * are invisible one request at a time and are where a real outage starts — a route that leaks a
 * pool connection is indistinguishable from a healthy one until enough callers arrive at once, and
 * then it stops serving every other route too.
 *
 * <p><b>Read-only, and deliberately modest.</b> Every path here is a GET, so a run changes nothing
 * and can be repeated; and the concurrency defaults are small because this is a correctness check
 * about degradation, not a load test. Asserting that a laptop sustains N requests per second would
 * be a number about the laptop. What is asserted instead is that concurrency does not produce
 * <em>errors</em>, and that the p95 does not collapse relative to the serial baseline.
 *
 * <p>Tune with {@code -Dperf.concurrency=…} and {@code -Dperf.requestsPerWorker=…} to use this as
 * a real load harness against a deployed environment.
 */
public class ApiConcurrencyTest extends ApiBaseTest {

    private static int concurrency() {
        return ConfigReader.getInt("perf.concurrency", 10);
    }

    private static int requestsPerWorker() {
        return ConfigReader.getInt("perf.requestsPerWorker", 10);
    }

    private static long budgetMillis() {
        // Roomier than the serial budget on purpose: with N callers in flight, queuing is the
        // expected behaviour, not a defect. What is not expected is errors or a collapse.
        return ConfigReader.getInt("perf.concurrentBudgetMillis", 4000);
    }

    @DataProvider(name = "concurrentReads")
    public Object[][] concurrentReads() {
        return new Object[][]{
                {"reference catalogue", TrimioApi.CLIENT, "/reference/catalog"},
                {"store products", TrimioApi.CLIENT, "/store/client/products"},
                {"membership overview", TrimioApi.CLIENT, "/memberships/overview"},
                {"booking options", TrimioApi.CLIENT, "/appointment/booking-options"},
                {"admin professional list", TrimioApi.ADMIN, "/admin/all-professionals"},
        };
    }

    @Test(dataProvider = "concurrentReads",
            description = "PERF-010: a read stays correct and bounded under concurrent callers")
    public void readSurvivesConcurrentCallers(String what, String role, String path) {
        if (!api.has(role)) {
            throw new org.testng.SkipException("No '" + role + "' account configured.");
        }
        // One Caller, shared across threads. That is the realistic shape — many users, one
        // service — and it also exercises the token cache from several threads at once, which is
        // where a lazily-minted token would otherwise be minted N times and hit Firebase's
        // sign-in rate limit mid-test.
        TrimioApi.Caller caller = api.as(role);
        LatencySample sample = new LatencySample(what + "  " + path);
        AtomicInteger transportFailures = new AtomicInteger();

        int workers = concurrency();
        int perWorker = requestsPerWorker();
        Instant started = Instant.now();

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int w = 0; w < workers; w++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < perWorker; i++) {
                        long t0 = System.nanoTime();
                        try {
                            ApiClient.Response response = caller.get(path);
                            sample.record((System.nanoTime() - t0) / 1_000_000, response.status());
                        } catch (RuntimeException e) {
                            // A refused or reset connection is the pool or the listener giving
                            // up. Counted separately: it never produced a status, so it cannot
                            // be a latency observation, and it is a worse symptom than a 500.
                            transportFailures.incrementAndGet();
                            LOG.warn("PERF-010 transport failure on {}: {}", path, e.getMessage());
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get(2, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Concurrent run against " + path + " did not finish: "
                    + e.getMessage(), e);
        } finally {
            pool.shutdownNow();
        }

        Duration elapsed = Duration.between(started, Instant.now());
        int total = workers * perWorker;
        double throughput = elapsed.toMillis() == 0 ? 0
                : total * 1000.0 / elapsed.toMillis();

        LOG.info("PERF-010 {} | {} workers × {} = {} requests in {} ms ({} req/s)",
                sample, workers, perWorker, total, elapsed.toMillis(),
                String.format("%.1f", throughput));

        Assert.assertEquals(transportFailures.get(), 0,
                what + " (" + path + ") produced " + transportFailures.get() + " transport "
                        + "failures under " + workers + " concurrent callers — connections were "
                        + "refused or reset. That is the connection pool or the listener giving "
                        + "up, and it takes every other route down with it.");
        Assert.assertEquals(sample.failures(), 0,
                what + " (" + path + ") returned " + sample.failures() + " error responses out of "
                        + sample.count() + " under " + workers + " concurrent callers (last status "
                        + sample.lastStatus() + "). The same request succeeds serially, so this is "
                        + "contention, not the request being wrong.");
        Assert.assertTrue(sample.p95() <= budgetMillis(),
                what + " (" + path + ") reached " + sample.p95() + " ms at p95 under " + workers
                        + " concurrent callers, against a budget of " + budgetMillis() + " ms "
                        + "(p50 " + sample.p50() + " ms, max " + sample.max() + " ms). Queuing is "
                        + "expected; collapsing is not.");
    }

    /**
     * PERF-011 — concurrent callers on <em>different</em> routes, which is what a real minute
     * looks like.
     *
     * <p>PERF-010 hammers one path at a time, so a route that monopolises the connection pool
     * still looks healthy — it is the only thing running. This mixes a client, a professional and
     * an admin read across the same window, so one handler holding a connection shows up as
     * failures on the others, which is exactly how it presents in production.
     */
    @Test(description = "PERF-011: mixed concurrent traffic across roles does not degrade")
    public void mixedTrafficAcrossRolesDoesNotDegrade() {
        record Traffic(String role, String path) { }
        List<Traffic> mix = new ArrayList<>();
        for (Traffic t : List.of(
                new Traffic(TrimioApi.CLIENT, "/store/client/products"),
                new Traffic(TrimioApi.CLIENT, "/memberships/overview"),
                new Traffic(TrimioApi.PROFESSIONAL, "/professionalProfile/listing"),
                new Traffic(TrimioApi.PROFESSIONAL, "/api/pro/wallet"),
                new Traffic(TrimioApi.ADMIN, "/admin/user-counts"),
                new Traffic(TrimioApi.VENDOR, "/store/vendor/overview"))) {
            if (api.has(t.role())) {
                mix.add(t);
            }
        }
        if (mix.size() < 2) {
            throw new org.testng.SkipException("Fewer than two roles are configured, so there is "
                    + "no mixed traffic to generate.");
        }

        // Callers are resolved up front: minting a token inside a worker would measure Firebase.
        List<TrimioApi.Caller> callers = mix.stream().map(t -> api.as(t.role())).toList();
        LatencySample sample = new LatencySample("mixed traffic across " + mix.size() + " routes");
        AtomicInteger transportFailures = new AtomicInteger();

        int rounds = requestsPerWorker();
        ExecutorService pool = Executors.newFixedThreadPool(mix.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < mix.size(); i++) {
                TrimioApi.Caller caller = callers.get(i);
                String path = mix.get(i).path();
                futures.add(pool.submit(() -> {
                    for (int r = 0; r < rounds; r++) {
                        long t0 = System.nanoTime();
                        try {
                            ApiClient.Response response = caller.get(path);
                            sample.record((System.nanoTime() - t0) / 1_000_000, response.status());
                        } catch (RuntimeException e) {
                            transportFailures.incrementAndGet();
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get(2, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Mixed-traffic run did not finish: " + e.getMessage(), e);
        } finally {
            pool.shutdownNow();
        }

        LOG.info("PERF-011 {} across {} routes", sample, mix.size());
        Assert.assertEquals(transportFailures.get(), 0,
                "Mixed traffic produced " + transportFailures.get() + " transport failures.");
        Assert.assertEquals(sample.failures(), 0,
                "Mixed traffic across " + mix.size() + " routes produced " + sample.failures()
                        + " error responses out of " + sample.count() + " — each of those routes "
                        + "answers cleanly on its own, so one of them is holding a resource the "
                        + "others need.");
        Assert.assertTrue(sample.p95() <= budgetMillis(),
                "Mixed traffic reached " + sample.p95() + " ms at p95 against a budget of "
                        + budgetMillis() + " ms (p50 " + sample.p50() + ", max " + sample.max()
                        + ").");
    }
}
