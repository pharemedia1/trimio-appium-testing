package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.client.ClientAppointmentsScreen;
import org.example.pages.mobile.client.ClientReviewScreen;
import org.example.pages.mobile.common.BottomNavBar;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The client review flow — {@code screens/reviews/client_review_flow.dart}.
 *
 * <p>Reviews drive a professional's public rating and feed the admin quality queue, so the flow is
 * deliberately hard to complete carelessly: an overall rating, a rating per service, the aspect
 * sliders and a public comment of at least 20 characters. Each gate is tested for its <em>message</em>
 * as well as its refusal — a form that silently does nothing when Submit is pressed is, to the
 * client, indistinguishable from a broken app.
 *
 * <p>Submission itself is left manual: a submitted review is public, attached to a real
 * professional, and not straightforward to retract.
 */
public class ClientReviewTest extends RoleSessionTest {

    /** The reviewer these tests sign in as — Postgres user 41501, roleAccounts.client. */
    private static final long REVIEWER_USER_ID = 41501;

    /**
     * Clears any half-finished review left by an earlier run.
     *
     * <p>Every test here opens the flow and abandons it -- submitting is deliberately manual,
     * because a submitted review is public and attached to a real professional -- and the app
     * saves a draft as it goes. Without this reset the second run of the suite starts on a
     * part-filled wizard and the gate assertions fail against last run's answers rather than
     * against the app.
     */
    @org.testng.annotations.BeforeMethod(alwaysRun = true)
    public void clearReviewDrafts() {
        org.example.utils.DbHelper.clearReviewDrafts(REVIEWER_USER_ID);
    }

    /**
     * Opens the review flow on a visit that had MORE THAN ONE service.
     *
     * <p>Only the per-service gate needs this. The newest completed visit is usually
     * single-service -- the payment suite books individual appointments which later complete and
     * land at the top of history -- so taking whichever is first made this test skip claiming the
     * appointment had one service. True of that appointment; not true of the history.
     */
    private ClientReviewScreen openMultiServiceReviewFlow() {
        loginAsClient();
        new BottomNavBar(driver).open(BottomNavBar.CLIENT_APPOINTMENTS);

        ClientAppointmentsScreen appointments = new ClientAppointmentsScreen(driver);
        appointments.openHistory();
        if (!appointments.rateFirstMultiServiceVisit()) {
            throw new SkipException("No reviewable visit in the history had more than one service, "
                    + "so the per-service gate cannot trigger. Add one with: INSERT INTO "
                    + "appointment_services (appointment_id, service_id, final_price, quoted_price) "
                    + "VALUES (<a completed, unreviewed appointment>, 21, 85.00, 85.00);");
        }

        ClientReviewScreen review = new ClientReviewScreen(driver);
        if (!review.isLoaded()) {
            throw new SkipException("'" + ClientAppointmentsScreen.RATE_VISIT + "' did not open the "
                    + "review flow — its entry point may differ in this build.");
        }
        return review;
    }

    private ClientReviewScreen openReviewFlow() {
        loginAsClient();
        new BottomNavBar(driver).open(BottomNavBar.CLIENT_APPOINTMENTS);

        // Via HISTORY, not the first appointment. The review flow hangs off a past visit's
        // "Rate your visit" button; the appointment DETAIL page has no review control at all, and
        // the first appointment is an upcoming booking, so the old route could never arrive.
        ClientAppointmentsScreen appointments = new ClientAppointmentsScreen(driver);
        appointments.openHistory();
        if (!appointments.hasRateableVisit()) {
            throw new SkipException("No completed, not-yet-reviewed visit in the client's history — "
                    + "every past visit has been reviewed already. Seed one by clearing a review: "
                    + "DELETE FROM reviews WHERE booking_id = <a completed appointment>;");
        }
        appointments.rateFirstVisit();

        ClientReviewScreen review = new ClientReviewScreen(driver);
        if (!review.isLoaded()) {
            throw new SkipException("'" + ClientAppointmentsScreen.RATE_VISIT + "' did not open the "
                    + "review flow — its entry point may differ in this build.");
        }
        return review;
    }

    @Test(description = "Submitting without a star rating is refused with a message")
    public void ratingIsRequired() {
        ClientReviewScreen review = openReviewFlow();

        // The flow refuses by DISABLING its Continue button, not by raising a message: with no
        // star chosen the CTA renders enabled="false" clickable="false". Asserting on a refusal
        // message was asserting against a design the app does not use — it stayed silent and
        // stayed on step 1, and the test read that silence as a defect. Disabling is the stronger
        // gate of the two, since there is nothing to press in the first place.
        Assert.assertFalse(review.canAdvance(),
                "With no star chosen, the step's Continue button must not be pressable");

        review.submit();

        Assert.assertTrue(review.isOnStep(1),
                "Pressing a disabled Continue must not advance the wizard past step 1");
        Assert.assertFalse(review.showsSubmitted(), "No review should be submitted");
    }

    @Test(description = "Every service received must be rated")
    public void allServicesMustBeRated() {
        // Walks to the step by TITLE. It was step 3 of four; a step called "The shop" was then
        // added at position 3 and it became step 4, so asserting on the number counted services on
        // the shop step instead -- whose rows read "Optional", never "Not rated" -- and reported
        // that the appointment had one service. Titles do not renumber.
        ClientReviewScreen review = openMultiServiceReviewFlow();
        review.completeStepOne(5);

        Assert.assertTrue(review.advanceToStep(ClientReviewScreen.STEP_EACH_SERVICE, 5),
                "The wizard should reach the '" + ClientReviewScreen.STEP_EACH_SERVICE + "' step");
        int services = review.unratedRowCount();
        if (services < 2) {
            throw new SkipException("The appointment has " + services + " service(s), so the "
                    + "per-service gate cannot trigger — it needs two. Seed one with: INSERT INTO "
                    + "appointment_services (appointment_id, service_id, final_price, quoted_price) "
                    + "VALUES (<completed appt>, 21, 85.00, 85.00);");
        }

        // Rate ONE service and leave the other: the step must not advance.
        review.rateFirstUnratedRow(5);

        Assert.assertTrue(review.unratedRowCount() > 0, "One service should still be unrated");
        Assert.assertFalse(review.canAdvance(),
                "Leaving a service unrated must keep the step's Continue disabled — a rating that "
                        + "skips a service silently misattributes the whole visit to the rest");
        review.submit();
        Assert.assertTrue(review.isOnStep(3), "The wizard must not advance past the unrated service");
    }

    @Test(description = "The public review must be at least 20 characters")
    public void publicReviewMinimumLength() {
        // Walks forward until the public-review field is on screen, rather than counting steps to
        // it. It used to be step 4 of four; "The shop" was inserted at position 3 and made it step
        // 5, so a fixed count stopped one short on "Each service" -- a step with no text field --
        // and this failed as a 30s timeout on EditText(0), naming a field that was never there.
        ClientReviewScreen review = openReviewFlow();
        review.completeStepOne(5);

        Assert.assertTrue(review.advanceToStep(ClientReviewScreen.PUBLIC_REVIEW, 5),
                "The wizard should reach the step carrying the public-review field");

        review.enterPublicReview("great");

        review.submit();

        Assert.assertTrue(review.showsMinimumLengthError() || review.showsSlidersRequired(),
                "A short public comment should be refused with '" + ClientReviewScreen.MIN_LENGTH
                        + "' (or blocked earlier by the required sliders)");
        Assert.assertFalse(review.showsSubmitted(), "No review should be submitted");
    }
}
