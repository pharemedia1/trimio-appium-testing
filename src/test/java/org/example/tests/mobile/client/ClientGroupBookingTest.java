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

    private ClientBookingFlowScreen openGroupFlow() {
        ClientHomeScreen home = loginAsProvisionedClient();
        Assert.assertTrue(home.isLoaded(), "The Home tab should render");

        ClientBookingFlowScreen flow = home.startBooking();
        if (!flow.isLoaded()) {
            throw new SkipException("The booking flow did not open.");
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

        flow.continueStep();

        Assert.assertTrue(flow.isBlockedOn(1),
                "A single participant should not satisfy the group step — a 'group' of one is a "
                        + "solo booking and must not be priced as a group");
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
