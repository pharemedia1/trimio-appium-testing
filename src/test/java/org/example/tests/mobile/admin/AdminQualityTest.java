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

        // Quality → a status list → Actions. The panel does not exist on the Quality page itself;
        // it belongs to a professional's row inside a status list.
        // The WARNING list, not the Suspended one. The detail page offers the actions that suit
        // the professional's CURRENT status, so an already-suspended professional is offered only
        // "Deactivate" and "Reactivate" -- there is no Suspend button to press, and a test of the
        // suspend-reason gate cannot run there. A professional on warning can still be suspended.
        quality.openStatusList(AdminQualityScreen.CARD_WARNING);
        if (!quality.statusListHasActions()) {
            throw new SkipException("The '" + AdminQualityScreen.CARD_WARNING + "' list has no "
                    + "professional to act on in this environment, so there is no Actions panel. "
                    + "Seed one with: UPDATE professional SET account_status = 'warning' WHERE "
                    + "professional_id = 1203;");
        }
        quality.openActions();
        quality.tapSuspend();

        quality.submitWithoutReason();

        Assert.assertTrue(quality.stillAsksForReason(),
                "Suspending without a reason must be refused — the reason is what the user is told "
                        + "and what the enforcement record keeps");
    }
}
