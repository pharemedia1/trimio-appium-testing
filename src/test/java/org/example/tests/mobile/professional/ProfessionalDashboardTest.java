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
            // NOT a fixture gap, and no amount of seeding fixes it. An offer reaches the
            // dashboard over a SOCKET, pushed by the matching service when a client actually
            // dispatches an on-demand request (services/socket/socketAuth.js) — there is no row
            // that can be inserted to make the card appear, because the app never polls for it.
            // Producing one needs a second device acting as the client, which is what the PAIRED
            // suite is for: OnDemandDispatchTest PAIR-011 dispatches a real request and asserts
            // this very thing, pro.showsPayoutBreakdown(), on the professional's device.
            //
            // So this is left skipping deliberately, with the coverage living where it can
            // actually run: mvn -o test -DsuiteXmlFile=src/test/resources/suites/paired-testng.xml
            throw new SkipException("No offer is on the dashboard. An offer is socket-pushed by a "
                    + "live dispatch, so a single-device run cannot create one — this assertion is "
                    + "covered by OnDemandDispatchTest (PAIR-011) in suites/paired-testng.xml, "
                    + "which dispatches from a second device and asserts the same payout breakdown.");
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
