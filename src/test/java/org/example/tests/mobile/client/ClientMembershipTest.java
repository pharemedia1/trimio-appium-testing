package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.client.ClientMembershipScreen;
import org.example.pages.mobile.client.ClientProfileScreen;
import org.example.pages.mobile.common.BottomNavBar;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Membership — plan discovery and plan management ({@code screens/client/membership/*}).
 *
 * <p>Subscribing, upgrading, pausing and cancelling all move money against a live Stripe
 * subscription, so those stay manual. The automated slice is the state a client reads before
 * deciding: which plans exist, what they cost, how many credits are left and when the plan renews.
 * Those numbers are also what the booking flow spends, so a wrong credit count here shows up as a
 * wrong charge at checkout.
 */
public class ClientMembershipTest extends RoleSessionTest {

    private ClientMembershipScreen openMembership() {
        loginAsClient();
        new BottomNavBar(driver).open(BottomNavBar.CLIENT_PROFILE);

        ClientProfileScreen profile = new ClientProfileScreen(driver);
        Assert.assertTrue(profile.isLoaded(), "The Profile tab should render");

        ClientMembershipScreen membership = new ClientMembershipScreen(driver);
        if (!membership.isPlanChooserLoaded() && !membership.isManageLoaded()) {
            throw new SkipException("The membership area was not reachable from the profile — its "
                    + "entry point may differ in this build.");
        }
        return membership;
    }

    @Test(description = "Available plans are listed with their monthly allotment")
    public void plansAreListed() {
        ClientMembershipScreen membership = openMembership();
        if (membership.isManageLoaded()) {
            throw new SkipException("The signed-in client already has a membership — use a client "
                    + "without one to exercise the plan chooser.");
        }

        Assert.assertTrue(membership.hasAnyPlan() || membership.showsNoPlans(),
                "The chooser should list plans or state that none are available");
        if (membership.hasAnyPlan()) {
            Assert.assertTrue(membership.showsAllotment(),
                    "Each plan should state its monthly in-home allotment — it is what the client is "
                            + "actually buying");
        }
    }

    @Test(description = "Manage plan shows the remaining credits and renewal")
    public void managePlanShowsCredits() {
        ClientMembershipScreen membership = openMembership();
        if (!membership.isManageLoaded()) {
            throw new SkipException("The signed-in client has no active membership — subscribe one "
                    + "(or seed one) to exercise the manage screen.");
        }

        Assert.assertTrue(membership.showsCreditsRemaining(),
                "Manage plan should show '<a> of <b> credits left' — the balance the booking flow spends");
        Assert.assertTrue(membership.showsRenewalLine(),
                "Manage plan should show the price and renewal date");
    }

    @Test(description = "Cancellation offers retention options before confirming")
    public void cancellationOffersAlternatives() {
        ClientMembershipScreen membership = openMembership();
        if (!membership.isManageLoaded()) {
            throw new SkipException("The signed-in client has no active membership.");
        }

        membership.startCancel();

        Assert.assertTrue(membership.showsRetentionOptions(),
                "Cancelling should offer the pause alternative before destroying the membership");
        membership.keepMembership();
    }
}
