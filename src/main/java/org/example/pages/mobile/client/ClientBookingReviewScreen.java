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
    /**
     * The page's own title.
     *
     * <p>Was "Review & Cost Breakdown", which is what the old <em>dialog</em> was called. The
     * final price moved out of a pop-up and onto a page of its own — see the note on
     * {@code pushReviewAndCost} in {@code ClientHomePageReviewAndCostDialog.dart}, which kept the
     * class name and changed everything else — and the page is headed "Review & pay". The old
     * title matched nothing, so isLoaded() was false on a page that had rendered.
     */
    public static final String TITLE = "Review & pay";
    /** The breakdown block's heading. Everything below is merged into ONE node with it. */
    public static final String PRICE_DETAILS = "Price details";
    /** The breakdown's line labels, as the page renders them. */
    public static final String LINE_SERVICES = "Services";
    public static final String LINE_BOOKING_TRAVEL_FEE = "Booking & travel fee";
    public static final String LINE_TOTAL = "Total";
    public static final String ASSIGNED_PROFESSIONAL = "Assigned Professional";
    /** @deprecated the row is called {@link #LINE_SERVICES} now. */
    @Deprecated
    public static final String SERVICE_CHARGE = "Selected Service Charge";
    public static final String DISTANCE_SURCHARGE = "Distance Surcharge";
    public static final String SELECTED_PROFESSIONAL = "Selected Professional";
    public static final String FINAL_BEFORE_AI = "Final price before AI:";
    public static final String AI_PRICE = "AI Price";
    /**
     * The confirm button, which now carries the amount: "Pay $97.00".
     *
     * <p>Matched on the prefix because the rest is the total, which changes with the booking.
     * It was "Confirm & Pay" when this was a dialog.
     */
    public static final String CONFIRM_AND_PAY = "Pay $";
    public static final String EDIT = "Edit";

    // ---- the success screen -------------------------------------------------
    /**
     * The confirmation page's landmark.
     *
     * <p>Was "Booking Confirmed". The page is headed "You're booked!" and carries a
     * "Booking number  #26532" row — that row is used instead of the headline because the
     * headline's apostrophe is non-ASCII and a {@code UiSelector} cannot match it, the same trap
     * that has already cost this suite nineteen dead locators.
     */
    public static final String BOOKING_CONFIRMED = "Booking number";
    /**
     * The line that states the charge was taken: "Paid $97.00".
     *
     * <p>Was "Payment successful", which the page never says. Matched on the prefix because the
     * amount varies with the booking.
     */
    public static final String PAYMENT_SUCCESSFUL = "Paid $";
    /**
     * The confirmation page's recurring offer, now a pair of buttons rather than a dialog:
     * "Keep my spot · every 2 weeks" to accept, "Done" to decline.
     */
    public static final String MAKE_RECURRING = "Keep my spot";
    /** Declines the recurring offer and closes the confirmation. */
    public static final String RECURRING_DECLINE = "Done";

    // ---- failure copy the app can raise ------------------------------------
    /** Snackbar from {@code PaymentHandlerService} when the charge is refused. */
    public static final String PAYMENT_FAILED = "Payment Failed!";
    /** A hard compliance refusal gets a dialog of its own, not a snackbar. */
    public static final String BOOKING_UNAVAILABLE = "available in your area";
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
    /**
     * The duplicate-booking dialog's title.
     *
     * <p>Was "Heads up"; it now reads <b>"Book another visit?"</b> above "You already have an
     * appointment on … Book another for the same day?". The rename mattered more than a rename
     * usually does: this dialog stands between "Pay" and the charge and <b>until it is answered
     * no request is sent at all</b>, so failing to recognise it presents exactly as a silently
     * refused payment — the test pressed Pay, waited ninety seconds, saw no confirmation and
     * reported the charge as declined, while the app sat waiting for an answer nobody gave.
     */
    public static final String DUPLICATE_WARNING = "Book another visit?";
    /** Its proceed button. "Cancel" abandons the booking. */
    public static final String DUPLICATE_PROCEED = "Book anyway";

    /** How long to give the charge. It authorises, writes the booking, then captures. */
    private static final Duration PAYMENT_TIMEOUT = Duration.ofSeconds(90);

    // CONTAINS, not an exact accessibility id: the button reads "Pay $97.00" — the label
    // carries the total, which changes with every booking.
    private final By confirmAndPay = buttonDescContains(CONFIRM_AND_PAY);

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

    /** True if the itemised price breakdown is showing. */
    public boolean showsBreakdown() {
        return isPresentAfterScroll(PRICE_DETAILS)
                && isPresentAfterScroll(LINE_SERVICES)
                && isPresentAfterScroll(LINE_TOTAL);
    }

    /**
     * Every line of the breakdown except the total, as label → amount.
     *
     * <p>The whole block is ONE merged node —
     * {@code "Price details\nServices\n$85.00\nBooking & travel fee\n$12.00\nTotal\n$97.00"}
     * — so the lines are read by walking its segments in pairs rather than by locating separate
     * rows. Returning the lines rather than naming them means a test can assert the total is the
     * sum of what is shown without having to know which lines this booking happens to have: a
     * shop visit has no travel fee, a premium professional adds a line, and the assertion that
     * matters ("the client can derive the total") holds either way.
     */
    public java.util.Map<String, Double> lineItems() {
        java.util.Map<String, Double> lines = new java.util.LinkedHashMap<>();
        String block = mergedBreakdown();
        if (block == null) {
            return lines;
        }
        String[] parts = block.split("\n");
        for (int i = 0; i + 1 < parts.length; i++) {
            String label = parts[i].trim();
            double amount = ClientBookingFlowScreen.parseAmount(parts[i + 1].trim());
            if (amount >= 0 && !label.isEmpty() && !label.startsWith("$")
                    && !LINE_TOTAL.equalsIgnoreCase(label)) {
                lines.put(label, amount);
            }
        }
        return lines;
    }

    /** The merged "Price details" node, or null when it is not on screen. */
    private String mergedBreakdown() {
        if (!isPresentAfterScroll(PRICE_DETAILS)) {
            return null;
        }
        WebElement node = find(descContains(PRICE_DETAILS));
        return node == null ? null : node.getAttribute("content-desc");
    }

    /**
     * The price the client is about to pay, read from the "Final price before AI:" row.
     *
     * <p>That row is the total the charge is built from. Returns -1 when it cannot be read.
     */
    public double total() {
        return lineAmount(LINE_TOTAL);
    }

    /** The service line's own amount, so a test can check the total is built from it. */
    public double serviceCharge() {
        return lineAmount(LINE_SERVICES);
    }

    /** The booking &amp; travel fee line. */
    public double bookingAndTravelFee() {
        return lineAmount(LINE_BOOKING_TRAVEL_FEE);
    }

    /**
     * Reads one line of the merged breakdown by its label.
     *
     * <p>The amount is the segment AFTER the label, not the last "$" in the node: the block holds
     * every line, so taking the last one returns the total whichever line was asked for.
     *
     * @return the amount, or -1 when the page does not carry that line
     */
    public double lineAmount(String label) {
        String block = mergedBreakdown();
        if (block == null) {
            return -1;
        }
        String[] parts = block.split("\n");
        for (int i = 0; i + 1 < parts.length; i++) {
            if (parts[i].trim().equalsIgnoreCase(label)) {
                return ClientBookingFlowScreen.parseAmount(parts[i + 1].trim());
            }
        }
        return -1;
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

    /**
     * Declines the recurring-booking offer on the confirmation screen.
     *
     * <p>The offer is no longer a dialog with "No, thanks" — the confirmation page asks "Want this
     * time again?" under the receipt and gives two buttons, {@link #MAKE_RECURRING} to accept and
     * {@link #RECURRING_DECLINE} to decline. Declining is also what dismisses the confirmation, so
     * this is how a booking test gets back to the app.
     *
     * <p>Guarded rather than assumed: the offer only appears for a booking that could sensibly
     * repeat, and a test that has just paid should not fail because it was not asked.
     */
    public void declineRecurring() {
        if (isPresentAfterScroll(RECURRING_DECLINE)) {
            scrollAndTapExact(RECURRING_DECLINE);
        }
    }
}
