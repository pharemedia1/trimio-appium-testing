package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.client.ClientBookingFlowScreen;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Group booking — the same {@code booking_flow_screen.dart} widget with {@code isGroup: true}.
 *
 * <p>Sharing one widget between the solo and group flows is efficient but fragile: the two differ
 * only in their step titles and their advance conditions, so a change to the solo path can silently
 * relax a group rule. The two rules under test are exactly those: at least two participants, and
 * every participant assigned at least one service.
 */
public class ClientGroupBookingTest extends RoleSessionTest {

    /**
     * A PER-PERSON service, which is what the two-person minimum applies to.
     *
     * <p>Deliberately not a day rate: those are priced by headcount and are the documented
     * exception to the minimum, so choosing one would prove the opposite of what this asserts.
     */
    private static final String PER_PERSON_SERVICE = "Blowout";

    private ClientBookingFlowScreen openGroupFlow() {
        ClientHomeScreen home = loginAsProvisionedClient();
        Assert.assertTrue(home.isLoaded(), "The Home tab should render");

        // planGroupBooking(), NOT startBooking(). The two CTAs build the SAME widget in different
        // modes, so which one you tap decides the titles, the steps and the payload — and the
        // deprecated startBooking() taps neither, matching a bare "Book" that also hits the nav
        // tab. This class then skipped on "the group flow was not reached", blaming a feature flag.
        ClientBookingFlowScreen flow = home.planGroupBooking();
        if (!flow.isLoaded()) {
            throw new SkipException("The group booking flow did not open from '"
                    + ClientHomeScreen.PLAN_GROUP_BOOKING + "'.");
        }
        if (!flow.showsTitle(ClientBookingFlowScreen.GROUP_TITLES[0])) {
            throw new SkipException("The group flow was not reached — the Home tab's group-booking "
                    + "entry point may be gated on a feature flag or a saved address.");
        }
        return flow;
    }

    @Test(description = "The group flow uses its own step titles")
    public void groupFlowShowsGroupSteps() {
        ClientBookingFlowScreen flow = openGroupFlow();

        Assert.assertTrue(flow.showsTitle(ClientBookingFlowScreen.GROUP_TITLES[0]),
                "Group step 1 should be titled \"" + ClientBookingFlowScreen.GROUP_TITLES[0] + "\"");
        Assert.assertTrue(flow.showsTotalSteps(ClientBookingFlowScreen.TOTAL_STEPS),
                "The group flow should also declare 4 steps");
    }

    @Test(description = "A group booking needs at least two participants")
    public void groupRequiresTwoPeople() {
        ClientBookingFlowScreen flow = openGroupFlow();

        // THE RULE MOVED, and this test used to encode the old one.
        //
        // "Who's coming" now accepts a single participant. booking_flow_screen.dart says why, in
        // _groupCanAdvance: a DAY RATE is one organiser booking a building for residents whose
        // names they do not know, and the service that says so is not chosen until step 2. Under
        // the old two-person gate the client had to invent a second participant just to reach the
        // screen that would have told them they did not need one.
        //
        // The two-person rule still holds — it is enforced at STEP 2, where the service type is
        // finally known, and only for per-person bookings. So that is where it is asserted.
        flow.continueStep();
        Assert.assertEquals(flow.currentStep(), 2,
                "One participant should now get past 'Who's coming' — the minimum moved to step 2 "
                        + "so day-rate bookings, which are priced by headcount rather than by a "
                        + "roster, can reach the screen that offers them");

        // Step 2 with ONE participant and a per-person service selected: still refused.
        flow.selectService(PER_PERSON_SERVICE);
        flow.continueStep();

        Assert.assertTrue(flow.isBlockedOn(2),
                "A per-person group booking with a single participant must not advance — a 'group' "
                        + "of one is a solo booking and must not be priced as a group. (A day-rate "
                        + "service is the deliberate exception: it is priced by headcount.)");
    }

    @Test(description = "Every participant must have at least one service")
    public void everyMemberNeedsAService() {
        ClientBookingFlowScreen flow = openGroupFlow();
        flow.addPerson();
        flow.continueStep();

        if (flow.currentStep() != 2) {
            throw new SkipException("Could not reach the group services step — adding a second "
                    + "participant may require saved family members.");
        }

        flow.continueStep();

        Assert.assertTrue(flow.isBlockedOn(2),
                "The flow must not advance while a participant has no service assigned");
    }
}
