package org.example.pages.mobile.professional;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Earnings — {@code screens/professional/balence/professional_balance.dart},
 * {@code bankDetails/professional_bank_details_add.dart}, and the performance/trends dashboards.
 *
 * <p>"Withdraw all" is the one irreversible action a professional can take from their phone, and it
 * is only legal once Stripe onboarding has completed. The pair of assertions worth keeping green are
 * therefore: the balance reconciles with completed work, and withdrawal is refused (with a route to
 * "Set up payouts") when payouts are not enabled.
 */
public class ProfessionalEarningsScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String WITHDRAW_ALL = "Withdraw all";
    public static final String REFRESH = "Refresh";
    public static final String PAYOUTS = "Payouts";
    public static final String SET_UP_PAYOUTS = "Set up payouts";
    public static final String MANAGE_PAYOUT_ACCOUNT = "Manage payout account";
    public static final String REFRESH_STATUS = "Refresh status";
    public static final String RETRY = "Retry";

    // ---- trends -------------------------------------------------------------
    public static final String NO_BOOKINGS_IN_RANGE = "No bookings in this range yet.";

    public ProfessionalEarningsScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(WITHDRAW_ALL), Duration.ofSeconds(25))
                || isPresent(descContains("$"), Duration.ofSeconds(15));
    }

    /** The headline balance as a number; -1 when unreadable. */
    public double balance() {
        By amount = descContains("$");
        if (!isPresent(amount, Duration.ofSeconds(15))) {
            return -1;
        }
        var element = find(amount);
        String raw = element == null ? "" : element.getAttribute("content-desc");
        return org.example.pages.mobile.client.ClientBookingFlowScreen.parseAmount(raw);
    }

    /** True when a balance figure is rendered at all. */
    public boolean showsBalance() {
        return balance() >= 0;
    }

    /** Re-reads the balance from the server. */
    public ProfessionalEarningsScreen refresh() {
        tap(accId(REFRESH));
        return this;
    }

    /** Attempts a full withdrawal. */
    public ProfessionalEarningsScreen withdrawAll() {
        LOG.info("Earnings: tapping 'Withdraw all'");
        scrollAndTap(WITHDRAW_ALL);
        return this;
    }

    /** True when the withdrawal was refused and the user is pointed at payout onboarding. */
    public boolean isDirectedToPayoutSetup() {
        return isPresentAfterScroll(SET_UP_PAYOUTS);
    }

    // ---- payout account -----------------------------------------------------

    /** Opens the payout-account screen. */
    public ProfessionalEarningsScreen openPayoutAccount() {
        scrollAndTap(MANAGE_PAYOUT_ACCOUNT);
        return this;
    }

    /** True when payouts are not yet enabled (the onboarding CTA is showing). */
    public boolean payoutsNotEnabled() {
        return isPresentAfterScroll(SET_UP_PAYOUTS);
    }

    /** Starts Stripe onboarding. */
    public ProfessionalEarningsScreen startPayoutSetup() {
        scrollAndTap(SET_UP_PAYOUTS);
        return this;
    }

    /** Re-reads the Stripe account status. */
    public ProfessionalEarningsScreen refreshPayoutStatus() {
        scrollAndTap(REFRESH_STATUS);
        return this;
    }

    // ---- performance / trends ----------------------------------------------

    /** True when a performance/trends surface rendered rather than erroring out. */
    public boolean performanceLoaded() {
        return !isPresent(accId(RETRY), Duration.ofSeconds(8));
    }

    /** True when the selected trend range contains no bookings. */
    public boolean showsNoBookingsInRange() {
        return isPresentAfterScroll(NO_BOOKINGS_IN_RANGE);
    }

    /** True when the trends dashboard summarises bookings and revenue. */
    public boolean showsTrendSummary() {
        return isPresentAfterScroll("bookings") && isPresentAfterScroll("$");
    }
}
