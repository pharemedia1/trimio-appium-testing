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
    /**
     * The row on the professional's ACCOUNT tab that opens this screen:
     * "Earnings | Balance, payouts and history".
     *
     * <p>The account tab is a menu of destinations -- verification, what clients see, earnings --
     * not the balance itself. Treating the tab as the balance screen made every earnings test
     * report "the balance screen was not reachable from the account tab", with the entry point
     * sitting on screen the whole time.
     */
    public static final String ACCOUNT_ROW = "Earnings";

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

    /**
     * Opens the balance screen from the professional DASHBOARD.
     *
     * <p><b>Not from the account tab.</b> professionalProfileForm.dart does list an "Earnings |
     * Balance, payouts and history" row under "3 · WORK &amp; MONEY", and it opens the same
     * {@code ProfessionalBalance} page -- but on-device that section renders only its last row,
     * "Shop membership". "Services &amp; rates", "Get paid" and "Earnings" are absent from the
     * accessibility tree entirely: searched across every scroll position after a 15-second wait,
     * {@code getPageSource()} contains neither "Earnings" nor "Balance, payouts". The rows are
     * unconditional in the source and the file has not changed since 2026-09-16, well before this
     * build, so they are being dropped at runtime for a reason that lives in the app, not here.
     *
     * <p>The dashboard carries a properly labelled {@code Semantics(label: 'Earnings')} control
     * that pushes the same page, and {@code loginAsProfessional()} already lands on the dashboard
     * -- so this is both the shorter route and the one that works. The account-tab row is kept as
     * a fallback in case that section starts rendering again.
     */
    public ProfessionalEarningsScreen openFromDashboard() {
        if (isLoaded()) {
            return this;
        }
        if (isPresent(accId(ACCOUNT_ROW), SHORT_TIMEOUT)) {
            LOG.info("ProEarnings: opening '{}' from the dashboard", ACCOUNT_ROW);
            tap(accId(ACCOUNT_ROW));
            return this;
        }
        if (isPresentAfterScroll(ACCOUNT_ROW)) {
            LOG.info("ProEarnings: opening '{}' after scrolling", ACCOUNT_ROW);
            scrollAndTap(ACCOUNT_ROW);
        }
        return this;
    }

    /** @deprecated the account tab's money rows do not render — use {@link #openFromDashboard()}. */
    @Deprecated
    public ProfessionalEarningsScreen openFromAccountTab() {
        return openFromDashboard();
    }

    /**
     * True once the BALANCE screen is showing.
     *
     * <p><b>A bare "$" is not enough</b>, and accepting one caused a silent misnavigation. The
     * professional dashboard renders an earnings widget -- "Earnings · last 14 days | $0 | ..." --
     * so {@code descContains("$")} matched while still ON the dashboard. openFromDashboard() then
     * believed it had already arrived, returned without tapping anything, and the caller looked
     * for payout controls on the dashboard: withdrawalRequiresPayoutOnboarding skipped reporting
     * that the professional "has payouts enabled after all", which was never true.
     *
     * <p>Anchored instead on controls that exist only on the balance screen. "Set up payouts" is
     * included deliberately: a professional who has not finished Connect onboarding sees that
     * INSTEAD of a withdrawal button, and they are exactly who that test signs in as.
     */
    /**
     * True when a withdrawal is actually offered.
     *
     * <p>The app refuses a withdrawal before Connect onboarding by NOT OFFERING ONE: the balance
     * screen shows "{@value #SET_UP_PAYOUTS}" where the withdraw button would be. So the refusal
     * is observed as an absence, and pressing a button that is not there is not something a
     * professional can do.
     */
    public boolean canWithdraw() {
        return isPresent(descContains(WITHDRAW_ALL), SHORT_TIMEOUT);
    }

    public boolean isLoaded() {
        return isPresent(descContains(WITHDRAW_ALL), Duration.ofSeconds(20))
                || isPresent(descContains(SET_UP_PAYOUTS), SHORT_TIMEOUT)
                || isPresent(descContains(MANAGE_PAYOUT_ACCOUNT), SHORT_TIMEOUT)
                || isPresent(descContains(PAYOUTS), SHORT_TIMEOUT);
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
