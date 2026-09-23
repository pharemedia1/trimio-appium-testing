package org.example.tests.security;

import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

/**
 * SEC-05 — whether an unauthenticated caller can learn which email addresses have Trimio accounts.
 *
 * <p>Enumeration is the step before every credential-stuffing run: knowing that an address is
 * registered turns a spray across millions of addresses into a focused attack on the few thousand
 * that exist. For Trimio it discloses more than membership, because the same answers carry the
 * internal {@code user_id} and the {@code user_type_id} — which is to say, who the professionals
 * and the admins are.
 *
 * <p>The password-reset path was <b>deliberately</b> hardened against this:
 * {@code passwordController.sendOtp} returns the same {@code 200 {"message":"OTP sent
 * successfully."}} for an unknown address as for a registered one, and skips generating an OTP for
 * the unknown one. That decision is the reason this suite exists — a control someone chose on
 * purpose is exactly the kind that gets undone by a later "improve the error message" change.
 *
 * <p>{@code /auth/checkUserExists} is the counter-example, and it is treated as a finding rather
 * than as the expected behaviour: it answers 200 with the user's id and type for a registered
 * address and 404 for an unknown one, unauthenticated. SEC-052 states the position we want.
 */
public class AccountEnumerationTest extends ApiBaseTest {

    /** An address that certainly has no account — unique per run so nothing can cache it. */
    private static String unknownEmail() {
        return "no-such-user-" + UUID.randomUUID() + "@example.com";
    }

    /**
     * Rate limiting protects these endpoints, which is correct and also means a probe can be
     * refused before it learns anything. A 429 is not evidence either way, so the test says so
     * instead of passing on it.
     */
    private static void skipIfRateLimited(ApiClient.Response... responses) {
        for (ApiClient.Response r : responses) {
            if (r.status() == 429) {
                throw new SkipException("The auth rate limiter answered 429 during this probe, so "
                        + "the two cases were never actually compared. Re-run this class on its "
                        + "own after the window (a few minutes), or from a different source "
                        + "address. A pass here would be meaningless.");
            }
        }
    }

    @Test(description = "SEC-050: password reset answers identically for known and unknown addresses")
    public void passwordResetDoesNotRevealWhetherAnAccountExists() {
        String known = api.emailOf(TrimioApi.CLIENT);
        if (known.isBlank()) {
            throw new SkipException("No client account configured to compare against.");
        }

        ApiClient.Response forKnown = api.anonymous()
                .post("/password/forgotPassword", Map.of("email", known));
        ApiClient.Response forUnknown = api.anonymous()
                .post("/password/forgotPassword", Map.of("email", unknownEmail()));
        skipIfRateLimited(forKnown, forUnknown);

        Assert.assertEquals(forUnknown.status(), forKnown.status(),
                "ACCOUNT ENUMERATION: /password/forgotPassword answered " + forKnown.status()
                        + " for a registered address and " + forUnknown.status() + " for an "
                        + "unknown one. The status alone then tells an unauthenticated caller "
                        + "which addresses have Trimio accounts.");
        Assert.assertEquals(forUnknown.errorMessage(), forKnown.errorMessage(),
                "ACCOUNT ENUMERATION: the reset endpoint returns different text for a registered "
                        + "address (\"" + forKnown.errorMessage() + "\") than for an unknown one "
                        + "(\"" + forUnknown.errorMessage() + "\"). This indistinguishability was "
                        + "a deliberate decision in passwordController.sendOtp — see the comment "
                        + "there before changing this test.");
        Assert.assertFalse(forUnknown.body() != null
                        && forUnknown.body().toLowerCase().contains("not found"),
                "ACCOUNT ENUMERATION: the reset endpoint said \"not found\" for an unknown "
                        + "address: " + forUnknown.body());
    }

    @Test(description = "SEC-051: registration availability does not disclose existing accounts")
    public void registrationAvailabilityDoesNotDiscloseAccounts() {
        ApiClient.Response response = api.anonymous().get("/auth/registration-availability");
        if (response.status() == 429) {
            throw new SkipException("Rate limited (429) — re-run this class on its own.");
        }
        Assert.assertTrue(response.status() < 500,
                "/auth/registration-availability answered " + response.status());
        String body = response.body() == null ? "" : body(response);
        Assert.assertFalse(body.contains("@"),
                "/auth/registration-availability is public and its response contains an email "
                        + "address: " + body);
    }

    /**
     * SEC-052 — the enumeration oracle.
     *
     * <p>{@code POST /auth/checkUserExists} is public and answers, for any address anyone cares to
     * try: whether it is registered, its {@code userId}, and its {@code userTypeId}. That last one
     * is the part that makes it more than a membership check — it identifies which addresses
     * belong to professionals and to admins, which is precisely the list a targeted attack starts
     * from.
     *
     * <p>This asserts the position the reset endpoint already takes: an unauthenticated caller
     * should not be able to distinguish the two cases. It will fail until the endpoint either
     * stops answering unauthenticated callers or stops distinguishing — both are real fixes, and
     * the test is written to pass under either.
     *
     * <p>Rate limiting does reduce the blast radius and is asserted separately in
     * {@link RateLimitAndLockoutTest}; it bounds how fast a list can be enumerated, not whether it
     * can be.
     */
    @Test(description = "SEC-052: checkUserExists does not disclose account existence, id or role")
    public void checkUserExistsDoesNotDiscloseAccounts() {
        String known = api.emailOf(TrimioApi.CLIENT);
        if (known.isBlank()) {
            throw new SkipException("No client account configured to compare against.");
        }

        ApiClient.Response forKnown = api.anonymous()
                .post("/auth/checkUserExists", Map.of("email", known));
        ApiClient.Response forUnknown = api.anonymous()
                .post("/auth/checkUserExists", Map.of("email", unknownEmail()));
        skipIfRateLimited(forKnown, forUnknown);

        // Either fix satisfies this: refusing unauthenticated callers entirely (both become
        // 401/403), or answering both the same way.
        boolean refusesEveryone = forKnown.status() == 401 || forKnown.status() == 403;
        if (refusesEveryone) {
            Assert.assertEquals(forUnknown.status(), forKnown.status(),
                    "checkUserExists refuses unauthenticated callers for a known address but not "
                            + "for an unknown one.");
            return;
        }

        Assert.assertEquals(forUnknown.status(), forKnown.status(),
                "ACCOUNT ENUMERATION: POST /auth/checkUserExists is public and answered "
                        + forKnown.status() + " for a registered address versus "
                        + forUnknown.status() + " for an unknown one — an unauthenticated oracle "
                        + "for which addresses have Trimio accounts. Compare "
                        + "passwordController.sendOtp, which was deliberately made "
                        + "indistinguishable for this exact reason.");

        String knownBody = body(forKnown);
        Assert.assertFalse(knownBody.contains("userId") || knownBody.contains("userTypeId"),
                "ACCOUNT DISCLOSURE: checkUserExists returned the internal user id and role to an "
                        + "unauthenticated caller: " + knownBody + ". userTypeId identifies which "
                        + "addresses are professionals and admins.");
    }

    private static String body(ApiClient.Response response) {
        return response.body() == null ? "" : response.body();
    }
}
