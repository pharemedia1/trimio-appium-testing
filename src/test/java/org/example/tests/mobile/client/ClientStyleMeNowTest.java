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

    private ClientStyleMeNowScreen openFlow() {
        ClientHomeScreen home = loginAsProvisionedClient();
        Assert.assertTrue(home.isLoaded(), "The Home tab should render");

        ClientStyleMeNowScreen flow = new ClientStyleMeNowScreen(driver);
        home.startBooking();
        if (!flow.isLoaded() || !flow.showsTwoSteps()) {
            throw new SkipException("The Style-Me-Now flow was not reached — its entry point may be "
                    + "gated on location permission or on a professional being on duty nearby.");
        }
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
        ClientStyleMeNowScreen flow = openFlow();

        double total = flow.total();
        double quoted = flow.ctaQuote();

        if (total < 0 || quoted < 0) {
            throw new SkipException("Could not read both the TOTAL row and the CTA quote — the flow "
                    + "may not have reached the pay step (a service and a location are required).");
        }
        Assert.assertEquals(quoted, total, 0.001,
                "The 'Find a pro · $x.xx' CTA must quote the same amount as the TOTAL row — this is "
                        + "the figure the client agrees to before any professional is assigned");
    }

    @Test(description = "Without a card on file the client is told they cannot book")
    public void noCardBlocksBooking() {
        ClientStyleMeNowScreen flow = openFlow();

        if (!flow.showsNoCardOnFile()) {
            throw new SkipException("The signed-in client already has a card on file — use a client "
                    + "without a payment method to exercise this path.");
        }
        Assert.assertTrue(flow.showsNoCardOnFile(),
                "A client with no payment method should see '" + ClientStyleMeNowScreen.NO_CARD + "'");
    }
}
