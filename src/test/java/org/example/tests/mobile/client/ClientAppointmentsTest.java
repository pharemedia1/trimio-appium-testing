package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.client.ClientAppointmentsScreen;
import org.example.pages.mobile.common.BottomNavBar;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The client Appointments tab — list, detail and the cancellation gates.
 *
 * <p>Cancellation is read-only here on purpose. Cancelling a real appointment cancels a real
 * professional's paid work and may trigger the cancellation-fee policy, so the automation asserts
 * that the <em>guards</em> are in place — the "Keep appointment" escape and, for a series, the
 * "Only this visit" vs "All future visits" choice — and stops before confirming. Actually destroying
 * a booking stays a manual case.
 */
public class ClientAppointmentsTest extends RoleSessionTest {

    private ClientAppointmentsScreen openAppointments() {
        loginAsClient();
        new BottomNavBar(driver).open(BottomNavBar.CLIENT_APPOINTMENTS);

        ClientAppointmentsScreen appointments = new ClientAppointmentsScreen(driver);
        Assert.assertTrue(appointments.isLoaded(), "The Appointments tab should render");
        return appointments;
    }

    @Test(description = "The bookings hub summarises past, current and future bookings")
    public void tabsListAppointments() {
        ClientAppointmentsScreen appointments = openAppointments();

        Assert.assertTrue(appointments.showsSummarySections(),
                "The Appointments tab should show its Past / Current / Future summary cards");
    }

    @Test(description = "Appointment detail shows the number and total")
    public void detailShowsSummary() {
        ClientAppointmentsScreen appointments = openAppointments();
        if (!appointments.hasAnyAppointment()) {
            throw new SkipException("The signed-in client has no appointments — book one (or seed "
                    + "one) to exercise the detail screen.");
        }

        appointments.openFirst();

        Assert.assertTrue(appointments.detailIsLoaded(),
                "The detail screen should show the 'Appointment #<id>' header");
        Assert.assertTrue(appointments.detailShowsTotal(),
                "The detail screen should show the appointment total");
    }

    @Test(description = "Cancelling offers an escape before anything is destroyed")
    public void cancelOffersKeepAppointment() {
        ClientAppointmentsScreen appointments = openAppointments();
        if (!appointments.hasAnyAppointment()) {
            throw new SkipException("The signed-in client has no appointments.");
        }

        appointments.openFirst().tapCancel();
        appointments.keepAppointment();

        Assert.assertTrue(appointments.detailIsLoaded(),
                "'Keep appointment' should abort the cancellation and leave the appointment intact");
    }

    @Test(description = "A recurring appointment asks which visits to cancel")
    public void recurringCancelAsksForScope() {
        ClientAppointmentsScreen appointments = openAppointments();
        if (!appointments.hasAnyAppointment()) {
            throw new SkipException("The signed-in client has no appointments.");
        }

        appointments.openFirst();
        if (!appointments.isRecurring()) {
            throw new SkipException("The first appointment is not part of a recurring series — seed "
                    + "a series to exercise the cancellation-scope choice.");
        }

        appointments.tapCancel();

        Assert.assertTrue(appointments.showsRecurringCancelChoice(),
                "Cancelling a series must ask whether to cancel only the next visit or all future "
                        + "visits — silently cancelling the whole series destroys a standing booking");
        appointments.keepAppointment();
    }
}
