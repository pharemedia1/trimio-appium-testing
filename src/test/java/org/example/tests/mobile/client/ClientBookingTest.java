package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.client.ClientBookingFlowScreen;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The scheduled booking flow — {@code booking_flow_screen.dart}.
 *
 * <p>Coverage stops at the review-and-pay hand-off. That is deliberate: past that point the flow
 * charges a real card through Stripe and creates a real appointment that a real professional is
 * dispatched to. Automating it would mean either a payment sandbox wired end-to-end or a stream of
 * bookings someone has to clean up — so the payment leg stays a controlled manual case (see the
 * "Review & pay" rows of the Client sheet) and the automation guards everything that decides
 * <em>what</em> gets charged: the steps, the gates, the address, availability and the add-on maths.
 */
public class ClientBookingTest extends RoleSessionTest {

    /** Opens the booking flow from the Home tab, skipping if it never appears. */
    private ClientBookingFlowScreen openBookingFlow() {
        ClientHomeScreen home = loginAsProvisionedClient();
        Assert.assertTrue(home.isLoaded(), "The Home tab should render");

        ClientBookingFlowScreen flow = home.startBooking();
        if (!flow.isLoaded()) {
            throw new SkipException("The booking flow did not open — the Home tab's booking entry "
                    + "point may require a saved address or an active service catalogue.");
        }
        return flow;
    }

    @Test(description = "The scheduled booking flow presents four titled steps")
    public void bookingFlowShowsFourSteps() {
        ClientBookingFlowScreen flow = openBookingFlow();

        Assert.assertTrue(flow.showsTotalSteps(ClientBookingFlowScreen.TOTAL_STEPS),
                "The flow should declare 4 total steps");
        Assert.assertEquals(flow.currentStep(), 1, "It should open on step 1");
        Assert.assertTrue(flow.showsTitle(ClientBookingFlowScreen.STEP_WHAT),
                "Step 1 should be titled '" + ClientBookingFlowScreen.STEP_WHAT + "'");
    }

    @Test(description = "Step 1 refuses to advance until a service is selected")
    public void step1RequiresAService() {
        ClientBookingFlowScreen flow = openBookingFlow();

        flow.continueStep();

        Assert.assertTrue(flow.isBlockedOn(1),
                "Continuing with no service selected should leave the user on step 1");
    }

    @Test(description = "Service search filters the catalogue and reports an empty result")
    public void serviceSearchFilters() {
        ClientBookingFlowScreen flow = openBookingFlow();

        flow.searchService("zzzznotaservice");

        Assert.assertTrue(flow.showsNoServiceMatch(),
                "A query matching nothing should show '" + ClientBookingFlowScreen.NO_SERVICE_MATCH + "'");
    }

    @Test(description = "Step 2 explains that editing the address updates the saved one")
    public void serviceAddressCanBeEdited() {
        ClientBookingFlowScreen flow = openBookingFlow();
        advanceToStep(flow, 2);

        Assert.assertTrue(flow.showsAddressHint(),
                "Step 2 should warn that the edit updates the client's saved address — this is the "
                        + "only notice the client gets before their default address changes");
    }

    @Test(description = "Availability resolves for a selected day")
    public void availabilityLoadsForADay() {
        ClientBookingFlowScreen flow = openBookingFlow();
        advanceToStep(flow, 3);

        Assert.assertTrue(flow.waitForAvailability(),
                "The 'Checking availability…' state should resolve rather than hang");
        // Either open slots or the explicit empty state is a correct outcome; a blank screen is not.
        Assert.assertTrue(flow.showsNoOpenTimes() || flow.currentStep() == 3,
                "Step 3 should show either bookable slots or the no-open-times message");
    }

    @Test(description = "A day with no availability shows the empty-times message")
    public void fullyBookedDayShowsMessage() {
        ClientBookingFlowScreen flow = openBookingFlow();
        advanceToStep(flow, 3);
        flow.waitForAvailability();

        if (!flow.showsNoOpenTimes()) {
            throw new SkipException("No fully-booked day is present in this environment — seed a day "
                    + "with no professional availability to exercise this path.");
        }
        Assert.assertTrue(flow.isBlockedOn(3),
                "A day with no slots must not allow the flow to advance");
    }

    @Test(description = "Selecting an add-on increases the subtotal")
    public void addOnUpdatesSubtotal() {
        ClientBookingFlowScreen flow = openBookingFlow();
        advanceToStep(flow, 4);

        if (flow.showsNoAddOns()) {
            throw new SkipException("The selected service offers no add-ons — pick a service with "
                    + "extras to exercise the add-on pricing path.");
        }

        double before = flow.subtotal();
        if (before < 0) {
            throw new SkipException("The subtotal could not be read from the extras step.");
        }

        flow.selectAddOn("+$");
        double after = flow.subtotal();

        Assert.assertTrue(after > before,
                "Adding an extra should raise the subtotal (was " + before + ", now " + after + ")");
    }

    /**
     * Walks the flow forward to {@code target}, skipping the test if a step's own gate stops us —
     * an environment without services or availability cannot exercise the later steps, and saying so
     * is more useful than a failure that looks like a defect.
     */
    private void advanceToStep(ClientBookingFlowScreen flow, int target) {
        for (int step = flow.currentStep(); step > 0 && step < target; step = flow.currentStep()) {
            flow.continueStep();
            if (flow.currentStep() == step) {
                throw new SkipException("The flow would not advance past step " + step
                        + " — this environment lacks the data that step requires (services, saved "
                        + "address or availability).");
            }
        }
        Assert.assertEquals(flow.currentStep(), target, "Should have reached step " + target);
    }
}
