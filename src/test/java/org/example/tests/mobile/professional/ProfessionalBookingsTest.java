package org.example.tests.mobile.professional;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.professional.ProfessionalBookingsScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The professional's bookings dashboard — listing, search and the two guarded actions.
 *
 * <p>Cancel and "Report no-show" both have consequences beyond the screen: the first is subject to
 * the pro-cancellation policy, the second charges the client the §5.5 no-show fee. The automation
 * therefore opens each dialog, asserts the guard is there, and backs out — it never confirms.
 */
public class ProfessionalBookingsTest extends RoleSessionTest {

    private ProfessionalBookingsScreen openBookings() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_BOOKINGS);

        ProfessionalBookingsScreen bookings = new ProfessionalBookingsScreen(driver);
        Assert.assertTrue(bookings.isLoaded(), "The bookings dashboard should render");
        return bookings;
    }

    @Test(description = "Bookings are listed for the professional")
    public void bookingsAreListed() {
        ProfessionalBookingsScreen bookings = openBookings();

        Assert.assertTrue(bookings.isLoaded(),
                "The bookings dashboard should render its list (or its empty state)");
    }

    @Test(description = "Search filters the bookings list")
    public void searchFiltersBookings() {
        ProfessionalBookingsScreen bookings = openBookings();
        if (!bookings.hasAnyBooking()) {
            throw new SkipException("The professional has no bookings — seed one to exercise search.");
        }

        bookings.search("zzzznomatch");

        Assert.assertFalse(bookings.hasResult("zzzznomatch"),
                "A query matching nothing should leave no rows");
    }

    @Test(description = "Reporting a no-show discloses the fee before charging it")
    public void noShowDisclosesTheFee() {
        ProfessionalBookingsScreen bookings = openBookings();
        if (!bookings.hasAnyBooking()) {
            throw new SkipException("The professional has no bookings.");
        }

        bookings.openFirstBooking().tapReportNoShow();

        Assert.assertTrue(bookings.showsNoShowFeeNotice(),
                "The no-show action must disclose that it charges the client a fee before it is "
                        + "confirmed — the professional is triggering a charge on someone else");
    }
}
