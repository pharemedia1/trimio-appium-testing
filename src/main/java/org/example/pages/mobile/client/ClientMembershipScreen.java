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
    /**
     * The plan chooser's heading, as the app actually renders it.
     *
     * <p>Was {@code "Choose your plan"}. The card on the account page reads
     * <b>"Choose Your Membership"</b> — different words and different capitalisation, and
     * UiSelector matching is case-sensitive, so the old value matched nothing and the whole
     * membership suite skipped with "the membership area was not reachable from the profile".
     *
     * <p>Worth knowing: the chooser is an inline card ON the Profile tab, not a separate screen.
     */
    public static final String CHOOSE_PLAN = "Choose Your Membership";
    public static final String NO_PLANS = "No plans available right now.";
    public static final String MANAGE_PLAN = "Manage plan";
    /**
     * The card on the PROFILE tab that leads to the manage screen, and the button on it.
     *
     * <p>Not the same string as {@link #MANAGE_PLAN}, which is a control ON the manage screen. The
     * profile's button is the shorter "Manage", so looking for "Manage plan" from the profile
     * found nothing and every membership test concluded the client had no membership at all --
     * with an active subscription and a rendered card reading "Trimio Membership | Active |
     * Current plan: Standard" right there on screen.
     */
    public static final String MEMBERSHIP_CARD = "Trimio Membership";
    public static final String MANAGE_ENTRY = "Manage";
    public static final String UPGRADE_PLAN = "Upgrade your plan";
    public static final String BILLING_HISTORY = "Billing history";
    public static final String NO_INVOICES = "No invoices yet";
    public static final String CANCEL_MEMBERSHIP = "Cancel membership";
    public static final String CANCEL_CONFIRM = "Cancel membership?";
    public static final String KEEP_MEMBERSHIP = "Keep my membership";
    public static final String PAUSE_INSTEAD = "Pause for up to 2 months instead";
    public static final String CALCULATING_REFUND = "Calculating your refund";
    public static final String PAUSE_CONFIRM = "Pause membership?";
    public static final String ACTIVATED = "Membership activated successfully";
    public static final String CONFIRM_MEMBERSHIP = "Confirm membership";
    /**
     * The credit balance's own word. The manage screen renders the count and its caption as
     * separate nodes -- "1" above "credit | available" -- so there is no "<a> of <b> credits left"
     * line anywhere, which is what this used to look for.
     */
    public static final String CREDITS_SUFFIX = "available";
    /** The renewal caption. The screen says "Next renewal", never "renews". */
    public static final String RENEWAL_LINE = "Next renewal";

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

    /**
     * True when a plan card states its billing period — the "/mo" or "/yr" beside the price.
     *
     * <p>Replaces {@code showsAllotment()}, which looked for "in-home cuts/month". No plan says
     * that: {@code accountPage.dart} builds each card from the plan's name, its price suffixed
     * {@code '/mo'} or {@code '/yr'}, and up to four benefit lines drawn from
     * {@code membership_plan_benefits} — whose seeded labels read "10% member pricing on all
     * services", "Priority booking window" and the like. The old anchor could never match, so the
     * assertion failed on a chooser that was describing the plans perfectly well.
     *
     * <p>The period is the right thing to assert: it is the one fact that turns a price into a
     * commitment, and a card that omits it is genuinely misleading.
     */
    public boolean showsBillingPeriod() {
        return isPresentAfterScroll("/mo") || isPresentAfterScroll("/yr");
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

    /** True when the credit balance is rendered ("1" over "credit / available"). */
    public boolean showsCreditsRemaining() {
        return isPresentAfterScroll(CREDITS_SUFFIX);
    }

    /** True when the renewal line ("Next renewal" over the date) is rendered. */
    public boolean showsRenewalLine() {
        return isPresentAfterScroll(RENEWAL_LINE);
    }

    /** True when the profile tab is showing the membership summary card. */
    public boolean showsMembershipCard() {
        return isPresent(descContains(MEMBERSHIP_CARD), Duration.ofSeconds(15));
    }

    /**
     * Opens the manage screen from the profile tab's membership card.
     *
     * <p>Needed because the card and the manage screen are different screens: the card carries the
     * plan name, the credit count and a "Manage" button, and everything the manage tests assert on
     * -- "Manage plan", "Upgrade plan", "Cancel membership" -- is one tap further in.
     */
    public ClientMembershipScreen openManage() {
        scrollAndTapExact(MANAGE_ENTRY);
        return this;
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
