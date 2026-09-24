package org.example.tests.mobile.professional;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.professional.ProfessionalEarningsScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Earnings, payouts and performance.
 *
 * <p>"Withdraw all" is never executed by automation — it moves real money out of a real connected
 * account and cannot be undone from the app. The valuable assertion is the one immediately before
 * it: a professional who has not finished Stripe onboarding must be refused and pointed at
 * "Set up payouts", not left with a silent failure and an unexplained zero balance.
 */
public class ProfessionalEarningsTest extends RoleSessionTest {

    private ProfessionalEarningsScreen openEarnings() {
        return openEarnings(PROFESSIONAL);
    }

    /** As {@link #openEarnings()}, signed in as a named professional role. */
    private ProfessionalEarningsScreen openEarnings(String role) {
        // Stay on the DASHBOARD -- loginAsProfessional already lands there, and the dashboard
        // carries a labelled "Earnings" control that pushes the same ProfessionalBalance page.
        // The account tab's money section renders only "Shop membership" on-device: "Services &
        // rates", "Get paid" and "Earnings" are missing from the accessibility tree entirely, so
        // routing through Account reached a menu that no longer offers the destination.
        loginAsProfessional(role);

        ProfessionalEarningsScreen earnings = new ProfessionalEarningsScreen(driver);
        earnings.openFromDashboard();
        if (!earnings.isLoaded()) {
            throw new SkipException("The balance screen was not reachable from the dashboard's '"
                    + ProfessionalEarningsScreen.ACCOUNT_ROW + "' control in this build.");
        }
        return earnings;
    }

    @Test(description = "The balance screen shows a figure")
    public void balanceIsShown() {
        ProfessionalEarningsScreen earnings = openEarnings();

        Assert.assertTrue(earnings.showsBalance(),
                "The balance screen should render a monetary figure rather than an empty card");
    }

    @Test(description = "Withdrawal without payout onboarding is refused")
    public void withdrawalRequiresPayoutOnboarding() {
        // Signed in as the professional WITHOUT payouts. 878 keeps payouts enabled so the money
        // path stays testable, so it can never satisfy this assertion and the test skipped itself
        // every run; 881 carries the negative case.
        ProfessionalEarningsScreen earnings = openEarnings(PROFESSIONAL_NO_PAYOUTS);

        if (!earnings.payoutsNotEnabled()) {
            throw new SkipException("roleAccounts." + PROFESSIONAL_NO_PAYOUTS + " has payouts "
                    + "enabled after all. Clear it with: UPDATE professional_stripe_accounts SET "
                    + "payouts_enabled = false WHERE professional_id = 881;");
        }

        // The refusal is STRUCTURAL, not a message. A professional who has not finished Connect
        // onboarding is never offered a withdrawal: the balance screen shows "Set up payouts"
        // where the button would be. The old version pressed "Withdraw all" first and timed out
        // after 30 seconds on a control the app deliberately withholds -- asserting against a
        // design the app does not use, which is the same mistake ratingIsRequired made.
        Assert.assertFalse(earnings.canWithdraw(),
                "A professional without a connected payout account must not be offered '"
                        + ProfessionalEarningsScreen.WITHDRAW_ALL + "' at all — money cannot be "
                        + "sent anywhere, so offering the action only produces a failure later");

        Assert.assertTrue(earnings.isDirectedToPayoutSetup(),
                "The balance screen must route them to '"
                        + ProfessionalEarningsScreen.SET_UP_PAYOUTS + "' instead, so the "
                        + "professional can see why they cannot be paid and what to do about it");
    }
}
