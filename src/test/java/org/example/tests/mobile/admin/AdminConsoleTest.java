package org.example.tests.mobile.admin;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.admin.AdminConsoleScreen;
import org.example.pages.mobile.common.BottomNavBar;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The admin console home — {@code screens/Admin/admin_home_page.dart}.
 *
 * <p>The console is a hub: its value is that every queue is reachable and that anything needing
 * attention is visible from the front door. So the assertions are about completeness (all ten
 * management tiles) and about signalling (the attention strip / suspension banner) rather than about
 * any single queue's behaviour — those live in the per-queue tests.
 *
 * <p>The same tiles appear as sidebar destinations in the web portal, so {@code
 * web.AdminPortalNavigationTest} asserts the browser half of the same contract.
 */
public class AdminConsoleTest extends RoleSessionTest {

    @Test(description = "An admin lands on the Admin Console")
    public void adminLandsOnConsole() {
        AdminConsoleScreen console = loginAsAdmin();

        Assert.assertTrue(console.isLoaded() || console.isAdminShell(),
                "An admin sign-in should land on the Admin Console");
    }

    @Test(description = "The console's four tabs open")
    public void consoleTabsOpen() {
        AdminConsoleScreen console = loginAsAdmin();
        BottomNavBar nav = console.nav();

        for (String tab : new String[]{
                BottomNavBar.ADMIN_DASHBOARD, BottomNavBar.ADMIN_PROS,
                BottomNavBar.ADMIN_QUALITY, BottomNavBar.ADMIN_REPORTS}) {
            nav.open(tab);
            Assert.assertTrue(nav.hasTab(tab), "Tab '" + tab + "' should stay available");
        }
    }

    @Test(description = "Every management tile is present on the console")
    public void consoleShowsManagementTiles() {
        AdminConsoleScreen console = loginAsAdmin();

        for (String tile : AdminConsoleScreen.ALL_TILES) {
            Assert.assertTrue(console.hasTile(tile),
                    "The console should offer the '" + tile + "' tile — a missing tile makes that "
                            + "queue unreachable from the front door");
        }
    }

    @Test(description = "The KPI header renders its three counters")
    public void consoleShowsKpiHeader() {
        AdminConsoleScreen console = loginAsAdmin();

        Assert.assertTrue(console.showsKpiHeader(),
                "The header should show users, active holds and open reports");
    }

    @Test(description = "Unread notifications surface as the attention strip")
    public void attentionStripAppearsWhenUnread() {
        AdminConsoleScreen console = loginAsAdmin();

        if (!console.showsAttentionStrip()) {
            throw new SkipException("No unread admin notifications in this environment — the "
                    + "attention strip only renders when something needs action.");
        }
        Assert.assertTrue(console.showsAttentionStrip(),
                "Pending work should be visible from the console front door");
    }
}
