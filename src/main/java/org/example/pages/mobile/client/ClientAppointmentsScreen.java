package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The client Appointments tab and its detail screen —
 * {@code screens/client/appointment/client_appointment.dart} (+ {@code …_detail_page.dart}).
 *
 * <p>Appointments are grouped into tabs (upcoming / past / cancelled); an empty tab renders
 * "No &lt;tab&gt; appointments." with the tab name lower-cased. Cancellation is the interesting part:
 * for a recurring series the app must ask whether to cancel only the next visit or the whole series
 * ("Cancel next upcoming visit only?" / "All future visits"), and getting that wrong silently
 * destroys a client's standing booking.
 */
public class ClientAppointmentsScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String NO_CURRENT = "No current appointments.";
    public static final String CANCEL_APPOINTMENT = "Cancel appointment";
    public static final String KEEP_APPOINTMENT = "Keep appointment";
    public static final String CANCEL_NEXT_ONLY = "Cancel next upcoming visit only?";
    public static final String ONLY_THIS_VISIT = "Only this visit";
    public static final String ALL_FUTURE_VISITS = "All future visits";
    public static final String UPDATE_DATE_TIME = "Update date / time";
    public static final String RECURRING = "Recurring appointment";
    public static final String NO_MILEAGE = "No mileage data available.";
    public static final String NO_PAYMENT = "No payment recorded.";
    public static final String NO_SERVICES = "No services added.";
    public static final String REVIEW_THANKS = "Thanks for your review!";

    // ---- the Bookings hub (verified on-device) ------------------------------
    /**
     * The Appointments tab is a <em>summary hub</em>, not a tabbed list: a "Bookings" header over
     * Past / Current / Future cards (each with its own count and an "Open …" link) plus a month
     * calendar. The per-appointment list lives one level down, behind those links.
     */
    public static final String TITLE = "Bookings";
    public static final String SUBTITLE = "Manage past, current and future bookings in one place.";
    public static final String SECTION_PAST = "Past";
    public static final String SECTION_CURRENT = "Current";
    public static final String SECTION_FUTURE = "Future";
    public static final String OPEN_HISTORY = "Open history";
    public static final String OPEN_CURRENT = "Open current";
    public static final String OPEN_SCHEDULE = "Open schedule";

    public ClientAppointmentsScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(TITLE), Duration.ofSeconds(25));
    }

    /** True when the hub rendered all three summary cards. */
    public boolean showsSummarySections() {
        return isPresentAfterScroll(SECTION_PAST)
                && isPresentAfterScroll(SECTION_CURRENT)
                && isPresentAfterScroll(SECTION_FUTURE);
    }

    /** Opens one of the hub's drill-downs ("Open history" / "Open current" / "Open schedule"). */
    public ClientAppointmentsScreen openSection(String openLink) {
        LOG.info("Appointments: opening '{}'", openLink);
        scrollAndTap(openLink);
        return this;
    }

    /**
     * True if this client has any booking at all, read from the hub's own counts.
     * The cards render "<n> visits" / "<n> active" / "<n> booked", so a client with nothing shows
     * zeros rather than an empty-list message.
     */
    public boolean hasAnyAppointment() {
        return !isPresentAfterScroll("0 visits")
                || !isPresentAfterScroll("0 active")
                || !isPresentAfterScroll("0 booked");
    }

    /** Drills into the current bookings list and opens the first appointment there. */
    public ClientAppointmentsScreen openFirst() {
        openSection(OPEN_CURRENT);
        scrollAndTap("Appointment #");
        return this;
    }

    // ---- detail assertions --------------------------------------------------

    /** True when the detail screen shows the appointment number header. */
    public boolean detailIsLoaded() {
        return isPresent(descContains("Appointment #"), Duration.ofSeconds(20));
    }

    /** True when a total price is rendered on the detail. */
    public boolean detailShowsTotal() {
        return isPresentAfterScroll("Total:");
    }

    /** True when the appointment is part of a recurring series. */
    public boolean isRecurring() {
        return isPresentAfterScroll(RECURRING) || isPresentAfterScroll("Pattern:");
    }

    // ---- actions ------------------------------------------------------------

    /** Opens the cancel dialog. */
    public ClientAppointmentsScreen tapCancel() {
        scrollAndTap(CANCEL_APPOINTMENT);
        return this;
    }

    /** Backs out of the cancel dialog. */
    public ClientAppointmentsScreen keepAppointment() {
        tap(accId(KEEP_APPOINTMENT));
        return this;
    }

    /** True when the recurring-cancel scope choice is offered. */
    public boolean showsRecurringCancelChoice() {
        return isPresent(descContains(CANCEL_NEXT_ONLY), Duration.ofSeconds(10))
                || isPresent(descContains(ALL_FUTURE_VISITS), Duration.ofSeconds(5));
    }

    /** Cancels only the next visit of a series. */
    public ClientAppointmentsScreen cancelOnlyThisVisit() {
        tap(descContains(ONLY_THIS_VISIT));
        return this;
    }

    /** Cancels the whole series. */
    public ClientAppointmentsScreen cancelAllFutureVisits() {
        tap(descContains(ALL_FUTURE_VISITS));
        return this;
    }

    /** Opens the reschedule flow. */
    public ClientAppointmentsScreen updateDateTime() {
        scrollAndTap(UPDATE_DATE_TIME);
        return this;
    }
}
