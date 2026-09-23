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

        // Cancel from the LIST. The detail page has no cancel control — it ends at Payment and
        // Mileage Tracking — while every schedule card carries its own "Cancel appointment".
        // Opening the detail first therefore looked for a button that is not on that screen.
        appointments.openSchedule().tapCancel();
        appointments.keepAppointment();

        Assert.assertTrue(appointments.hasCardsListed(),
                "'Keep appointment' should abort the cancellation and leave the schedule intact");
    }

    @Test(description = "A recurring appointment asks which visits to cancel")
    public void recurringCancelAsksForScope() {
        ClientAppointmentsScreen appointments = openAppointments();
        if (!appointments.hasAnyAppointment()) {
            throw new SkipException("The signed-in client has no appointments.");
        }

        // Whether a booking RECURS is only stated on its detail page ("Recurring appointment" /
        // "Pattern:"), but CANCELLING is only possible from the schedule list -- the detail ends
        // at Payment and Mileage Tracking and carries no cancel control at all. So the check and
        // the action happen on two different screens, and the test has to walk back between them.
        // Cancelling straight from the detail spent 30 seconds looking for a "Cancel appointment"
        // that is not on that page.
        appointments.openFirst();
        boolean recurring = appointments.isRecurring();
        appointments.goBack();
        if (!recurring) {
            throw new SkipException("The first appointment is not part of a recurring series — seed "
                    + "a series to exercise the cancellation-scope choice. Mark one with: UPDATE "
                    + "appointments SET is_recurring = true, recurrence_pattern = 'weekly' WHERE "
                    + "appointment_id = <the client's soonest upcoming>;");
        }

        appointments.openSchedule().tapCancel();

        Assert.assertTrue(appointments.showsRecurringCancelChoice(),
                "Cancelling a series must ask whether to cancel only the next visit or all future "
                        + "visits — silently cancelling the whole series destroys a standing booking");
        appointments.keepAppointment();
    }
}
