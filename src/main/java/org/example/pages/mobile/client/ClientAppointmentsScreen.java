package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

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
    /** The intermediate dialog that now precedes the cancellation confirmation. */
    public static final String CHOOSE_ACTION = "Choose an action";
    /** The cancellation confirmation's title, reached only through that dialog. */
    public static final String CANCEL_CONFIRM_TITLE = "Cancel for free?";
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
    /**
     * The middle summary card. Titled "Today", not "Current".
     *
     * <p>Verified on-device 2026-08-31 — the hub renders "Past / 6 visits / Open history →",
     * "Today / 3 bookings / Open schedule →" and "Future / 1 booked / Open schedule →".
     */
    public static final String SECTION_TODAY = "Today";
    /** @deprecated the card is called "Today"; kept so the old name fails loudly rather than silently. */
    @Deprecated
    public static final String SECTION_CURRENT = SECTION_TODAY;
    public static final String SECTION_FUTURE = "Future";
    public static final String OPEN_HISTORY = "Open history";
    /**
     * The per-visit review CTA on the history screen: "Rate your visit".
     *
     * <p>This is the ONLY route into the review flow from the appointments area. The review tests
     * used to open the first appointment's DETAIL page and expect the flow there, which is why
     * they skipped with "the review flow was not reachable": the detail has no review control, and
     * the first appointment is an upcoming one in any case. A past visit's card carries this
     * button, and it appears only while the visit is completed and not yet reviewed -- exactly the
     * state the flow requires.
     */
    public static final String RATE_VISIT = "Rate your visit";
    /**
     * The drill-down link on BOTH the Today and Future cards.
     *
     * <p>"Open current" no longer exists — that is what took the review tests down, waiting 30
     * seconds for a link the app had renamed. Note this label is <b>not unique</b>: Today and
     * Future both use it, so tapping it by itself opens whichever comes first. Use
     * {@link #openSectionNamed(String)} and name the card instead.
     */
    public static final String OPEN_SCHEDULE = "Open schedule";
    /**
     * A card's status chip, used as the card anchor.
     *
     * <p>The cards no longer carry "Appointment #&lt;id&gt;" — see {@link #openFirst()}. They read
     * {@code "Men's Haircut\n$72.00\n2:30 - 3:15 PM · 45 min\n· In 1 day\nIndividual\nConfirmed"},
     * and of that only the status and the price are stable across fixtures. ASCII, deliberately:
     * the card's own separators are middots, which a UiSelector cannot match.
     */
    public static final String CARD_STATUS_CONFIRMED = "Confirmed";
    /** The detail page's price label. The amount is a sibling node, not part of this one. */
    public static final String DETAIL_TOTAL_LABEL = "Total Price:";

    public ClientAppointmentsScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(TITLE), Duration.ofSeconds(25));
    }

    /** True when the hub rendered all three summary cards. */
    public boolean showsSummarySections() {
        return isPresentAfterScroll(SECTION_PAST)
                && isPresentAfterScroll(SECTION_TODAY)
                && isPresentAfterScroll(SECTION_FUTURE);
    }

    /** Opens one of the hub's drill-downs by its link text ("Open history" / "Open schedule"). */
    public ClientAppointmentsScreen openSection(String openLink) {
        LOG.info("Appointments: opening '{}'", openLink);
        scrollAndTap(openLink);
        return this;
    }

    /**
     * Opens a summary card by the card's own name — "Past", "Today" or "Future".
     *
     * <p>Prefer this to {@link #openSection(String)}. Each card merges its title, count and link
     * into one node ("Today\n3 bookings\nOpen schedule →"), and Today and Future share the same
     * link text, so addressing the link alone is ambiguous and silently opens the wrong one. The
     * card name is unique and is what a person would say they tapped.
     */
    public ClientAppointmentsScreen openSectionNamed(String section) {
        LOG.info("Appointments: opening the '{}' card", section);
        // The SUMMARY CARD, not the first node containing the word.
        //
        // "Future", "Past" and "Today" each appear more than once on this hub — the calendar
        // beneath the cards uses the same words — and a plain descContains takes whichever comes
        // first. Tapping that did nothing visible, so the schedule never opened and the caller
        // reported "no appointment card" about a screen it had never reached.
        //
        // The card is the only node that ALSO carries "Open schedule"/"Open history", which is
        // what makes it a card rather than a label.
        if (!isPresentAfterScroll(section)) {
            LOG.warn("Appointments: no '{}' card on the hub", section);
            return this;
        }
        for (WebElement candidate : findAll(descContains(section))) {
            String desc = candidate.getAttribute("content-desc");
            if (desc != null && (desc.contains(OPEN_SCHEDULE) || desc.contains(OPEN_HISTORY))) {
                candidate.click();
                return this;
            }
        }
        LOG.warn("Appointments: found '{}' but no node carrying '{}'/'{}' — tapping the first",
                section, OPEN_SCHEDULE, OPEN_HISTORY);
        scrollAndTap(section);
        return this;
    }

    /**
     * True if this client has any booking at all, read from the hub's own counts.
     * The cards render "<n> visits" / "<n> active" / "<n> booked", so a client with nothing shows
     * zeros rather than an empty-list message.
     */
    public boolean hasAnyAppointment() {
        // Future first: it is the section most likely to be non-empty and the one whose cards are
        // richest. Whichever answers is REMEMBERED, because re-deriving it later does not work —
        // see nonEmptySection().
        for (String section : new String[]{SECTION_FUTURE, SECTION_PAST, SECTION_TODAY}) {
            if (countIn(section) > 0) {
                nonEmptySection = section;
                return true;
            }
        }
        nonEmptySection = null;
        return false;
    }

    /**
     * The section {@link #hasAnyAppointment()} found entries in, or null.
     *
     * <p>Remembered rather than recomputed. Counting scrolls, and Flutter drops the semantics of
     * off-screen widgets, so by the time a caller acts on the answer the cards it was derived from
     * may no longer be in the tree — two consecutive reads of the same unchanged screen then
     * disagree, and the second one wins. Caching the first answer is the only version that is
     * self-consistent.
     */
    public String nonEmptySection() {
        return nonEmptySection;
    }

    private String nonEmptySection;

    /**
     * The number on a summary card — 6 from "Past\n6 visits\nOpen history →".
     *
     * <p>Reads the count rather than testing for the string "0 …". The old check looked for
     * "0 visits" / "0 active" / "0 booked" and OR-ed three negations together, so it answered true
     * whenever any ONE of them was absent — which is always, since the cards render different nouns
     * ("visits", "bookings", "booked") and "0 active" was never one of them. It therefore claimed
     * an appointment existed on an account with none, and the failure landed later and elsewhere.
     *
     * @return the count, or -1 when the card is not on screen
     */
    public int countIn(String section) {
        if (!isPresentAfterScroll(section)) {
            return -1;
        }
        // EVERY node containing the word, not just the first.
        //
        // The section words are short and appear more than once on this screen: "Today" labels the
        // summary card ("Today\n0 bookings\nOpen schedule →") AND the calendar's jump-to-today
        // button, which is a bare "Today" with no number in it. Taking the first match therefore
        // read the button, parsed no digits, and returned -1 — and because hasAnyAppointment()
        // ORs three of these together, an account with 28 past visits and 4 future bookings
        // reported itself as having none. Three tests skipped on that.
        for (WebElement candidate : findAll(descContains(section))) {
            String desc = candidate.getAttribute("content-desc");
            if (desc == null) {
                continue;
            }
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(\\d+)\\s+\\w+").matcher(desc);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return -1;
    }

    /**
     * Drills into today's bookings and opens the first appointment there.
     *
     * <p>Goes via the card name, not the link: Today and Future both read "Open schedule →", so
     * tapping the link text alone lands on whichever the tree yields first.
     */
    /**
     * Opens the schedule LIST for the section that has entries, without opening a detail.
     *
     * <p>Cancellation lives on the list card, not on the detail page: verified on-device, the
     * detail ends at Payment / Mileage Tracking and carries no cancel control at all, while every
     * card in the schedule has its own "Cancel appointment" and "Reschedule".
     */
    /** Opens the past-appointments history, where completed visits and their review CTAs live. */
    public ClientAppointmentsScreen openHistory() {
        LOG.info("Appointments: opening history");
        scrollAndTap(OPEN_HISTORY);
        return this;
    }

    /** True when the history is showing at least one visit that can still be reviewed. */
    public boolean hasRateableVisit() {
        return isPresentAfterScroll(RATE_VISIT);
    }

    /**
     * Starts a review for a past visit that had MORE THAN ONE service.
     *
     * <p>A multi-service card names its first service and then "+N more" --
     * "Men's Haircut + Beard +1 more | $72.00 | ... | Completed". Picking one matters because the
     * per-service gate needs two services to bite, and the newest completed visit is usually a
     * single-service one: the payment suite creates individual bookings which later complete, so
     * they arrive at the TOP of history and push the two-service visit down. Taking whichever is
     * first made the test skip claiming the appointment had one service, which was true of the
     * appointment it happened to open and not of the history.
     *
     * @return false when no multi-service reviewable visit is listed
     */
    public boolean rateFirstMultiServiceVisit() {
        // SCROLL while searching. Flutter drops the semantics of anything below the fold, so a
        // visit that is not currently rendered does not exist as far as findAll is concerned --
        // and the multi-service visit is rarely near the top. The payment suite books
        // single-service appointments which complete over the following hours and arrive ABOVE
        // it, so it sinks a little further down the history every time that suite runs. Without
        // this the search gave up in half a second having looked at one screenful.
        for (int screen = 0; screen < MAX_HISTORY_SCREENS; screen++) {
            if (tapRateOnMultiServiceCard()) {
                return true;
            }
            if (!scrollForwardOnce()) {
                break;
            }
        }
        LOG.warn("Appointments: no multi-service reviewable visit in {} screenful(s) of history",
                MAX_HISTORY_SCREENS);
        return false;
    }

    /** How far down the history to look for a multi-service visit. */
    private static final int MAX_HISTORY_SCREENS = 8;

    /**
     * Taps "Rate your visit" on a multi-service card VISIBLE RIGHT NOW.
     *
     * <p>A multi-service card names its first service and then "+N more" --
     * "Men's Haircut + Beard +1 more | $72.00 | ... | Completed".
     */
    private boolean tapRateOnMultiServiceCard() {
        for (org.openqa.selenium.WebElement card : findAll(descContains(" more"))) {
            String desc = card.getAttribute("content-desc");
            if (desc == null || !desc.contains("Completed")) {
                continue;
            }
            int cardY = card.getLocation().getY();
            org.openqa.selenium.WebElement cta = null;
            int best = Integer.MAX_VALUE;
            for (org.openqa.selenium.WebElement c : findAll(descContains(RATE_VISIT))) {
                int y = c.getLocation().getY();
                if (y > cardY && y < best) {
                    best = y;
                    cta = c;
                }
            }
            if (cta != null) {
                LOG.info("Appointments: reviewing a multi-service visit: {}",
                        desc.replace("\n", " | "));
                cta.click();
                return true;
            }
        }
        return false;
    }

    /** Scrolls the history one screen; false when it will not move any further. */
    private boolean scrollForwardOnce() {
        try {
            driver.findElement(io.appium.java_client.AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true)).scrollForward()"));
            sleepBriefly();
            return true;
        } catch (RuntimeException e) {
            LOG.debug("Appointments: history will not scroll further: {}", e.getMessage());
            return false;
        }
    }

    /** Starts the review flow for the first reviewable visit in the history. */
    public ClientAppointmentsScreen rateFirstVisit() {
        LOG.info("Appointments: starting a review from history");
        scrollAndTap(RATE_VISIT);
        return this;
    }

    public ClientAppointmentsScreen openSchedule() {
        return openSectionNamed(nonEmptySection != null ? nonEmptySection : SECTION_FUTURE);
    }

    public ClientAppointmentsScreen openFirst() {
        // Open the section hasAnyAppointment() FOUND, and tap a card anchor that still exists.
        //
        // Three separate things were wrong here. It always opened TODAY, which is empty on most
        // days — the seeded client has 28 past visits, 4 future bookings and nothing today. It
        // then tapped "Appointment #", which the list no longer renders: client_appointment.dart
        // deliberately dropped the internal id from the card ("the one thing on the card a client
        // can neither use nor care about") in favour of the service name; the id survives only as
        // the DETAIL page's appbar title, which is why detailIsLoaded() still matches on it. And
        // re-counting the sections here did not work either, because counting scrolls and Flutter
        // drops off-screen semantics — so this method and hasAnyAppointment() disagreed about the
        // same unchanged screen. Hence the remembered section.
        String section = nonEmptySection != null ? nonEmptySection : SECTION_FUTURE;
        LOG.info("Appointments: opening the '{}' section", section);
        openSectionNamed(section);

        if (isPresentAfterScroll(CARD_STATUS_CONFIRMED)) {
            scrollAndTap(CARD_STATUS_CONFIRMED);
            return this;
        }
        // A past visit is not "Confirmed" — fall back to the price, which every card carries.
        if (isPresentAfterScroll("$")) {
            scrollAndTap("$");
            return this;
        }
        throw new org.openqa.selenium.NoSuchElementException(
                "The '" + section + "' schedule rendered no appointment card. hasAnyAppointment() "
                        + "reported entries there, so either the schedule failed to load or the "
                        + "card layout has changed again — it is matched on '"
                        + CARD_STATUS_CONFIRMED + "' or a price.");
    }

    // ---- detail assertions --------------------------------------------------

    /** True when the detail screen shows the appointment number header. */
    public boolean detailIsLoaded() {
        return isPresent(descContains("Appointment #"), Duration.ofSeconds(20));
    }

    /** True when a total price is rendered on the detail. */
    public boolean detailShowsTotal() {
        // "Total Price:", not "Total:". Verified on-device: the detail page renders the label and
        // the amount as two separate nodes — content-desc="Total Price:" followed by
        // content-desc="$72.00" — so a locator for "Total:" matched neither.
        return isPresentAfterScroll(DETAIL_TOTAL_LABEL);
    }

    /** True when the appointment is part of a recurring series. */
    public boolean isRecurring() {
        return isPresentAfterScroll(RECURRING) || isPresentAfterScroll("Pattern:");
    }

    // ---- actions ------------------------------------------------------------

    /** Opens the cancel dialog. */
    /** Returns from an appointment's detail page to the schedule it was opened from. */
    public ClientAppointmentsScreen goBack() {
        back();
        return this;
    }

    public ClientAppointmentsScreen tapCancel() {
        // TWO dialogs stand between the card and the confirmation, and the first is new.
        //
        // Tapping a card's "Cancel appointment" no longer opens the cancellation confirmation.
        // It raises "Choose an action" — "Would you like to reschedule this appointment or cancel
        // it?" — offering "Update date / time" / "Cancel appointment" / "Dismiss". Only the second
        // of those reaches "Cancel for free?", which is the dialog that actually carries
        // "Keep appointment". Without this hop the test looked for Keep on a screen that was
        // still asking a different question.
        scrollAndTap(CANCEL_APPOINTMENT);
        if (isPresent(descContains(CHOOSE_ACTION), SHORT_TIMEOUT)) {
            LOG.info("Appointments: answering the '{}' dialog with '{}'",
                    CHOOSE_ACTION, CANCEL_APPOINTMENT);
            // Button-scoped: the dialog's own message contains "cancel it?", and the card
            // underneath still carries the same words, so a plain descContains can resolve to
            // either the prose or the row behind the scrim.
            tap(buttonDescContains(CANCEL_APPOINTMENT));
        }
        return this;
    }

    /** True when the schedule list still shows at least one appointment card. */
    public boolean hasCardsListed() {
        return isPresentAfterScroll(CARD_STATUS_CONFIRMED) || isPresentAfterScroll("$");
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
