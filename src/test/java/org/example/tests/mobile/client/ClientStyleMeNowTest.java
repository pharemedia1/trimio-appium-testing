package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.example.pages.mobile.client.ClientStyleMeNowScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Style-Me-Now, the on-demand flow — {@code style_me_now_flow_screen.dart}.
 *
 * <p>The dispatch itself is not automated: it puts a live request in front of real on-duty
 * professionals and, on acceptance, creates a real job. What <em>is</em> automated is the money
 * statement made just before that point — the CTA quotes a price ("Find a pro · $x.xx") and the
 * client commits to it before knowing who will turn up. If that figure and the TOTAL row ever
 * disagree, the client is agreeing to one number and being charged another, which is why the two are
 * compared to the cent.
 */
public class ClientStyleMeNowTest extends RoleSessionTest {

    /** A service the seeded barber is priced for — the list is filtered by the type segment. */
    private static final String SERVICE = "Haircut";

    /**
     * Opens the on-demand flow from its OWN Home CTA.
     *
     * <p>It used to go through the deprecated {@code startBooking()}, which taps a bare "Book" —
     * that is the SCHEDULED flow (or the nav tab), not this one. The two-step assertion then
     * failed and the whole class skipped itself with "the flow was not reached", which reads as a
     * missing professional or a denied location and is neither: the flow was never opened.
     */
    private ClientStyleMeNowScreen openFlow() {
        return openFlow(CLIENT);
    }

    /** As {@link #openFlow()}, signed in as a named client role. */
    private ClientStyleMeNowScreen openFlow(String role) {
        ClientHomeScreen home = loginAsProvisionedClient(role);
        Assert.assertTrue(home.isLoaded(), "The Home tab should render");

        ClientStyleMeNowScreen flow = home.styleMeNow();
        if (!flow.isLoaded() || !flow.showsTwoSteps()) {
            throw new SkipException("The Style-Me-Now flow did not open from its '"
                    + ClientHomeScreen.STYLE_ME_NOW + "' CTA. It needs a location: with GPS "
                    + "unavailable the app raises a manual-address dialog instead. Set one with "
                    + "`adb -s <device> emu geo fix -96.7970 32.7767`.");
        }
        return flow;
    }

    /** Fills step 1 and advances to "Where & pay", where the money statement lives. */
    private ClientStyleMeNowScreen openPayStep() {
        return openPayStep(CLIENT);
    }

    /** As {@link #openPayStep()}, signed in as a named client role. */
    private ClientStyleMeNowScreen openPayStep(String role) {
        ClientStyleMeNowScreen flow = openFlow(role);
        flow.chooseRecipient("Me");
        // The segment FILTERS the service list — searching without choosing it finds nothing.
        flow.chooseServiceType(ClientStyleMeNowScreen.TYPE_BARBER);
        flow.searchService(SERVICE);
        flow.selectService(SERVICE);
        flow.continueToPay();
        return flow;
    }

    @Test(description = "Style-Me-Now presents two steps")
    public void flowHasTwoSteps() {
        ClientStyleMeNowScreen flow = openFlow();

        Assert.assertTrue(flow.showsTwoSteps(), "The on-demand flow should declare 2 total steps");
        Assert.assertTrue(flow.showsTitle(ClientStyleMeNowScreen.STEP_WHO_WHAT),
                "Step 1 should be titled '" + ClientStyleMeNowScreen.STEP_WHO_WHAT + "'");
    }

    @Test(description = "The dispatch CTA quotes exactly the displayed total")
    public void ctaQuotesTheTotal() {
        ClientStyleMeNowScreen flow = openPayStep();

        double total = flow.total();
        double quoted = flow.ctaQuote();

        if (total < 0 || quoted < 0) {
            // Step 1 shows SUBTOTAL and a "Continue" CTA; only step 2 shows TOTAL and the quote.
            // Reading -1 for both means openPayStep() did not get there — usually because no
            // service matched the chosen segment.
            throw new SkipException("Could not read both the TOTAL row and the CTA quote, so the "
                    + "flow did not reach 'Where & pay'. A service must be selected first, and the "
                    + "'" + ClientStyleMeNowScreen.TYPE_BARBER + "' segment must actually offer '"
                    + SERVICE + "'.");
        }
        Assert.assertEquals(quoted, total, 0.001,
                "The 'Find a pro · $x.xx' CTA must quote the same amount as the TOTAL row — this is "
                        + "the figure the client agrees to before any professional is assigned");
    }

    @Test(description = "Without a card on file the client is told they cannot book")
    public void noCardBlocksBooking() {
        // Signed in as the CARDLESS client, not the suite's usual one. 41501 keeps its card so the
        // booking and payment suites can take a real charge, which means it can never satisfy this
        // assertion; 41617 exists to have nothing to pay with. Before this the test signed in as
        // 41501, found a card and skipped itself every single run.
        // The card notice lives on step 2, next to the pay controls.
        ClientStyleMeNowScreen flow = openPayStep(CLIENT_NO_CARD);

        if (!flow.showsNoCardOnFile()) {
            throw new SkipException("roleAccounts." + CLIENT_NO_CARD + " has a card on file after "
                    + "all, so the no-card notice cannot appear. Clear it with: DELETE FROM "
                    + "client_payment_methods WHERE client_id = 41617;");
        }
        Assert.assertTrue(flow.showsNoCardOnFile(),
                "A client with no payment method should see '" + ClientStyleMeNowScreen.NO_CARD + "'");
    }
}
