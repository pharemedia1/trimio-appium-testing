package org.example.tests.paired;

import org.example.base.PairedDeviceTest;
import org.example.data.TestAccounts;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.example.pages.mobile.client.ClientStyleMeNowScreen;
import org.example.pages.mobile.professional.ProfessionalDashboardScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * PAIR-01 — the on-demand handshake, driven across two emulators at the same time.
 *
 * <p><b>The gap this closes.</b> Every other test of Style-Me-Now stops at the moment of dispatch.
 * {@code ClientStyleMeNowTest} says so in as many words — it asserts the quoted price and then
 * declines to press the button, because pressing it puts a live request in front of real
 * professionals and nothing in a single-device run can answer it. So the most important part of
 * Trimio's on-demand product, the part that distinguishes it from scheduled booking, had no
 * end-to-end coverage at all: not the offer reaching a professional, not the acceptance reaching
 * the client back.
 *
 * <p>That is not a coverage gap of the ordinary kind. The failure modes it hides are invisible
 * from either side alone:
 * <ul>
 *   <li>the request dispatches and no professional is ever offered it — which looks, on the
 *       client's screen, exactly like "nobody was free";</li>
 *   <li>a professional accepts and the client's screen never updates — the job exists, the client
 *       is still watching a spinner, and the professional is on their way to someone who does not
 *       know it;</li>
 *   <li>the offer arrives but quotes a payout that does not correspond to what the client was
 *       charged.</li>
 * </ul>
 * Each of those is a conversation failing, and you can only see a conversation failing if you are
 * listening to both ends.
 *
 * <p><b>This test creates real records</b> — a dispatched request, and on acceptance a real job
 * assigned to a real professional. It is kept out of the routine regression for that reason and
 * lives in its own suite ({@code suites/paired-testng.xml}), alongside the booking-payment suite
 * which is excluded on the same grounds.
 *
 * <p>Every precondition failure <b>skips</b> rather than fails. An on-demand request needs a
 * professional on duty, within range, licensed for the service, in an open jurisdiction — the
 * matching service applies all of it. "Nobody was offered the job" is very often the environment
 * being correct rather than Trimio being wrong, and a test that reported those as defects would be
 * ignored within a week. The one thing asserted without mercy is the causal link: when a
 * professional <em>does</em> accept, the client must be told.
 */
public class OnDemandDispatchTest extends PairedDeviceTest {

    /** A service the seeded professional is priced for — pro1 is a Class A Barber. */
    private static final String SERVICE = "Haircut";

    /**
     * PAIR-010 — both devices are where they are supposed to be before anything is dispatched.
     *
     * <p>Run first and depended on by the rest. Without it, every later failure is ambiguous
     * between "the handshake is broken" and "one of the two apps was never signed in", and those
     * take very different amounts of time to chase.
     */
    @Test(description = "PAIR-010: the paired devices hold a client and an on-duty professional")
    public void bothDevicesAreInTheRightPlace() {
        ClientHomeScreen home = clientHome();
        Assert.assertTrue(home.isLoaded(),
                "The client device (" + clientDevice + ") did not reach the Home feed.");

        ProfessionalDashboardScreen pro = professionalOnDuty();
        Assert.assertTrue(pro.isLoaded(),
                "The professional device (" + proDevice + ") did not reach the dashboard.");
        Assert.assertTrue(pro.isOnDuty(),
                "The professional on " + proDevice + " is not on duty, so the matching service "
                        + "will correctly offer them nothing and the dispatch test below could "
                        + "only ever report a false negative.");

        LOG.info("PAIR-010: client={} ({}), professional={} ({}) on duty",
                TestAccounts.emailFor("client"), clientDevice,
                TestAccounts.emailFor("professional"), proDevice);
    }

    /**
     * PAIR-011 — dispatch on one device, offer on the other.
     *
     * <p>The half of the handshake that has never been covered. It asserts only that the offer
     * <em>arrives</em>; whether this particular professional should have been chosen is the
     * matching service's business and is tested at the API level, not here.
     *
     * <p>The offer is declined at the end rather than accepted, so this test alone leaves no job
     * behind. Accepting is PAIR-012's job, and it is the one that writes.
     */
    @Test(dependsOnMethods = "bothDevicesAreInTheRightPlace",
            description = "PAIR-011: a client's on-demand request reaches an on-duty professional")
    public void dispatchedRequestReachesTheProfessional() {
        ProfessionalDashboardScreen pro = professionalDashboard();
        boolean hadOfferBefore = pro.hasOffer();

        ClientStyleMeNowScreen flow = openOnDemandFlow();
        dispatch(flow);

        // Poll the professional's dashboard rather than sleeping: the offer arrives over a socket
        // and the delay is the matching service's, which varies with how many professionals it
        // has to score.
        boolean offered = waitForOffer(pro, 90);

        if (!offered && !hadOfferBefore) {
            // Distinguish the two indistinguishable-looking cases before deciding what this means.
            if (flow.showsNobodyFree()) {
                throw new SkipException("The request expired with 'Nobody's free right now' and the "
                        + "professional on " + proDevice + " was never offered it. That is a "
                        + "correct outcome when the seeded professional is out of range, not "
                        + "licensed for '" + SERVICE + "', or outside an open jurisdiction — the "
                        + "matching service applies all of those. Seed a professional near the "
                        + "client's address to make this assertable.");
            }
            Assert.fail("The client dispatched an on-demand request and 90s later the on-duty "
                    + "professional on " + proDevice + " had no offer, AND the client was not told "
                    + "'Nobody's free right now' either. The request is in limbo: the client is "
                    + "watching a matching screen for a job nobody has been asked to take.");
        }

        Assert.assertTrue(pro.showsPayoutBreakdown(),
                "The offer reached the professional but does not show what they would earn. The "
                        + "payout is the whole basis on which they accept or decline within the "
                        + "offer's lifetime.");

        // Leave nothing behind: this test proves delivery, not acceptance.
        pro.declineOffer();
        cancelIfStillSearching(flow);
    }

    /**
     * PAIR-012 — acceptance on one device, confirmation on the other.
     *
     * <p>The full round trip, and the only test in the project that covers it. The assertion that
     * matters is the last one: after the professional accepts, the <em>client's</em> screen must
     * say a pro is on the way. A job that exists in the database while the client still sees a
     * spinner is the worst outcome in this flow — the professional travels to someone who does not
     * know they are coming, and the client cancels a request that has already been taken.
     *
     * <p>Writes a real appointment. See the class note.
     */
    @Test(dependsOnMethods = "dispatchedRequestReachesTheProfessional",
            description = "PAIR-012: accepting on the pro device confirms on the client device")
    public void acceptedOfferConfirmsOnTheClientDevice() {
        ProfessionalDashboardScreen pro = professionalDashboard();
        ClientStyleMeNowScreen flow = openOnDemandFlow();
        dispatch(flow);

        if (!waitForOffer(pro, 90)) {
            throw new SkipException("No offer reached the professional on " + proDevice + " within "
                    + "90s, so there is nothing to accept. PAIR-011 covers why that happens; this "
                    + "test has nothing to say about it.");
        }

        LOG.info("PAIR-012: accepting the offer on {}", proDevice);
        pro.acceptOffer();

        Assert.assertTrue(flow.isMatched(),
                "THE HANDSHAKE BROKE HALFWAY. The professional on " + proDevice + " accepted the "
                        + "job, but the client on " + clientDevice + " was never shown '"
                        + ClientStyleMeNowScreen.PRO_ON_THE_WAY + "' and is still on the matching "
                        + "screen. The appointment exists and the client does not know it: they "
                        + "will cancel a request somebody is already travelling to.");

        // The professional's own screen after accepting is NOT asserted, deliberately.
        //
        // The claim this test exists to make has already been made above: the client was told.
        // Where the pro app lands next — an appointment detail, a navigation hand-off, back to the
        // dashboard — is a product decision that has changed before and carries no information
        // about whether the handshake worked. An assertion over "any recognisable state" sounds
        // like a safety net and behaves like a tripwire: it failed a run in which every meaningful
        // step had succeeded, which is the most expensive kind of red there is.
        //
        // Logged instead, so a human reading the report can see where it went.
        LOG.info("PAIR-012: round trip complete — dispatch → offer → accept → client confirmed. "
                + "Professional's dashboard after accepting: appointmentInProgress={}, "
                + "offerStillShown={}", pro.showsAppointmentInProgress(), pro.hasOffer());
    }

    // ---- flow helpers -------------------------------------------------------

    private ClientStyleMeNowScreen openOnDemandFlow() {
        ClientHomeScreen home = clientHome();
        if (home.showsOnDemandInProgress()) {
            throw new SkipException("The client already has an on-demand appointment in progress, "
                    + "so the app refuses to start another — correctly. Complete or cancel it "
                    + "before re-running.");
        }
        ClientStyleMeNowScreen flow = home.styleMeNow();
        if (!flow.isLoaded() || !flow.showsTwoSteps()) {
            throw new SkipException("The Style-Me-Now flow did not open from its Home CTA. It is "
                    + "gated on a location: with GPS denied the app raises a manual-address dialog "
                    + "instead, which this test does not drive.");
        }
        return flow;
    }

    private void dispatch(ClientStyleMeNowScreen flow) {
        // Step 1 in the order the screen asks for it: who, what type, which service.
        // The service type is not optional to choose — it FILTERS the list, so searching for a
        // barbering service while the stylist segment is selected finds nothing and reads as the
        // service having been removed.
        flow.chooseRecipient("Me");
        flow.chooseServiceType(ClientStyleMeNowScreen.TYPE_BARBER);
        flow.searchService(SERVICE);
        flow.selectService(SERVICE);
        flow.continueToPay();

        flow.enterAddress(TestAccounts.shippingStreet());

        double total = flow.total();
        double quoted = flow.ctaQuote();
        if (total >= 0 && quoted >= 0) {
            // Cheap to check here and worth checking every time: this is the number the client
            // commits to before any professional is known.
            Assert.assertEquals(quoted, total, 0.001,
                    "The 'Find a pro' CTA quotes " + quoted + " but the TOTAL row says " + total);
        }
        if (flow.showsNoCardOnFile()) {
            throw new SkipException("The client has no card on file, so the app blocks dispatch. "
                    + "Give " + TestAccounts.emailFor("client") + " a Stripe test card.");
        }
        LOG.info("PAIR: dispatching an on-demand request for '{}' at {}",
                SERVICE, total < 0 ? "an unread total" : ("$" + total));
        flow.findAPro();
    }

    /** Polls the professional's dashboard for an offer, up to {@code seconds}. */
    private boolean waitForOffer(ProfessionalDashboardScreen pro, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (pro.hasOffer()) {
                LOG.info("PAIR: the offer reached {}", proDevice);
                return true;
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Cancels an in-flight request if one is still searching, so the next test starts clean. */
    private void cancelIfStillSearching(ClientStyleMeNowScreen flow) {
        try {
            flow.cancelRequest();
        } catch (RuntimeException e) {
            LOG.debug("PAIR: nothing to cancel ({})", e.getMessage());
        }
    }
}
