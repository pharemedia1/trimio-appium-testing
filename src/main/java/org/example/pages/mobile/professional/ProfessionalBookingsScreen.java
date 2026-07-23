package org.example.pages.mobile.professional;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The professional's bookings dashboard —
 * {@code screens/professional/booking/professional_booking_deshbord.dart} and the appointment
 * detail it opens.
 *
 * <p>Two actions here have money and policy consequences and therefore both need a confirmation
 * step: cancelling an appointment (which is subject to the pro-cancellation policy) and reporting a
 * client no-show, which the UI states outright "Charges the §5.5 no-show fee". A regression that
 * lets either fire without confirmation charges somebody by accident.
 */
public class ProfessionalBookingsScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String TITLE = "Bookings";
    public static final String SEARCH_HINT = "Search clients or services";
    public static final String VIEW_DETAILS = "View details";
    public static final String CANCEL_APPOINTMENT = "Cancel appointment";
    public static final String KEEP = "Keep";
    public static final String REPORT_NO_SHOW = "Report no-show";
    public static final String NO_SHOW_FEE_NOTE = "Charges the §5.5 no-show fee";
    public static final String ONLY_THIS_VISIT = "Only this visit";
    public static final String ALL_FUTURE_VISITS = "All future visits";
    public static final String SANITATION_CHECKLIST = "Sanitation checklist";
    public static final String SAVE_ATTESTATION = "Save attestation";
    public static final String MESSAGE_CLIENT = "Message Client";

    /** Empty state of the timeline, verified on-device (the range word varies: today/week/month). */
    public static final String EMPTY_TIMELINE = "bookings yet.";

    public ProfessionalBookingsScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(TITLE), Duration.ofSeconds(25))
                || isPresent(descContains(SEARCH_HINT), Duration.ofSeconds(10));
    }

    /**
     * True if at least one booking row is listed.
     *
     * <p>Deliberately <em>not</em> a search for "$": the screen's own KPI strip renders
     * "Est. Revenue $0" even when there is nothing booked, so a dollar sign proves only that the
     * page loaded. The empty state is the reliable signal, so this asks the negative question.
     */
    public boolean hasAnyBooking() {
        if (isPresent(descContains(EMPTY_TIMELINE), SHORT_TIMEOUT)) {
            return false;
        }
        return isPresentAfterScroll(VIEW_DETAILS);
    }

    /** Filters the list by client or service name. */
    public ProfessionalBookingsScreen search(String query) {
        LOG.info("ProBookings: searching '{}'", query);
        type(editText(0), query);
        hideKeyboard();
        return this;
    }

    /** True if a row matching {@code text} survived the filter. */
    public boolean hasResult(String text) {
        return isPresentAfterScroll(text);
    }

    /** Opens the first booking's detail. */
    public ProfessionalBookingsScreen openFirstBooking() {
        scrollAndTap(VIEW_DETAILS);
        return this;
    }

    // ---- cancellation -------------------------------------------------------

    public ProfessionalBookingsScreen tapCancel() {
        scrollAndTap(CANCEL_APPOINTMENT);
        return this;
    }

    /** Backs out of the cancel confirmation. */
    public ProfessionalBookingsScreen keep() {
        tap(accId(KEEP));
        return this;
    }

    /** True when the recurring-cancel scope choice is offered. */
    public boolean showsRecurringCancelChoice() {
        return isPresent(descContains(ONLY_THIS_VISIT), Duration.ofSeconds(10))
                || isPresent(descContains(ALL_FUTURE_VISITS), Duration.ofSeconds(5));
    }

    // ---- no-show ------------------------------------------------------------

    public ProfessionalBookingsScreen tapReportNoShow() {
        scrollAndTap(REPORT_NO_SHOW);
        return this;
    }

    /** True when the no-show dialog discloses the fee it will charge. */
    public boolean showsNoShowFeeNotice() {
        return isPresent(descContains("no-show fee"), Duration.ofSeconds(10));
    }

    // ---- sanitation ---------------------------------------------------------

    /** Opens the sanitation attestation, ticks all three items and saves. */
    public ProfessionalBookingsScreen completeSanitationChecklist() {
        scrollAndTap(SANITATION_CHECKLIST);
        for (int i = 0; i < 3; i++) {
            if (isPresent(checkable(i), SHORT_TIMEOUT)) {
                tap(checkable(i));
            }
        }
        scrollAndTap(SAVE_ATTESTATION);
        return this;
    }

    /** Opens the in-app chat with the client. */
    public ProfessionalBookingsScreen messageClient() {
        scrollAndTap(MESSAGE_CLIENT);
        return this;
    }
}
