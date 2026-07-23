package org.example.tests.mobile.professional;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.professional.ProfessionalDashboardScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The professional dashboard — landing, tabs and the offer payout disclosure.
 *
 * <p>Accepting and declining offers is not automated: an accepted offer commits a professional to
 * travel to a real address, and a decline feeds the offer-scoring model. What is automated is the
 * disclosure made <em>before</em> that decision — the offer must state what the professional will
 * take home ("you earn $x.xx") and that the convenience fee is Trimio's, not theirs. A pro who
 * accepts on a wrong number finds out after doing the work.
 */
public class ProfessionalDashboardTest extends RoleSessionTest {

    @Test(description = "A professional lands on the professional shell")
    public void professionalLandsOnDashboard() {
        ProfessionalDashboardScreen dashboard = loginAsProfessional();

        Assert.assertTrue(dashboard.isLoaded(),
                "A professional sign-in should land in the professional shell");
    }

    @Test(description = "All five professional tabs open")
    public void dashboardTabsOpen() {
        ProfessionalDashboardScreen dashboard = loginAsProfessional();
        BottomNavBar nav = dashboard.nav();

        for (String tab : new String[]{
                BottomNavBar.PRO_DASHBOARD, BottomNavBar.PRO_BOOKINGS, BottomNavBar.PRO_CLIENT_HUB,
                BottomNavBar.PRO_STORE, BottomNavBar.PRO_ACCOUNT}) {
            nav.open(tab);
            Assert.assertTrue(nav.hasTab(tab), "Tab '" + tab + "' should stay available");
        }
    }

    @Test(description = "An incoming offer discloses what the professional earns")
    public void offerShowsPayoutBreakdown() {
        ProfessionalDashboardScreen dashboard = loginAsProfessional();

        if (!dashboard.hasOffer()) {
            throw new SkipException("No offer is on the dashboard — the professional must be on duty "
                    + "with a dispatched request nearby to exercise the offer card.");
        }

        Assert.assertTrue(dashboard.showsPayoutBreakdown(),
                "An offer must state the professional's take-home ('you earn $x.xx') before they "
                        + "can accept it");
        Assert.assertTrue(dashboard.showsPayoutNote(),
                "The offer should explain the composition — service pay + mileage, with the "
                        + "convenience fee going to Trimio");
    }

    @Test(description = "The professional shell exposes no admin tabs")
    public void professionalShellHasNoAdminTabs() {
        ProfessionalDashboardScreen dashboard = loginAsProfessional();

        Assert.assertFalse(dashboard.nav().hasTab(BottomNavBar.ADMIN_QUALITY),
                "A professional must not see the admin Quality tab");
    }
}
