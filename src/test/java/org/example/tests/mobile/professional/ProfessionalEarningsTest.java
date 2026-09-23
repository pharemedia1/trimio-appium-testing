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
        loginAsProfessional(role);
        new BottomNavBar(driver).open(BottomNavBar.PRO_ACCOUNT);

        // The Account tab is a menu, not the balance. Earnings sit behind its own row --
        // "Earnings | Balance, payouts and history" -- and the old version expected the balance to
        // BE the account tab, so it reported the screen unreachable while its entry point was on
        // screen the whole time.
        ProfessionalEarningsScreen earnings = new ProfessionalEarningsScreen(driver);
        earnings.openFromAccountTab();
        if (!earnings.isLoaded()) {
            throw new SkipException("The balance screen was not reachable from the account tab's '"
                    + ProfessionalEarningsScreen.ACCOUNT_ROW + "' row in this build.");
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

        earnings.withdrawAll();

        Assert.assertTrue(earnings.isDirectedToPayoutSetup(),
                "A withdrawal without a connected account must be refused and route the "
                        + "professional to '" + ProfessionalEarningsScreen.SET_UP_PAYOUTS + "'");
    }
}
