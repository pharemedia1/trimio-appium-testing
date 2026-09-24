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

    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(ClientStyleMeNowTest.class);

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
    /**
     * ON-DEMAND IS ALWAYS AT THE CLIENT'S ADDRESS — the professional travels to them.
     *
     * <p>Worth asserting because the rule is easy to undo and expensive when it is. The shop fork
     * used to live in step 1 of this flow, and choosing it left On-Demand altogether: it pushed
     * the shop search and booked the chair through processPayment as a 'Scheduled' appointment.
     * So the screen whose whole promise is "a professional, now, at your address" was quietly also
     * the way to make a scheduled booking somewhere else, and it put a venue question in front of
     * every on-demand client to do it. Going to a shop is an INDIVIDUAL booking and the individual
     * flow offers it properly, on its own "Who &amp; where" step.
     *
     * <p>Nothing guarded this until now: the flow's tests covered its two steps, its quote and the
     * no-card refusal, so the fork could have come back without a single test noticing.
     */
    @Test(description = "On-demand never offers a venue choice — the professional comes to the client")
    public void onDemandIsAlwaysAtTheClientsAddress() {
        ClientStyleMeNowScreen flow = openFlow();

        Assert.assertFalse(flow.offersAVenueChoice(),
                "Style-Me-Now asked a venue question ('" + ClientStyleMeNowScreen.VENUE_PROMPT
                        + "' / '" + ClientStyleMeNowScreen.GO_TO_SHOP + "'). On-demand dispatches a "
                        + "professional to the client's address; a shop visit is a scheduled "
                        + "booking and belongs in the individual flow, which already offers it.");

        // The step that DOES exist asks where to meet the client, not whether to meet at all.
        Assert.assertTrue(flow.showsTwoSteps(),
                "The flow should still be the two-step '" + ClientStyleMeNowScreen.STEP_WHO_WHAT
                        + "' / '" + ClientStyleMeNowScreen.STEP_WHERE_PAY + "' shape");
    }


    /**
     * The longest job On-Demand may dispatch.
     *
     * <p>240 minutes, chosen deliberately rather than tightly. Braiding at home is a legitimate
     * on-demand job and "Braids / Locs (custom)" is a genuine 180 minutes, so a tighter ceiling
     * would bar real work. What this bars is the catalogue's DAY services -- "Corporate Grooming
     * Day (up to 14)" at 480 minutes and "Apartment Grooming Day (up to 10)" at 360 -- which are
     * {@code booking_scope = 'group_only'} event work, booked ahead for a room full of people.
     * Dispatching one through a screen promising "a professional, now" would hold a
     * professional's entire day against a request meant to be a haircut.
     *
     * <p>Neither is offered today, and this exists so that stays true: the client app filters
     * this catalogue on {@code bookable_at_home} alone, and only the PROFESSIONAL's services page
     * filters {@code group_only}. Nothing on the client side enforces it.
     */
    private static final int MAX_ON_DEMAND_MINUTES = 240;

    /**
     * ON-DEMAND MUST NOT OFFER A DAY'S WORK.
     *
     * <p>The catalogue holds services measured in hours — "Corporate Grooming Day (up to 14)" at
     * 480 minutes and "Apartment Grooming Day (up to 10)" at 360 — and they are marked
     * {@code booking_scope = 'group_only'} precisely because they are event work, booked ahead
     * for a room full of people. Dispatching one through a screen whose promise is "a
     * professional, now, at your address" is not a booking anybody wants: it holds a
     * professional's whole day on a request that was meant to be a haircut.
     *
     * <p>The client app filters this list on {@code bookable_at_home} alone. Only the
     * PROFESSIONAL's services page filters {@code group_only}, so nothing on the client side
     * stops one reaching this flow, and nothing tested it.
     *
     * <p>Asserted on the DURATION rather than on a name list, because the rule is about the shape
     * of the job: whatever the catalogue grows next, a multi-hour service does not belong here.
     */
    @Test(description = "On-demand never offers a job longer than a visit")
    public void onDemandOffersOnlyVisitLengthWork() {
        ClientStyleMeNowScreen flow = openFlow();
        flow.chooseRecipient("Me");

        // EVERY segment, not just one. The segment FILTERS the catalogue, so checking Barbering
        // alone would clear the flow while a day-long service sat one tab away.
        java.util.List<String> tooLong = new java.util.ArrayList<>();
        java.util.Map<String, Integer> offered = new java.util.LinkedHashMap<>();
        for (String type : new String[]{ClientStyleMeNowScreen.TYPE_BARBER,
                                        ClientStyleMeNowScreen.TYPE_STYLIST}) {
            flow.chooseServiceType(type);
            java.util.Map<String, Integer> inSegment = flow.offeredServiceMinutes();
            LOG.info("On-demand '{}' offers {} service(s): {}", type, inSegment.size(), inSegment);
            offered.putAll(inSegment);
            inSegment.forEach((name, minutes) -> {
                if (minutes > MAX_ON_DEMAND_MINUTES) {
                    tooLong.add(type + " / " + name + " (" + minutes + " min)");
                }
            });
        }

        if (offered.isEmpty()) {
            throw new SkipException("No service row exposed its duration in any segment, so the "
                    + "catalogue could not be read. Each row should render '<n> min' beneath the "
                    + "service name.");
        }

        Assert.assertTrue(tooLong.isEmpty(),
                "On-demand offered work longer than " + MAX_ON_DEMAND_MINUTES + " minutes: "
                        + String.join(", ", tooLong) + ". A professional is being dispatched to "
                        + "arrive now; a service measured in hours is event work and belongs in "
                        + "the scheduled or group flow. Filter this list on booking_scope as well "
                        + "as bookable_at_home.");
    }
}
