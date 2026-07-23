package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The client review flow — {@code screens/reviews/client_review_flow.dart} /
 * {@code review_flow_page.dart}.
 *
 * <p>A multi-gate form: an overall star rating, a rating for every service received, a set of aspect
 * sliders, a public review of at least 20 characters and optional private feedback plus photos.
 * Each gate has its own message, and those messages are the contract this page object encodes —
 * they are what tells a client <em>why</em> the Submit button did nothing.
 *
 * <p>Private feedback is explicitly "only visible to Trimio", so any test that submits it should also
 * assert it does not appear on the public review.
 */
public class ClientReviewScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String TITLE = "Leave a Review";
    public static final String OVERALL = "Overall experience";
    public static final String TAP_TO_RATE = "Tap to rate";
    public static final String RATING_REQUIRED = "Please choose a star rating";
    public static final String ANSWER_REQUIRED = "Please answer before continuing";
    public static final String SLIDERS_REQUIRED = "Please rate all required sliders before continuing.";
    public static final String SERVICES_REQUIRED = "Please rate all your services before continuing.";
    public static final String MIN_LENGTH = "Please write at least 20 characters for your review.";
    public static final String PUBLIC_REVIEW = "Public Review";
    public static final String PRIVATE_FEEDBACK = "Private Feedback (only visible to Trimio)";
    public static final String ADD_PHOTOS = "Add Photos (Optional)";
    public static final String SUBMITTING = "Submitting review...";
    public static final String SUBMITTED = "Thanks for your review!";
    public static final String REPORT_TO_SUPPORT = "Report an issue to support";
    public static final String NO_SERVICES = "No services found for this appointment.";

    public ClientReviewScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(TITLE), Duration.ofSeconds(25))
                || isPresent(descContains("Leave a review"), Duration.ofSeconds(5));
    }

    // ---- ratings ------------------------------------------------------------

    /**
     * Sets the overall star rating. Stars render as tappable icons under the "Overall experience"
     * heading, labelled 1..5, so the star index is the value.
     */
    public ClientReviewScreen rateOverall(int stars) {
        LOG.info("Review: rating overall {} stars", stars);
        scrollToDesc(OVERALL);
        tap(accId(String.valueOf(stars)));
        return this;
    }

    /** Rates the n-th service in the per-service section. */
    public ClientReviewScreen rateService(String serviceName, int stars) {
        scrollToDesc(serviceName);
        tap(accId(String.valueOf(stars)));
        return this;
    }

    // ---- text ---------------------------------------------------------------

    /** Types the public review body. */
    public ClientReviewScreen enterPublicReview(String text) {
        scrollToDesc(PUBLIC_REVIEW);
        type(editText(0), text);
        hideKeyboard();
        return this;
    }

    /** Types the private feedback body. */
    public ClientReviewScreen enterPrivateFeedback(String text) {
        scrollToDesc("Private");
        type(editText(1), text);
        hideKeyboard();
        return this;
    }

    // ---- submission ---------------------------------------------------------

    public ClientReviewScreen submit() {
        hideKeyboard();
        if (isPresentAfterScroll("Submit review")) {
            tap(descContains("Submit review"));
        } else {
            tap(descContains("Submit"));
        }
        return this;
    }

    public boolean showsRatingRequired() {
        return isPresent(descContains(RATING_REQUIRED), Duration.ofSeconds(10))
                || isPresent(descContains(ANSWER_REQUIRED), Duration.ofSeconds(5));
    }

    public boolean showsServicesRequired() {
        return isPresent(descContains(SERVICES_REQUIRED), Duration.ofSeconds(10));
    }

    public boolean showsSlidersRequired() {
        return isPresent(descContains(SLIDERS_REQUIRED), Duration.ofSeconds(10));
    }

    public boolean showsMinimumLengthError() {
        return isPresent(descContains(MIN_LENGTH), Duration.ofSeconds(10));
    }

    public boolean showsSubmitted() {
        return isPresent(descContains(SUBMITTED), Duration.ofSeconds(45));
    }

    /** Opens the support escalation from inside the review flow. */
    public ClientReviewScreen reportToSupport() {
        scrollAndTap(REPORT_TO_SUPPORT);
        return this;
    }
}
