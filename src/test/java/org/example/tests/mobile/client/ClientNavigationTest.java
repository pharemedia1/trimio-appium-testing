package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.common.BottomNavBar;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The client shell — {@code NavigationBar/bottomNavigationBar.dart}.
 *
 * <p>Every other client test starts by assuming this shell exists, so it is worth asserting on its
 * own: five tabs, each of which opens its screen. The tabs are backed by independent
 * {@code Navigator}s inside an {@code IndexedStack}, which is why switching away and back must not
 * lose a tab's state.
 */
public class ClientNavigationTest extends RoleSessionTest {

    @Test(description = "All five client tabs are present and each opens its screen")
    public void allTabsOpen() {
        BottomNavBar nav = loginAs(CLIENT);

        Assert.assertTrue(nav.isClientShell(),
                "A client sign-in should land in the client shell (Home/Book/Appointments/Shop/Profile)");

        for (String tab : new String[]{
                BottomNavBar.CLIENT_HOME, BottomNavBar.CLIENT_BOOK, BottomNavBar.CLIENT_APPOINTMENTS,
                BottomNavBar.CLIENT_SHOP, BottomNavBar.CLIENT_PROFILE}) {
            nav.open(tab);
            Assert.assertTrue(nav.hasTab(tab), "Tab '" + tab + "' should stay available after opening it");
        }
    }

    @Test(description = "The client shell exposes no professional or admin tabs")
    public void clientShellHasNoStaffTabs() {
        BottomNavBar nav = loginAs(CLIENT);

        Assert.assertFalse(nav.hasTab(BottomNavBar.PRO_CLIENT_HUB),
                "A client must not see the professional Client Hub tab");
        Assert.assertFalse(nav.hasTab(BottomNavBar.ADMIN_QUALITY),
                "A client must not see the admin Quality tab");
    }
}
