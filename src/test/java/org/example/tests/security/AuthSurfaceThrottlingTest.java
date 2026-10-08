package org.example.tests.security;

import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

/**
 * SEC-062 — the limiter covers the rest of the unauthenticated auth surface, not just login.
 *
 * <p>Login is the obvious target and the one that gets protected first. The endpoints that matter
 * nearly as much are the ones answering questions about accounts — {@code checkUserExists} and the
 * reset-OTP senders — because unthrottled they turn into a bulk enumeration tool and, for the OTP
 * senders, into a way to have Trimio send mail to arbitrary addresses.
 *
 * <p><b>Why this is its own class, in its own suite.</b> It lived in
 * {@link RateLimitAndLockoutTest} and could not do its job there.
 * {@code middleware/authRateLimit.js} builds ONE {@code credentialLimiter}, keyed per IP, and
 * {@code /auth/login} and {@code /auth/checkUserExists} both sit behind it — one instance, one
 * counter. SEC-060 runs first and deliberately exhausts that counter, which is its whole subject.
 * By the time this test ran, attempt 1 already answered 429:
 *
 * <pre>
 *   SEC-062: checkUserExists throttled at attempt 1
 *   &lt;&lt;&lt; PASS: authSurfaceBeyondLoginIsThrottled
 * </pre>
 *
 * <p>It passed on every run while proving nothing. A 429 on the first request is what <em>any</em>
 * endpoint returns once the budget is spent, including one with no limiter of its own — which is
 * precisely the defect this test exists to catch. The old assertion, {@code throttledAt > 0}, could
 * not tell the two apart.
 *
 * <p>So the assertion now requires the endpoint to have ANSWERED at least once before being
 * throttled. That makes the 429 attributable to this endpoint's own traffic rather than inherited,
 * and it is the difference between measuring a control and observing a side effect.
 */
public class AuthSurfaceThrottlingTest extends ApiBaseTest {

    /** Enough attempts to trip a 10-per-15-minutes allowance several times over. */
    private static final int ATTEMPTS = 40;

    @Test(description = "SEC-062: the unauthenticated auth surface is throttled, not just login")
    public void authSurfaceBeyondLoginIsThrottled() {
        int throttledAt = -1;
        int answered = 0;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            ApiClient.Response response = api.anonymous().post("/auth/checkUserExists",
                    Map.of("email", "enum-probe-" + attempt + "-" + UUID.randomUUID() + "@example.com"));
            if (response.status() == 429) {
                throttledAt = attempt;
                break;
            }
            answered++;
        }
        LOG.info("SEC-062: checkUserExists answered {} request(s), then throttled at attempt {}",
                answered, throttledAt);

        if (throttledAt == 1) {
            // Nothing was measured, so nothing is reported. The budget was already gone when this
            // started -- most likely a login-throttling test ran first in the same 15-minute
            // window, since they share the single credentialLimiter instance. Passing here would
            // be the false green this class was split out to remove.
            throw new SkipException("checkUserExists answered 429 on the FIRST request, so the "
                    + "per-IP credential budget was already spent before this test began and "
                    + "nothing about this endpoint was measured. authRateLimit.js mounts one "
                    + "credentialLimiter across /auth and /password, so any earlier login or "
                    + "reset traffic in the same 15-minute window consumes it. Run "
                    + "suites/security-enumeration-testng.xml against a freshly started backend, "
                    + "which is where this test belongs.");
        }

        Assert.assertTrue(throttledAt > 0,
                "ENUMERATION AT SCALE: " + ATTEMPTS + " consecutive POSTs to /auth/checkUserExists "
                        + "— a public endpoint that reports whether an address is registered, plus "
                        + "its user id and role — were all answered. Unthrottled, that is a bulk "
                        + "directory of Trimio's users.");
        Assert.assertTrue(answered > 0,
                "The endpoint never answered a single request before throttling, so the 429 cannot "
                        + "be attributed to this endpoint being limited.");
    }
}
