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
    /**
     * The flow's title. It is "Rate your visit" -- the same words as the button that opens it --
     * not "Leave a Review", which appears nowhere.
     */
    public static final String TITLE = "Rate your visit";
    /** The wizard's progress caption, e.g. "Step 1 of 4". */
    public static final String STEP = "Step ";
    /**
     * The primary action on steps 1-3. The flow is a FOUR-STEP WIZARD, not one long form: step 1
     * is the overall star and "Would you book them again?", and the button says "Continue". Only
     * the last step submits, so tapping "Submit" on step 1 found nothing.
     */
    public static final String CONTINUE = "Continue";
    public static final String OVERALL = "Overall experience";
    /** The caption under the stars. It reads "Tap a star", not "Tap to rate". */
    public static final String TAP_TO_RATE = "Tap a star";
    /** The step-1 heading above the stars. */
    public static final String PROMPT = "How was your visit?";
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
                || isPresent(descContains(STEP), Duration.ofSeconds(5));
    }

    // ---- ratings ------------------------------------------------------------

    /**
     * Sets the overall star rating. Stars render as tappable icons under the "Overall experience"
     * heading, labelled 1..5, so the star index is the value.
     */
    public ClientReviewScreen rateOverall(int stars) {
        LOG.info("Review: rating overall {} stars", stars);
        java.util.List<org.openqa.selenium.WebElement> row = starRow();
        if (row.size() < stars) {
            throw new org.openqa.selenium.NoSuchElementException(
                    "Found " + row.size() + " star targets, needed " + stars
                            + ". The stars are unlabelled clickable Views between '" + PROMPT
                            + "' and '" + TAP_TO_RATE + "'.");
        }
        row.get(stars - 1).click();
        return this;
    }

    /**
     * The five overall-rating stars, left to right.
     *
     * <p>They have <b>no accessibility label of any kind</b> -- five bare
     * {@code android.view.View} nodes with {@code clickable="true"} and an empty content-desc --
     * so {@code accId("5")} could never resolve and there is nothing to match on by name. A
     * screen reader announces nothing for them either, which makes the overall rating, the one
     * mandatory field in the flow, unusable without sight. Worth fixing in the app with a
     * {@code Semantics(label: '$n stars')}; until then they are identified by position, between
     * the "{@value #PROMPT}" heading and the "{@value #TAP_TO_RATE}" caption.
     */
    private java.util.List<org.openqa.selenium.WebElement> starRow() {
        int top = yOf(PROMPT);
        int bottom = yOf(TAP_TO_RATE);
        java.util.List<org.openqa.selenium.WebElement> stars = new java.util.ArrayList<>();
        for (org.openqa.selenium.WebElement e : findAll(
                io.appium.java_client.AppiumBy.androidUIAutomator(
                        "new UiSelector().className(\"android.view.View\").clickable(true)"))) {
            String desc = e.getAttribute("content-desc");
            int y = e.getLocation().getY();
            if ((desc == null || desc.isBlank()) && y > top && y < bottom) {
                stars.add(e);
            }
        }
        stars.sort(java.util.Comparator.comparingInt(e -> e.getLocation().getX()));
        LOG.debug("Review: found {} star targets between y={} and y={}", stars.size(), top, bottom);
        return stars;
    }

    /** The y of the first node containing {@code text}, or a permissive bound when absent. */
    private int yOf(String text) {
        org.openqa.selenium.WebElement e = find(descContains(text));
        return e == null ? (text.equals(PROMPT) ? 0 : Integer.MAX_VALUE) : e.getLocation().getY();
    }

    /**
     * True when the step's primary action can actually be pressed.
     *
     * <p>The flow gates on the BUTTON, not on a message: with no star chosen, Continue renders
     * {@code enabled="false" clickable="false"}. So "submitting without a rating is refused" is
     * observed here rather than by waiting for an error that the app never shows -- and never
     * needs to show, because the control cannot be pressed.
     */
    /** True when the wizard is showing step {@code n} of 4. */
    public boolean isOnStep(int n) {
        return isPresent(descContains(STEP + n + " of"), Duration.ofSeconds(10));
    }

    public boolean canAdvance() {
        org.openqa.selenium.WebElement cta = find(buttonDescContains(CONTINUE));
        return cta != null && cta.isEnabled();
    }

    /**
     * Answers "Would you book them again?" -- step 1's second required field.
     *
     * <p>Step 1 has TWO gates, not one. Choosing a star alone leaves Continue disabled, which is
     * what made the wizard look stuck: the star registered (the caption changes from "Tap a star"
     * to "Excellent"), but the step will not advance until this is answered too.
     */
    public ClientReviewScreen answerBookAgain(boolean yes) {
        LOG.info("Review: book again = {}", yes);
        scrollAndTapExact(yes ? "Yes" : "No");
        return this;
    }

    /** Completes step 1 -- both of its required fields -- and moves to step 2. */
    public ClientReviewScreen completeStepOne(int stars) {
        rateOverall(stars);
        answerBookAgain(true);
        submit();
        return this;
    }

    /** The caption an unrated aspect or service shows. */
    public static final String NOT_RATED = "Not rated";

    /**
     * Rates every aspect still showing "{@value #NOT_RATED}" on the current step.
     *
     * <p>Steps 2 and 3 are lists of rows -- "Professionalism", "Hygiene &amp; cleanliness",
     * "Attitude &amp; courtesy", then one row per service booked -- each with its own 1..5
     * buttons. The digits ARE labelled, but every row has the same five, so a bare
     * {@code accId("5")} would hit whichever the tree yields first and leave the rest unrated.
     * Each row's buttons are therefore matched by position: the ones sitting just below that
     * row's "{@value #NOT_RATED}" caption.
     *
     * <p>Loops because the list re-renders as rows are rated, which invalidates the handles.
     *
     * @return how many rows were rated
     */
    public int rateAllAspects(int score) {
        // Wait for the step to render before the first read. rateFirstUnratedRow uses findAll,
        // which is immediate, so asking it as the step opens answers "no rows" for rows that are
        // merely still arriving. When that happened this reported 0 rated, the wizard never
        // advanced, and the failure surfaced two steps later as "Completing steps 1 and 2 should
        // land on the per-service step" — naming a screen that was never reached, for a reason
        // that had nothing to do with it. The same run rated 6 rows on a less loaded device.
        isPresent(descContains(NOT_RATED), Duration.ofSeconds(15));

        int rated = 0;
        while (rated < 12 && rateFirstUnratedRow(score)) {
            rated++;
        }
        LOG.info("Review: rated {} row(s) on this step", rated);
        return rated;
    }

    /**
     * Rates the first row still showing "{@value #NOT_RATED}".
     *
     * @return false when every row on the step is already rated
     */
    public boolean rateFirstUnratedRow(int score) {
        java.util.List<org.openqa.selenium.WebElement> pending = findAll(descContains(NOT_RATED));
        if (pending.isEmpty()) {
            return false;
        }
        int y = pending.get(0).getLocation().getY();
        for (org.openqa.selenium.WebElement digit : findAll(descContains(String.valueOf(score)))) {
            String desc = digit.getAttribute("content-desc");
            int dy = digit.getLocation().getY();
            if (String.valueOf(score).equals(desc == null ? null : desc.trim())
                    && dy > y && dy < y + 200) {
                digit.click();
                return true;
            }
        }
        LOG.warn("Review: no '{}' button found below the row at y={}", score, y);
        return false;
    }

    /** How many rows on this step are still unrated. */
    public int unratedRowCount() {
        return findAll(descContains(NOT_RATED)).size();
    }

    /** True while any row on this step is still unrated. */
    public boolean hasUnratedRows() {
        return !findAll(descContains(NOT_RATED)).isEmpty();
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

    /**
     * Presses the step's primary action.
     *
     * <p>"Submit" only exists on the wizard's final step; every earlier step advances with
     * "Continue", and it is that button which raises the validation messages the tests assert on.
     */
    public ClientReviewScreen submit() {
        hideKeyboard();
        if (isPresentAfterScroll("Submit review")) {
            tap(descContains("Submit review"));
        } else if (isPresentAfterScroll("Submit")) {
            tap(descContains("Submit"));
        } else {
            tap(buttonDescContains(CONTINUE));
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
