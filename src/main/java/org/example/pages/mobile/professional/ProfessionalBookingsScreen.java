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
 * client no-show, which the UI states outright "no-show fee". A regression that
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
    public static final String NO_SHOW_FEE_NOTE = "no-show fee";
    public static final String ONLY_THIS_VISIT = "Only this visit";
    public static final String ALL_FUTURE_VISITS = "All future visits";
    public static final String SANITATION_CHECKLIST = "Sanitation checklist";
    public static final String SAVE_ATTESTATION = "Save attestation";
    public static final String MESSAGE_CLIENT = "Message Client";

    /** Empty state of the timeline, verified on-device (the range word varies: today/week/month). */
    public static final String EMPTY_TIMELINE = "bookings yet.";
    /**
     * The timeline's own count, e.g. "1 booking" / "3 bookings", beside "Today's Timeline".
     *
     * <p>The screen has no per-card "View details" control -- a booking is a single merged card,
     * "C | Casey Client | Men's Haircut + Beard | Confirmed | $97 | 10:00 AM | Sep 23", and the
     * card IS the tap target. Looking for "View details" therefore reported an empty timeline for
     * a professional with 89 appointments and one on screen.
     */
    private static final java.util.regex.Pattern TIMELINE_COUNT =
            java.util.regex.Pattern.compile("^(\\d+) bookings?$");

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
        return timelineCount() > 0 || !bookingCards().isEmpty()
                || isPresentAfterScroll(VIEW_DETAILS);
    }

    /** The number the timeline states, or -1 when it is not on screen. */
    public int timelineCount() {
        for (org.openqa.selenium.WebElement e : findAll(descContains("booking"))) {
            String desc = e.getAttribute("content-desc");
            if (desc == null) {
                continue;
            }
            java.util.regex.Matcher m = TIMELINE_COUNT.matcher(desc.trim());
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return -1;
    }

    /**
     * The booking cards on screen.
     *
     * <p>Identified structurally -- a card carries both a clock time and a price -- because the
     * card has no stable label of its own and its status word varies (Confirmed, Completed,
     * Canceled). Matching on a status would quietly miss whichever one this environment happens
     * to hold.
     */
    private java.util.List<org.openqa.selenium.WebElement> bookingCards() {
        java.util.List<org.openqa.selenium.WebElement> cards = new java.util.ArrayList<>();
        for (org.openqa.selenium.WebElement e : findAll(descContains("$"))) {
            String desc = e.getAttribute("content-desc");
            if (desc != null && (desc.contains(" AM") || desc.contains(" PM"))) {
                cards.add(e);
            }
        }
        return cards;
    }

    /** Filters the list by client or service name. */
    public ProfessionalBookingsScreen search(String query) {
        LOG.info("ProBookings: searching '{}'", query);
        type(editText(0), query);
        hideKeyboard();
        return this;
    }

    /** True if a row matching {@code text} survived the filter. */
    /**
     * True when a booking matching {@code text} is still listed after a search.
     *
     * <p><b>Must not simply look for the text.</b> The search FIELD holds the query once it is
     * typed, so {@code isPresentAfterScroll("zzzznomatch")} found the search box itself and
     * reported a match for a query that matched nothing -- the assertion "a query matching nothing
     * should leave no rows" then failed against a correctly-empty list. So this reads the booking
     * CARDS and asks whether any of them contains the text.
     */
    public boolean hasResult(String text) {
        for (org.openqa.selenium.WebElement card : bookingCards()) {
            String desc = card.getAttribute("content-desc");
            if (desc != null && desc.toLowerCase().contains(text.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** Opens the first booking's detail. */
    public ProfessionalBookingsScreen openFirstBooking() {
        java.util.List<org.openqa.selenium.WebElement> cards = bookingCards();
        if (!cards.isEmpty()) {
            LOG.info("ProBookings: opening {}", cards.get(0).getAttribute("content-desc"));
            cards.get(0).click();
            return this;
        }
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

    /**
     * Opens a booking's action sheet -- "View details", "Report no-show", "Cancel appointment".
     *
     * <p>Tapping a card opens the DETAIL page, which carries none of these: it ends at the
     * earnings breakdown. The sheet is bound to the card's {@code onLongPress}, and to a
     * {@code more_horiz} icon that is a bare {@code GestureDetector} with no {@code Semantics} --
     * no name, no label, nothing to address, so a screen reader cannot reach it either. The
     * long-press is the only accessible route, which is why the test takes it.
     */
    public ProfessionalBookingsScreen openBookingActions() {
        java.util.List<org.openqa.selenium.WebElement> cards = bookingCards();
        if (cards.isEmpty()) {
            throw new org.openqa.selenium.NoSuchElementException(
                    "No booking card to open actions on.");
        }
        LOG.info("ProBookings: long-pressing {}", cards.get(0).getAttribute("content-desc"));
        longPress(cards.get(0));
        return this;
    }

    public ProfessionalBookingsScreen tapReportNoShow() {
        if (!isPresent(descContains(REPORT_NO_SHOW), SHORT_TIMEOUT)) {
            openBookingActions();
        }
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
