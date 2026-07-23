package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.example.pages.mobile.common.BottomNavBar;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Client discovery — the Home tab's search and the Book tab's service catalogue
 * ({@code home_screen.dart} / {@code activity/activityPage.dart}).
 *
 * <p>The Book tab hosts no booking flow of its own: it is a browse surface that hands off to Home,
 * which owns the flow. That indirection is easy to break during navigation refactors and invisible
 * from the code, so it gets its own assertion.
 */
public class ClientHomeTest extends RoleSessionTest {

    @Test(description = "Home search returns matching services, pros or styles")
    public void searchReturnsResults() {
        ClientHomeScreen home = loginAsProvisionedClient();
        Assert.assertTrue(home.isLoaded(), "The Home tab should render");

        home.searchFromHome("cut");

        // Any result at all is the assertion — the catalogue is environment-specific, so pinning a
        // particular service name here would make the test a fixture check rather than a search check.
        Assert.assertTrue(home.hasResult("cut") || home.hasResult("Cut"),
                "Searching 'cut' should surface at least one matching service or professional");
    }

    @Test(description = "The Book tab lists service categories and 'Browse all services'")
    public void bookTabListsServices() {
        ClientHomeScreen home = loginAsProvisionedClient();
        home.nav().open(BottomNavBar.CLIENT_BOOK);

        Assert.assertTrue(home.isDiscoveryLoaded(), "The Book (discovery) tab should render");
        Assert.assertTrue(home.hasResult("Browse all services"),
                "Discovery should offer 'Browse all services'");
    }

    @Test(description = "The Book tab directs the user to Home to start a booking")
    public void bookTabHandsOffToHome() {
        ClientHomeScreen home = loginAsProvisionedClient();
        home.nav().open(BottomNavBar.CLIENT_BOOK);
        Assert.assertTrue(home.isDiscoveryLoaded(), "The Book tab should render");

        home.startBooking();

        // The shell switches to the Home tab (BottomnavigationBar.switchTab) rather than pushing a
        // route, so the assertion is "we are on Home", not "a sheet opened".
        Assert.assertTrue(home.isLoaded() || home.showsHandoffHint(),
                "Booking from the Book tab should land on Home, which owns the booking flow");
    }
}
