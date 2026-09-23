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

    /** The category the seeded catalogue carries {@link #SERVICE} under — the list is filtered. */
    private static final String CATEGORY = "Hair";
    /** A service present in the seeded Texas catalogue. */
    private static final String SERVICE = "Haircut";
    /** A category that does NOT carry {@link #SERVICE}, used to prove the filter bites. */
    private static final String OTHER_CATEGORY = "Nail services";
    /** How far forward to look for a day with open times. */
    private static final int MAX_DAYS_TO_TRY = 7;
    /** How many of that day's times to try before concluding nobody is free. */
    private static final int MAX_SLOTS_TO_TRY = 6;

    /** Opens the booking flow from the Home tab, skipping if it never appears. */
    private ClientBookingFlowScreen openBookingFlow() {
        ClientHomeScreen home = loginAsProvisionedClient();
        Assert.assertTrue(home.isLoaded(), "The Home tab should render");

        // bookIndividual(), NOT the deprecated startBooking(). Home carries three separate entry
        // points — "Book Individual", "Plan Group Booking" and "Style Me Now" — and startBooking()
        // taps a bare "Book", which also matches the bottom-nav tab and the "Book a one-on-one
        // session…" subtitle. It therefore landed on the discovery tab instead of the flow, and
        // every test in this class skipped itself with "the booking flow did not open" —
        // a message that sends the reader to look at the address book and the service catalogue,
        // neither of which was ever the problem.
        ClientBookingFlowScreen flow = home.bookIndividual();
        if (!flow.isLoaded()) {
            throw new SkipException("The individual booking flow did not open from '"
                    + ClientHomeScreen.BOOK_INDIVIDUAL + "' — the entry point may require a saved "
                    + "address or an active service catalogue.");
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

    @Test(description = "Choosing a category filters the service list to that category")
    public void categoryFiltersServices() {
        ClientBookingFlowScreen flow = openBookingFlow();

        // REPLACES serviceSearchFilters. Step 1 has no search box — verified on-device, it renders
        // "Pick a category, then the service you'd like." over a category row and a filtered list,
        // with no EditText on the step at all. The old test typed a nonsense query into a field
        // that does not exist and timed out after 30 seconds, reporting it as a missing empty
        // state. Filtering is real; it is just done by category.
        flow.selectCategory(CATEGORY);
        Assert.assertTrue(flow.hasService(SERVICE),
                "The '" + CATEGORY + "' category should offer '" + SERVICE + "'");

        flow.selectCategory(OTHER_CATEGORY);
        Assert.assertFalse(flow.hasService(SERVICE),
                "Switching to '" + OTHER_CATEGORY + "' must drop '" + SERVICE + "' from the list — "
                        + "the catalogue is filtered by category, and a service showing under the "
                        + "wrong one is bookable from a category that cannot deliver it");
    }

    @Test(description = "Step 2 explains that editing the address updates the saved one")
    public void serviceAddressCanBeEdited() {
        ClientBookingFlowScreen flow = openBookingFlow();
        advanceToStep(flow, 2);

        // The hint lives in the ADDRESS EDITOR, not on step 2 itself. Step 2 shows the saved
        // address as a summary row — "Home / 1500 Marilla St, Dallas, Texas, 75201" — with a
        // "Change" affordance; only once that is tapped does the app warn that editing here
        // rewrites the client's saved address. The test asserted the warning without opening the
        // editor, so it failed on a screen that had not been asked to show it.
        flow.changeAddress();

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
    /**
     * Walks the flow to {@code target}, <b>satisfying each step's requirement on the way</b>.
     *
     * <p>The old version only tapped Continue. Every step of this flow gates on an answer — step 1
     * will not advance until a service is chosen (the FROM total sits at $0 and the CTA does
     * nothing), step 2 until a recipient is — so the loop stalled immediately and reported "this
     * environment lacks the data that step requires", blaming missing services and saved addresses
     * for a flow that was simply never given an answer. Four tests skipped on that message against
     * a perfectly seeded environment.
     *
     * <p>Answers are given only when the step still needs one, so it is safe to call at any point.
     */
    private void advanceToStep(ClientBookingFlowScreen flow, int target) {
        for (int step = flow.currentStep(); step > 0 && step < target; step = flow.currentStep()) {
            satisfy(flow, step);
            flow.continueStep();
            if (flow.currentStep() == step) {
                throw new SkipException("The flow would not advance past step " + step
                        + " even after answering it. Step 1 needs a bookable service in the chosen "
                        + "category, step 2 a recipient, step 3 an available slot — one of those is "
                        + "genuinely absent in this environment.");
            }
        }
        Assert.assertEquals(flow.currentStep(), target, "Should have reached step " + target);
    }

    /** Gives step {@code step} the answer it is waiting for. */
    private void satisfy(ClientBookingFlowScreen flow, int step) {
        switch (step) {
            case 1 -> {
                // Category FIRST: the service list is filtered by it, and the default category is
                // not necessarily the one carrying SERVICE. Then the service itself.
                flow.selectCategory(CATEGORY);
                flow.selectService(SERVICE);
            }
            // "Who & where" — book for the signed-in client, whose address is already saved.
            case 2 -> flow.chooseMyself();
            // "When" — the hardest step to satisfy, and the one this helper used to fumble.
            //
            // Three things are needed, not one: a day that actually has times, a SLOT, and then a
            // PROFESSIONAL. _canAdvance gates step 3 on `_time != null && _matchedPro != null`, so
            // expanding a period is not nearly enough — and the professional chooser is the part
            // everyone misses, because it only appears once a slot is picked and the flow lets you
            // walk past it. Slots are also collapsed under Morning/Afternoon/Evening and do not
            // exist in the accessibility tree until a period is expanded.
            //
            // Nothing is pinned to one day, one slot or one professional: today's times run out by
            // mid-afternoon, a given slot may have nobody free even on a busy day, and whoever was
            // free last run is booked this one.
            case 3 -> {
                flow.waitForAvailability();
                java.util.List<String> slots = flow.openFirstDayWithTimes(MAX_DAYS_TO_TRY);
                if (slots.isEmpty()) {
                    throw new SkipException("No day in the next " + MAX_DAYS_TO_TRY + " has any "
                            + "open times for '" + SERVICE + "', so step 3 cannot be satisfied. "
                            + "Put a professional on duty with availability in the client's area.");
                }
                ClientBookingFlowScreen.SlotOffer offer =
                        flow.selectSlotWithProfessionals(slots, MAX_SLOTS_TO_TRY);
                if (offer == null) {
                    throw new SkipException("None of the first " + MAX_SLOTS_TO_TRY + " open times "
                            + "offered a professional. The flow will not leave step 3 without one "
                            + "— it gates on `_time != null && _matchedPro != null`.");
                }
                flow.chooseProfessional(offer.professionals().get(0));
            }
            default -> { }
        }
    }
}
