package org.example.tests.api;

import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * API-01 — the membership endpoints the client app reads on every launch.
 *
 * <p>Membership is the subscription that gives a client credits against bookings, so these routes
 * sit on the money path even though none of them takes a payment: the overview decides what the
 * app believes the client is entitled to, and the credit figures decide what a booking costs.
 *
 * <p>The suite exists mainly because of {@link #invoiceListingDoesNotFailForAMemberWithoutStripe},
 * which holds a defect found by sweeping these routes across roles. The rest establishes the
 * baseline around it — without them, a green invoice test would not distinguish "fixed" from
 * "the whole membership API is down".
 */
public class MembershipApiTest extends ApiBaseTest {

    @Test(description = "API-010: the membership catalogue is readable by any signed-in client")
    public void catalogueIsReadable() {
        ApiClient.Response response = api.as(TrimioApi.CLIENT).get("/memberships/catalog");
        Assert.assertTrue(response.isSuccess(),
                "/memberships/catalog answered " + response.status() + " for a client: "
                        + response.body());
        Assert.assertNotNull(response.json(),
                "The membership catalogue should be JSON: " + response.body());
    }

    @Test(description = "API-011: the client-facing membership reads all answer")
    public void clientMembershipReadsAnswer() {
        SoftAssert soft = new SoftAssert();
        TrimioApi.Caller client = api.as(TrimioApi.CLIENT);

        for (String path : List.of("/memberships/overview", "/memberships/plans",
                "/memberships/plan-options", "/memberships/account-credit")) {
            ApiClient.Response response = client.get(path);
            soft.assertTrue(response.status() < 500,
                    path + " answered " + response.status() + " for a client — a 5xx here means "
                            + "the membership tab cannot render at all: " + response.body());
        }
        soft.assertAll();
    }

    /**
     * API-012 — a member whose subscription carries no Stripe customer id must not crash the
     * invoice listing.
     *
     * <p><b>This is a real defect, found by this suite on 2026-09-21 and not yet fixed.</b>
     *
     * <p>{@code services/membership.service.js listInvoices} does:
     * <pre>
     *   const sub = await getActiveSubscription(userId);
     *   const inv = await stripe.invoices.list({ customer: sub.stripe_customer_id, limit: 12 });
     * </pre>
     * with no check that {@code stripe_customer_id} is set. It is not always set: seeded user
     * 41504 holds an <em>active</em> {@code membership_subscriptions} row whose
     * {@code stripe_customer_id} is NULL — the shape a comped or manually-granted membership takes,
     * since nothing charged the member and so no Stripe customer was ever created. The call then
     * goes to Stripe with {@code customer: null} and the route answers
     * {@code 500 {"error":"Server error","code":"INTERNAL_ERROR"}}.
     *
     * <p>A member in that state cannot open their billing history at all, and the client gets a
     * server error rather than an empty list. The correct answer is an empty list: a membership
     * that was never billed has no invoices, which is a fact rather than a failure.
     *
     * <p>Note which caller finds it. A client with no subscription gets a clean 404 from
     * {@code getActiveSubscription}, so the obvious test passes; it takes a caller who <em>has</em>
     * a subscription without a Stripe customer to reach the line that breaks. That is why this
     * runs as the professional — the role is incidental, the data shape is the point.
     */
    @Test(description = "API-012: invoice listing handles a membership that was never billed")
    public void invoiceListingDoesNotFailForAMemberWithoutStripe() {
        if (!api.has(TrimioApi.PROFESSIONAL)) {
            throw new SkipException("No 'professional' account configured. This needs a caller "
                    + "holding an ACTIVE membership whose stripe_customer_id is NULL — the seeded "
                    + "professional (user 41504) is one.");
        }
        ApiClient.Response response = api.as(TrimioApi.PROFESSIONAL).get("/memberships/invoices");

        Assert.assertNotEquals(response.status(), 500,
                "GET /memberships/invoices answered 500 for a member whose active subscription has "
                        + "no stripe_customer_id. membership.service.js listInvoices passes that "
                        + "null straight to stripe.invoices.list({ customer }) without checking it. "
                        + "A membership that was never billed has no invoices — that is an empty "
                        + "list, not a server error, and right now the member cannot open their "
                        + "billing history at all. Body: " + response.body());
        Assert.assertTrue(response.status() < 500,
                "/memberships/invoices answered " + response.status() + ": " + response.body());
    }

    /**
     * API-013 — the cancellation quote must not 500 either.
     *
     * <p>Same family of bug and the same consequence if it appears: the quote is what the app
     * shows before a member confirms cancelling, so a crash there strands them mid-decision. A
     * 404 for a client with no subscription is correct and accepted.
     */
    @Test(description = "API-013: the cancellation quote answers cleanly with or without a plan")
    public void cancellationQuoteAnswersCleanly() {
        SoftAssert soft = new SoftAssert();
        for (String role : List.of(TrimioApi.CLIENT, TrimioApi.PROFESSIONAL)) {
            if (!api.has(role)) {
                continue;
            }
            ApiClient.Response response = api.as(role).get("/memberships/cancel/quote");
            soft.assertTrue(response.status() < 500,
                    "/memberships/cancel/quote answered " + response.status() + " as " + role
                            + " — the member is shown this before confirming a cancellation, so a "
                            + "crash strands them mid-decision: " + response.body());
        }
        soft.assertAll();
    }

    /**
     * API-014 — membership data is scoped to the caller, not to a parameter.
     *
     * <p>Every route here reports "your" membership, and the only correct source for whose it is
     * is the token. Passing somebody else's id must change nothing — the same shape
     * {@code /reports/client/:clientId} already has, where the path segment is ignored in favour of
     * the authenticated identity.
     */
    @Test(description = "API-014: membership reads ignore a user id supplied by the caller")
    public void membershipReadsAreScopedToTheToken() {
        TrimioApi.Caller client = api.as(TrimioApi.CLIENT);
        ApiClient.Response own = client.get("/memberships/overview");
        ApiClient.Response spoofed = client.get("/memberships/overview?userId=41507&user_id=41507");

        Assert.assertEquals(spoofed.status(), own.status(),
                "/memberships/overview answered differently when a userId was supplied in the "
                        + "query string (" + spoofed.status() + " vs " + own.status() + ").");
        Assert.assertEquals(spoofed.body(), own.body(),
                "BROKEN OBJECT-LEVEL AUTHORIZATION: /memberships/overview returned different data "
                        + "when the caller named another user's id. The route must resolve the "
                        + "member from the token and ignore anything the caller supplies.");
    }
}
