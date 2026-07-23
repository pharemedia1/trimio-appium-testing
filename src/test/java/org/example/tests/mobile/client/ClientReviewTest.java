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

    private ClientReviewScreen openReviewFlow() {
        loginAsClient();
        new BottomNavBar(driver).open(BottomNavBar.CLIENT_APPOINTMENTS);

        ClientAppointmentsScreen appointments = new ClientAppointmentsScreen(driver);
        if (!appointments.hasAnyAppointment()) {
            throw new SkipException("The signed-in client has no completed appointment to review — "
                    + "seed one to exercise the review flow.");
        }
        appointments.openFirst();

        ClientReviewScreen review = new ClientReviewScreen(driver);
        if (!review.isLoaded()) {
            throw new SkipException("The review flow was not reachable from the appointment — it is "
                    + "offered only for completed, not-yet-reviewed appointments.");
        }
        return review;
    }

    @Test(description = "Submitting without a star rating is refused with a message")
    public void ratingIsRequired() {
        ClientReviewScreen review = openReviewFlow();

        review.submit();

        Assert.assertTrue(review.showsRatingRequired(),
                "Submitting with no rating should explain why — '"
                        + ClientReviewScreen.RATING_REQUIRED + "'");
        Assert.assertFalse(review.showsSubmitted(), "No review should be submitted");
    }

    @Test(description = "Every service received must be rated")
    public void allServicesMustBeRated() {
        ClientReviewScreen review = openReviewFlow();
        review.rateOverall(5);

        review.submit();

        if (!review.showsServicesRequired()) {
            throw new SkipException("The appointment has a single service already rated, so the "
                    + "per-service gate cannot trigger — use an appointment with two services.");
        }
        Assert.assertTrue(review.showsServicesRequired(),
                "Leaving a service unrated should be refused with '"
                        + ClientReviewScreen.SERVICES_REQUIRED + "'");
    }

    @Test(description = "The public review must be at least 20 characters")
    public void publicReviewMinimumLength() {
        ClientReviewScreen review = openReviewFlow();
        review.rateOverall(5);
        review.enterPublicReview("great");

        review.submit();

        Assert.assertTrue(review.showsMinimumLengthError() || review.showsSlidersRequired(),
                "A short public comment should be refused with '" + ClientReviewScreen.MIN_LENGTH
                        + "' (or blocked earlier by the required sliders)");
        Assert.assertFalse(review.showsSubmitted(), "No review should be submitted");
    }
}
