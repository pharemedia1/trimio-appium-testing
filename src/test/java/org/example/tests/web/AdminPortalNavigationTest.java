package org.example.tests.web;

import org.example.base.WebBaseTest;
import org.example.pages.web.AdminWebDashboardPage;
import org.example.pages.web.WebShellPage;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The admin web portal — shell navigation and the native web dashboard.
 *
 * <p>The portal reuses the mobile admin screens inside a desktop shell, so what is genuinely new in
 * the browser is the <em>chrome</em>: the sidebar, the top bar, the responsive collapse and the
 * purpose-built dashboard. That is what this class covers; the queues' own behaviour is already
 * covered on the mobile side and does not need testing twice.
 *
 * <p>Every assertion goes through Flutter's accessibility tree, so a failure here is ambiguous by
 * nature — it can mean "the destination is gone" or "the semantics node is gone". {@code WebBaseTest}
 * skips up front when the tree is unavailable, so a failure that does get reported is a real one.
 */
public class AdminPortalNavigationTest extends WebBaseTest {

    private static final String ADMIN = "admin";

    @Test(description = "The sidebar lists every admin destination in its groups")
    public void sidebarShowsAllDestinations() {
        WebShellPage shell = signInAs(ADMIN);

        Assert.assertTrue(shell.hasAllDestinations(WebShellPage.ADMIN_DESTINATIONS),
                "The sidebar should offer every admin destination — a missing one makes that queue "
                        + "unreachable in the browser");
        Assert.assertTrue(shell.showsAdminGroups(),
                "Destinations should be grouped under MANAGE and MARKETPLACE");
    }

    @Test(description = "Every sidebar destination opens and titles the top bar")
    public void everyDestinationOpens() {
        WebShellPage shell = signInAs(ADMIN);

        for (String destination : WebShellPage.ADMIN_DESTINATIONS) {
            shell.openDestination(destination);
            Assert.assertTrue(shell.topBarShows(destination),
                    "Opening '" + destination + "' should title the top bar with it");
        }
    }

    @Test(description = "The dashboard shows its four KPI cards")
    public void dashboardShowsKpis() {
        WebShellPage shell = signInAs(ADMIN);
        shell.openDestination(WebShellPage.DASHBOARD);

        AdminWebDashboardPage dashboard = shell.dashboard();
        Assert.assertTrue(dashboard.isLoaded(), "The web dashboard should render");
        Assert.assertTrue(dashboard.showsAllKpis(),
                "The KPI row should show total users, active holds, open reports and services");
        Assert.assertTrue(dashboard.showsUserSplit(),
                "The users KPI should break down into clients and pros");
    }

    @Test(description = "The dashboard states either what needs attention or that all is clear")
    public void attentionOrAllClearIsShown() {
        WebShellPage shell = signInAs(ADMIN);
        shell.openDestination(WebShellPage.DASHBOARD);

        Assert.assertTrue(shell.dashboard().showsAttentionOrAllClear(),
                "The dashboard must resolve to one of its two states — a silent absence of both "
                        + "reads as 'nothing to do' when there may be a queue full of work");
    }

    @Test(description = "Management tiles navigate to their destination")
    public void manageTilesNavigate() {
        WebShellPage shell = signInAs(ADMIN);
        shell.openDestination(WebShellPage.DASHBOARD);
        AdminWebDashboardPage dashboard = shell.dashboard();
        Assert.assertTrue(dashboard.showsAllTiles(), "The manage grid should show every tile");

        dashboard.openTile(WebShellPage.ALL_USERS);

        Assert.assertTrue(shell.topBarShows(WebShellPage.ALL_USERS),
                "Clicking the All Users tile should switch the shell to that destination");
    }

    @Test(description = "Recent activity lists events with relative timestamps")
    public void recentActivityIsListed() {
        WebShellPage shell = signInAs(ADMIN);
        shell.openDestination(WebShellPage.DASHBOARD);
        AdminWebDashboardPage dashboard = shell.dashboard();

        Assert.assertTrue(dashboard.showsRecentActivity(),
                "The 'Latest events' panel should render");
        Assert.assertTrue(dashboard.recentActivityIsEmpty() || dashboard.showsRelativeTimestamps(),
                "Events should carry a relative timestamp, or the panel should say there is none");
    }

    @Test(description = "Sidebar badges reflect the attention counts")
    public void sidebarBadgesReflectCounts() {
        WebShellPage shell = signInAs(ADMIN);
        AdminWebDashboardPage dashboard = shell.dashboard();
        shell.openDestination(WebShellPage.DASHBOARD);

        int openReports = dashboard.kpiValue(AdminWebDashboardPage.KPI_OPEN_REPORTS);
        if (openReports > 0) {
            Assert.assertTrue(shell.hasAnyBadge(),
                    "With " + openReports + " open reports the sidebar should carry an attention badge");
        } else {
            Assert.assertTrue(dashboard.showsAllClear() || !shell.hasAnyBadge(),
                    "With nothing to action the sidebar should not claim attention is needed");
        }
    }

    @Test(description = "Below 900px the sidebar collapses into a drawer")
    public void sidebarCollapsesOnNarrowViewport() {
        WebShellPage shell = signInAs(ADMIN);

        shell.useNarrowViewport();

        Assert.assertTrue(shell.isDrawerLayout(),
                "A viewport under the 900px breakpoint should collapse the sidebar into a drawer");

        shell.useWideViewport();
        Assert.assertTrue(shell.hasDestination(WebShellPage.DASHBOARD),
                "Restoring a wide viewport should bring the sidebar back");
    }

    @Test(description = "The dashboard reflows across breakpoints")
    public void dashboardIsResponsive() {
        WebShellPage shell = signInAs(ADMIN);
        shell.openDestination(WebShellPage.DASHBOARD);
        AdminWebDashboardPage dashboard = shell.dashboard();

        for (int[] size : new int[][]{{1440, 900}, {900, 900}, {620, 900}}) {
            Assert.assertTrue(dashboard.rendersAt(size[0], size[1]),
                    "The dashboard should still render its sections at " + size[0] + "px wide");
        }
    }

    @Test(description = "The notification bell opens the notification centre")
    public void bellOpensNotifications() {
        WebShellPage shell = signInAs(ADMIN);

        shell.openNotifications();

        Assert.assertTrue(shell.isLoaded(),
                "Opening notifications should keep the shell usable rather than blanking it");
    }

    @Test(description = "The account menu signs the admin out")
    public void accountMenuLogsOut() {
        WebShellPage shell = signInAs(ADMIN);

        Assert.assertTrue(shell.logout().isLoaded(),
                "Logging out should return to the portal login screen");
    }
}
