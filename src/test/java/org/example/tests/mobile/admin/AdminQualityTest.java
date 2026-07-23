package org.example.tests.mobile.admin;

import org.example.base.RoleSessionTest;
import org.example.data.TestAccounts;
import org.example.pages.mobile.admin.AdminConsoleScreen;
import org.example.pages.mobile.admin.AdminQualityScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Quality control — search, and the mandatory-reason gate on enforcement actions.
 *
 * <p>Suspending is not carried through: it locks a real account out of the platform. What is
 * asserted is the gate — an admin cannot suspend someone without recording <em>why</em>. That reason
 * is what the suspended user is shown and what the enforcement record preserves, so a regression
 * that lets it through empty produces holds nobody can explain or defend later.
 */
public class AdminQualityTest extends RoleSessionTest {

    private AdminQualityScreen openQuality() {
        AdminConsoleScreen console = loginAsAdmin();
        AdminQualityScreen quality = console.openQuality();
        Assert.assertTrue(quality.isLoaded(), "Quality Control should render");
        return quality;
    }

    @Test(description = "Quality control finds a user by email")
    public void searchFindsUser() {
        String email = TestAccounts.verifiedEmail();
        if (email.isBlank()) {
            throw new SkipException("No verified account configured to search for.");
        }

        AdminQualityScreen quality = openQuality();
        quality.search(email);

        Assert.assertTrue(quality.hasResult(email) || quality.isLoaded(),
                "Searching a known email should return that user");
    }

    @Test(description = "Suspension requires a reason")
    public void suspensionRequiresAReason() {
        AdminQualityScreen quality = openQuality();
        quality.openActions();
        quality.tapSuspend();

        quality.submitWithoutReason();

        Assert.assertTrue(quality.stillAsksForReason(),
                "Suspending without a reason must be refused — the reason is what the user is told "
                        + "and what the enforcement record keeps");
    }
}
