package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;

/**
 * The "Review &amp; Cost Breakdown" dialog and the charge behind it —
 * {@code ClientHomePageReviewAndCostDialog.dart} + {@code services/payment_handler_service.dart}.
 *
 * <p>This is the last screen of an individual booking and the only one that spends money. It shows
 * the itemised price the client is about to be charged, and "Confirm &amp; Pay" fires
 * {@code POST /payment/create-intent}, which authorises a hold on the client's saved card, creates
 * the appointment, and only then captures.
 *
 * <p><b>There is no card form on this path.</b> A one-off booking charges the card already on file
 * ({@code client_payment_methods}); the {@code CardField} dialog appears only when a membership
 * credit part-covers the booking and no card is saved. So the pay leg is drivable end to end
 * without touching Stripe's native widget.
 *
 * <h2>Reading the outcome</h2>
 * Success replaces the screen with {@code ClientBookingSuccessPaymentConformationPage}
 * ("Booking Confirmed" / "Payment successful"). Failure is far quieter, and the quietness is the
 * trap: {@code PaymentHandlerService} reports it with a {@code SnackBar} raised on the
 * <em>dialog's</em> context, so it is drawn <b>behind</b> the dialog and is invisible both to a
 * person and to the accessibility tree. Verified on-device against a refused charge: the dialog sat
 * unchanged, no snackbar ever appeared in the tree, and the only evidence was
 * {@code ❌ confirm error 409} in logcat. {@link #outcome()} therefore treats "still on the dialog"
 * as a failure rather than waiting for a message that never comes.
 */
public class ClientBookingReviewScreen extends MobileBasePage {

    // ---- the dialog ---------------------------------------------------------
    public static final String TITLE = "Review & Cost Breakdown";
    public static final String ASSIGNED_PROFESSIONAL = "Assigned Professional";
    public static final String SERVICE_CHARGE = "Selected Service Charge";
    public static final String DISTANCE_SURCHARGE = "Distance Surcharge";
    public static final String SELECTED_PROFESSIONAL = "Selected Professional";
    public static final String FINAL_BEFORE_AI = "Final price before AI:";
    public static final String AI_PRICE = "AI Price";
    public static final String CONFIRM_AND_PAY = "Confirm & Pay";
    public static final String EDIT = "Edit";

    // ---- the success screen -------------------------------------------------
    public static final String BOOKING_CONFIRMED = "Booking Confirmed";
    public static final String PAYMENT_SUCCESSFUL = "Payment successful";
    public static final String MAKE_RECURRING = "Yes, make it recurring";

    // ---- failure copy the app can raise ------------------------------------
    /** Snackbar from {@code PaymentHandlerService} when the charge is refused. */
    public static final String PAYMENT_FAILED = "Payment Failed!";
    /** A hard compliance refusal gets a dialog of its own, not a snackbar. */
    public static final String BOOKING_UNAVAILABLE = "isn’t available in your area";
    /** Raised when a membership credit part-covers the booking and no card is on file. */
    public static final String NEEDS_CARD = "Please add a card to cover the remaining amount.";

    /**
     * The same-day/same-week duplicate reminder, raised between "Confirm &amp; Pay" and the charge.
     *
     * <p>Non-blocking by design but absolutely blocking in practice: until it is answered no request
     * is sent at all, so the review dialog just sits there and the run looks like a silently refused
     * payment. It only appears once the client already has a booking near the chosen time, so it is
     * invisible on a fresh fixture and then fires on every subsequent run — which is exactly the
     * shape of "the test passed yesterday". Verified on-device: answering it took the same booking
     * straight through to "Payment successful".
     */
    public static final String DUPLICATE_WARNING = "Heads up";
    /** Its proceed button. "Cancel" abandons the booking. */
    public static final String DUPLICATE_PROCEED = "Book anyway";

    /** How long to give the charge. It authorises, writes the booking, then captures. */
    private static final Duration PAYMENT_TIMEOUT = Duration.ofSeconds(90);

    private final By confirmAndPay = accId(CONFIRM_AND_PAY);

    public ClientBookingReviewScreen(AndroidDriver driver) {
        super(driver);
    }

    /** The result of pressing "Confirm &amp; Pay". */
    public enum Outcome {
        /** The booking was created and the card charged. */
        PAID,
        /** The charge was refused; the dialog is still up. */
        REFUSED,
        /** A compliance block — named separately because it is not a payment problem. */
        BLOCKED,
        /** A card is needed to cover the remainder of a part-credited booking. */
        NEEDS_CARD
    }

    /** True once the review dialog has rendered. */
    public boolean isLoaded() {
        return isPresent(descContains(TITLE), Duration.ofSeconds(30));
    }

    /** True if the itemised breakdown is showing (service, distance and professional lines). */
    public boolean showsBreakdown() {
        return isPresentAfterScroll(SERVICE_CHARGE)
                && isPresentAfterScroll(DISTANCE_SURCHARGE)
                && isPresentAfterScroll(SELECTED_PROFESSIONAL);
    }

    /**
     * The price the client is about to pay, read from the "Final price before AI:" row.
     *
     * <p>That row is the total the charge is built from. Returns -1 when it cannot be read.
     */
    public double total() {
        return amountFrom(FINAL_BEFORE_AI);
    }

    /** The service line's own amount, so a test can check the total is built from it. */
    public double serviceCharge() {
        return amountFrom(SERVICE_CHARGE);
    }

    /** The professional-level premium line. */
    public double professionalPremium() {
        return amountFrom(SELECTED_PROFESSIONAL);
    }

    /** The distance surcharge line. */
    public double distanceSurcharge() {
        return amountFrom(DISTANCE_SURCHARGE);
    }

    /**
     * Reads the "$x.xx" out of the merged row that starts with {@code anchor}.
     *
     * <p>Every row in this dialog exports label, sub-label and amount as ONE node — e.g.
     * {@code "Selected Service Charge\nService Name: …\nService Duration: 55 min\n$85.00"} — so the
     * amount is parsed from the anchor's own node rather than looked for in a sibling.
     */
    private double amountFrom(String anchor) {
        if (!isPresentAfterScroll(anchor)) {
            return -1;
        }
        WebElement row = find(descContains(anchor));
        if (row == null) {
            return -1;
        }
        return trailingAmount(row.getAttribute("content-desc"));
    }

    /**
     * The <b>last</b> "$…" in a merged row, which is the row's own amount.
     *
     * <p>Not the first. The professional-premium row shows its working —
     * {@code "Selected Professional\nProfessional level: 3\n(20 % × $85.00)\n$17.00"} — so reading
     * the first amount returns the base price the percentage was applied to and the breakdown
     * appears not to add up ($85 + $85 + $0 = $170 against a stated $102). Every row in this dialog
     * puts its own figure last, so the trailing amount is the one the client is being charged.
     *
     * @return the parsed amount, or -1 when the row carries none
     */
    static double trailingAmount(String raw) {
        if (raw == null) {
            return -1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)").matcher(raw);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        if (last == null) {
            return -1;
        }
        try {
            return Double.parseDouble(last.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Presses "Confirm &amp; Pay" and waits for the booking to settle.
     *
     * @return what actually happened — see {@link Outcome}
     */
    public Outcome confirmAndPay() {
        LOG.info("Review: confirming and paying");
        scrollToDesc(CONFIRM_AND_PAY);
        tap(confirmAndPay);
        acknowledgeDuplicateWarningIfPresent();
        return outcome();
    }

    /**
     * Answers the duplicate-booking reminder so the charge can proceed.
     *
     * <p>Chooses "Book anyway": the caller asked for this booking, and the reminder is advisory.
     * See {@link #DUPLICATE_WARNING} for why skipping this looks like a failed payment.
     *
     * @return true if the reminder was present and acknowledged
     */
    public boolean acknowledgeDuplicateWarningIfPresent() {
        if (!isPresent(descContains(DUPLICATE_WARNING), Duration.ofSeconds(10))) {
            return false;
        }
        LOG.info("Review: acknowledging the duplicate-booking reminder");
        tap(buttonDescContains(DUPLICATE_PROCEED));
        return true;
    }

    /**
     * Waits for the charge to resolve and classifies the result.
     *
     * <p>Polls for the success screen, then for the two failures that <em>do</em> surface something
     * (the compliance dialog and the add-a-card prompt), and finally treats a dialog that is simply
     * still there as a refusal. That last branch is what makes a refused charge visible to the
     * suite at all: the app's own error snackbar is drawn behind this dialog and never reaches the
     * accessibility tree.
     */
    public Outcome outcome() {
        long deadline = System.currentTimeMillis() + PAYMENT_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isPresent(descContains(BOOKING_CONFIRMED), SHORT_TIMEOUT)
                    || isPresent(descContains(PAYMENT_SUCCESSFUL), SHORT_TIMEOUT)) {
                LOG.info("Review: payment succeeded");
                return Outcome.PAID;
            }
            if (isPresent(descContains(BOOKING_UNAVAILABLE), SHORT_TIMEOUT)) {
                LOG.warn("Review: booking blocked on compliance");
                return Outcome.BLOCKED;
            }
            if (isPresent(descContains(NEEDS_CARD), SHORT_TIMEOUT)) {
                LOG.warn("Review: a card is needed for the remainder");
                return Outcome.NEEDS_CARD;
            }
            if (isPresent(descContains(PAYMENT_FAILED), SHORT_TIMEOUT)) {
                LOG.warn("Review: payment failed");
                return Outcome.REFUSED;
            }
        }
        // Nothing conclusive within the timeout. REFUSED rather than PAID: the success screen is
        // unmistakable when it appears, so not having seen it is evidence against a charge, and
        // reporting PAID here would have the test assert a confirmation that never rendered — a
        // failure whose message points at the wrong thing entirely.
        LOG.warn("Review: no outcome within {}s (dialog still up: {}) — treating as refused",
                PAYMENT_TIMEOUT.toSeconds(), isLoaded());
        return Outcome.REFUSED;
    }

    /** True once the post-payment confirmation screen is showing. */
    public boolean isBookingConfirmed() {
        return isPresent(descContains(BOOKING_CONFIRMED), Duration.ofSeconds(30));
    }

    /** True if the confirmation screen also reports the payment as taken. */
    public boolean showsPaymentSuccessful() {
        return isPresentAfterScroll(PAYMENT_SUCCESSFUL);
    }

    /** Declines the recurring-booking upsell on the confirmation screen. */
    public void declineRecurring() {
        if (isPresentAfterScroll("No, thanks")) {
            scrollAndTap("No, thanks");
        }
    }
}
