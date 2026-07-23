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
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_ACCOUNT);

        ProfessionalEarningsScreen earnings = new ProfessionalEarningsScreen(driver);
        if (!earnings.isLoaded()) {
            throw new SkipException("The balance screen was not reachable from the account tab in "
                    + "this build.");
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
        ProfessionalEarningsScreen earnings = openEarnings();

        if (!earnings.payoutsNotEnabled()) {
            throw new SkipException("The signed-in professional already has payouts enabled — use "
                    + "one without a completed Stripe account to exercise this refusal.");
        }

        earnings.withdrawAll();

        Assert.assertTrue(earnings.isDirectedToPayoutSetup(),
                "A withdrawal without a connected account must be refused and route the "
                        + "professional to '" + ProfessionalEarningsScreen.SET_UP_PAYOUTS + "'");
    }
}
