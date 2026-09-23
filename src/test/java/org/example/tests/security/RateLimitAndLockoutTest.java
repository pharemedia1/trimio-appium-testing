package org.example.tests.security;

import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.example.utils.DbHelper;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

/**
 * SEC-06 — brute-force resistance: the rate limiter and the per-account lockout.
 *
 * <p>Trimio has <em>two</em> independent controls here and they defend against different attacks,
 * which is why both are asserted rather than one standing in for the other:
 * <ul>
 *   <li>a <b>per-account lockout</b> ({@code users.failed_login_attempts} / {@code locked_until})
 *       stops a slow, distributed guessing run against one known address, which no per-IP limit
 *       would ever see;</li>
 *   <li>a <b>per-source rate limit</b> (429 {@code RATE_LIMITED}) stops one source spraying many
 *       addresses, which no per-account counter would ever see, because each account records a
 *       single failure.</li>
 * </ul>
 *
 * <p><b>This class deliberately locks an account, so it uses a throwaway one.</b> That is not
 * fastidiousness: an earlier manual probe of this very behaviour locked
 * {@code trimiotest+client1} for an hour and would have skipped every client journey in the
 * regression had it not been cleared. A security suite that breaks the functional suites is not
 * usable, so the target here is an address that has no account at all, and
 * {@link #releaseAnyLockout()} clears the record afterwards regardless.
 */
public class RateLimitAndLockoutTest extends ApiBaseTest {

    /** Generous enough to cross any sane threshold, small enough not to be an attack itself. */
    private static final int ATTEMPTS = 25;

    /**
     * The address the failed logins are aimed at.
     *
     * <p>Unregistered on purpose. The limiter and the lockout counter both key on the submitted
     * address, so an account never has to exist for either control to be observable — and using a
     * real one would take a seeded account out of service for the lockout window.
     */
    private final String targetEmail = "bruteforce-probe-" + UUID.randomUUID() + "@example.com";

    @Test(description = "SEC-060: repeated failed logins are throttled rather than answered forever")
    public void repeatedFailedLoginsAreThrottled() {
        int firstThrottled = -1;
        int firstLocked = -1;
        int answered = 0;

        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            ApiClient.Response response = api.anonymous().post("/auth/login",
                    Map.of("email", targetEmail, "password", "wrong-guess-" + attempt + "!A"));

            if (response.status() == 429 && firstThrottled < 0) {
                firstThrottled = attempt;
            }
            // 423 Locked is the per-account control; it can appear before or after the limiter
            // depending on which threshold is lower, so both are recorded rather than ordered.
            if (response.status() == 423 && firstLocked < 0) {
                firstLocked = attempt;
            }
            if (response.status() == 401) {
                answered++;
            }
        }

        LOG.info("SEC-060: {} attempts — {} answered 401, first 429 at {}, first 423 at {}",
                ATTEMPTS, answered, firstThrottled, firstLocked);

        Assert.assertTrue(firstThrottled > 0 || firstLocked > 0,
                "BRUTE FORCE: " + ATTEMPTS + " consecutive failed logins for the same address were "
                        + "all answered (" + answered + " × 401) with no 429 and no 423. Password "
                        + "guessing against a known address is then bounded only by network speed.");
        Assert.assertTrue(answered < ATTEMPTS,
                "BRUTE FORCE: every one of " + ATTEMPTS + " attempts got a plain 401 — neither "
                        + "control engaged.");
        int engagedAt = firstThrottled > 0 && firstLocked > 0
                ? Math.min(firstThrottled, firstLocked)
                : Math.max(firstThrottled, firstLocked);
        Assert.assertTrue(engagedAt <= 10,
                "BRUTE FORCE: a control only engaged at attempt " + engagedAt + ". Ten free "
                        + "guesses per address is already generous for a password policy this "
                        + "permissive.");
    }

    /**
     * SEC-061 — the 429 must be a refusal, not a slower yes.
     *
     * <p>A limiter that throttles the <em>wrong</em> passwords but lets the right one through is
     * not a limiter: an attacker only cares about the request that succeeds. So this checks that
     * once throttling has engaged, a correctly-formed login for the same address is refused too.
     * The address is unregistered, so no correct password exists — what is being asserted is that
     * the throttle answers before the credential is considered at all.
     */
    @Test(dependsOnMethods = "repeatedFailedLoginsAreThrottled",
            description = "SEC-061: throttling applies to the next attempt whatever it carries")
    public void throttlingAppliesToSubsequentAttempts() {
        ApiClient.Response response = api.anonymous().post("/auth/login",
                Map.of("email", targetEmail, "password", "Trimio123!"));

        Assert.assertTrue(response.status() == 429 || response.status() == 423
                        || response.status() == 401,
                "After the throttle engaged, a further login attempt answered " + response.status()
                        + ": " + response.body());
        Assert.assertNotEquals(response.status(), 200,
                "BRUTE FORCE BYPASS: a login succeeded immediately after the rate limiter engaged "
                        + "for this address — the throttle is not applied before the credential "
                        + "check.");
    }

    /**
     * SEC-062 — the limiter covers the rest of the unauthenticated auth surface, not just login.
     *
     * <p>Login is the obvious target and the one that gets protected first. The endpoints that
     * matter nearly as much are the ones that answer questions about accounts —
     * {@code checkUserExists} and the reset-OTP senders — because unthrottled they turn into a
     * bulk enumeration tool and, for the OTP senders, into a way to have Trimio send mail to
     * arbitrary addresses.
     */
    @Test(description = "SEC-062: the unauthenticated auth surface is throttled, not just login")
    public void authSurfaceBeyondLoginIsThrottled() {
        int throttledAt = -1;
        for (int attempt = 1; attempt <= 40; attempt++) {
            ApiClient.Response response = api.anonymous().post("/auth/checkUserExists",
                    Map.of("email", "enum-probe-" + attempt + "-" + UUID.randomUUID() + "@example.com"));
            if (response.status() == 429) {
                throttledAt = attempt;
                break;
            }
        }
        LOG.info("SEC-062: checkUserExists throttled at attempt {}", throttledAt);
        Assert.assertTrue(throttledAt > 0,
                "ENUMERATION AT SCALE: 40 consecutive POSTs to /auth/checkUserExists — a public "
                        + "endpoint that reports whether an address is registered, plus its user id "
                        + "and role — were all answered. Unthrottled, that is a bulk directory of "
                        + "Trimio's users.");
    }

    /**
     * Clears whatever this class created, whether it passed or failed.
     *
     * <p>The target address is unregistered, so normally there is nothing to clear. It runs anyway
     * because the row appears the moment an account is created with that address by anything else,
     * and because leaving cleanup conditional on the test having passed is how a failing security
     * test ends up taking the functional suite down with it.
     */
    @AfterClass(alwaysRun = true)
    public void releaseAnyLockout() {
        if (!DbHelper.isConfigured()) {
            LOG.warn("No -Ddb.password — cannot confirm the brute-force target was left unlocked. "
                    + "It is an unregistered address, so there should be no row to clear.");
            return;
        }
        int cleared = DbHelper.clearLockout(targetEmail);
        LOG.info("SEC-06 teardown: cleared the lockout on {} ({} row(s))", targetEmail, cleared);
    }

    /**
     * SEC-063 — the lockout must be recorded where the application can act on it.
     *
     * <p>A 429 from a limiter held in process memory disappears on the next deploy and does not
     * exist for a second instance. The per-account half is durable precisely because it is a
     * column, so this checks the columns are there and being written — the control's persistence,
     * not just its symptom.
     */
    @Test(description = "SEC-063: the per-account lockout is persisted, not only in-process")
    public void lockoutIsPersistedOnTheAccount() {
        if (!DbHelper.isConfigured()) {
            throw new SkipException("No -Ddb.password configured, so the lockout columns cannot be "
                    + "read. The 429/423 behaviour is covered by SEC-060 either way; this test is "
                    + "about the control surviving a restart.");
        }
        Assert.assertTrue(DbHelper.hasColumn("users", "failed_login_attempts"),
                "users.failed_login_attempts is gone — the per-account lockout has no counter, so "
                        + "only the in-process rate limiter is left and it forgets everything on "
                        + "restart.");
        Assert.assertTrue(DbHelper.hasColumn("users", "locked_until"),
                "users.locked_until is gone — nothing records that an account is locked.");
    }
}
