package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Membership — {@code screens/client/membership/*}: plan choice, Stripe checkout, manage/upgrade/
 * pause/cancel and billing history.
 *
 * <p>Membership is the second money path in the client app (after booking) and the one with the most
 * state: an active plan grants monthly credits that the booking flow spends before charging the card,
 * so a defect here is felt at checkout rather than here. The assertions worth automating are the
 * ones that read state — plans listed, credits remaining, renewal date — because the mutating paths
 * (subscribe/cancel) move real money and are better left to a controlled manual pass.
 */
public class ClientMembershipScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String CHOOSE_PLAN = "Choose your plan";
    public static final String NO_PLANS = "No plans available right now.";
    public static final String MANAGE_PLAN = "Manage plan";
    public static final String UPGRADE_PLAN = "Upgrade your plan";
    public static final String BILLING_HISTORY = "Billing history";
    public static final String NO_INVOICES = "No invoices yet";
    public static final String CANCEL_MEMBERSHIP = "Cancel membership";
    public static final String CANCEL_CONFIRM = "Cancel membership?";
    public static final String KEEP_MEMBERSHIP = "Keep my membership";
    public static final String PAUSE_INSTEAD = "Pause for up to 2 months instead";
    public static final String CALCULATING_REFUND = "Calculating your refund…";
    public static final String PAUSE_CONFIRM = "Pause membership?";
    public static final String ACTIVATED = "Membership activated successfully";
    public static final String CONFIRM_MEMBERSHIP = "Confirm membership";
    public static final String CREDITS_SUFFIX = "credits left";

    public ClientMembershipScreen(AndroidDriver driver) {
        super(driver);
    }

    // ---- plan selection -----------------------------------------------------

    public boolean isPlanChooserLoaded() {
        return isPresent(descContains(CHOOSE_PLAN), Duration.ofSeconds(25));
    }

    public boolean showsNoPlans() {
        return isPresent(descContains(NO_PLANS), Duration.ofSeconds(10));
    }

    /** True if at least one plan card is offered ("Choose <plan>"). */
    public boolean hasAnyPlan() {
        return isPresentAfterScroll("Choose ");
    }

    /** True if a plan advertises a monthly in-home allotment. */
    public boolean showsAllotment() {
        return isPresentAfterScroll("in-home cuts/month");
    }

    /** Selects a plan by name ("Choose <plan>"). */
    public ClientMembershipScreen choosePlan(String planName) {
        LOG.info("Membership: choosing plan '{}'", planName);
        scrollAndTap("Choose " + planName);
        return this;
    }

    // ---- checkout -----------------------------------------------------------

    /** Fills the billing fields on the membership checkout page. */
    public ClientMembershipScreen enterBillingDetails(String cardholder, String receiptEmail) {
        scrollToDesc("Billing info");
        type(editText(0), cardholder);
        type(editText(1), receiptEmail);
        hideKeyboard();
        return this;
    }

    /** Confirms the subscription ("Subscribe — $x/mo" / "Confirm membership"). */
    public ClientMembershipScreen confirmMembership() {
        if (isPresentAfterScroll("Subscribe")) {
            tap(descContains("Subscribe"));
        } else {
            tap(descContains(CONFIRM_MEMBERSHIP));
        }
        return this;
    }

    public boolean showsActivated() {
        return isPresent(descContains(ACTIVATED), Duration.ofSeconds(60))
                || isPresent(descContains("Welcome to "), Duration.ofSeconds(10));
    }

    // ---- manage -------------------------------------------------------------

    public boolean isManageLoaded() {
        return isPresent(descContains(MANAGE_PLAN), Duration.ofSeconds(25));
    }

    /** True when the "<a> of <b> credits left" line is rendered. */
    public boolean showsCreditsRemaining() {
        return isPresentAfterScroll(CREDITS_SUFFIX);
    }

    /** True when the renewal line ("$x/mo · renews <date>") is rendered. */
    public boolean showsRenewalLine() {
        return isPresentAfterScroll("renews");
    }

    public boolean showsActiveStatus() {
        return isPresentAfterScroll("Active");
    }

    // ---- pause / cancel -----------------------------------------------------

    public ClientMembershipScreen pause() {
        scrollAndTap("Pause");
        return this;
    }

    public boolean showsPauseConfirmation() {
        return isPresent(descContains(PAUSE_CONFIRM), Duration.ofSeconds(10));
    }

    public ClientMembershipScreen startCancel() {
        scrollAndTap(CANCEL_MEMBERSHIP);
        return this;
    }

    /** True while the refund is being computed — the screen's own progress copy. */
    public boolean showsRefundCalculation() {
        return isPresent(descContains(CALCULATING_REFUND), Duration.ofSeconds(15));
    }

    /** True when the retention alternatives are offered instead of a bare confirm. */
    public boolean showsRetentionOptions() {
        return isPresentAfterScroll(PAUSE_INSTEAD) || isPresentAfterScroll("Not ready to leave?");
    }

    /** Backs out of the cancellation. */
    public ClientMembershipScreen keepMembership() {
        tap(descContains(KEEP_MEMBERSHIP));
        return this;
    }

    // ---- billing history ----------------------------------------------------

    public boolean isBillingHistoryLoaded() {
        return isPresent(descContains(BILLING_HISTORY), Duration.ofSeconds(25));
    }

    public boolean showsNoInvoices() {
        return isPresent(descContains(NO_INVOICES), Duration.ofSeconds(10));
    }

    public boolean hasAnyInvoice() {
        return isPresentAfterScroll("PDF");
    }
}
