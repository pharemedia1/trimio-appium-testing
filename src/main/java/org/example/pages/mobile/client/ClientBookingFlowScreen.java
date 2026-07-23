package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The scheduled booking flow — {@code screens/client/ClientBookingAppointment/booking_flow_screen.dart}.
 *
 * <p>Four collection steps, then a hand-off to the app's existing review-and-cost dialog for the
 * money math and Stripe:
 * <ol>
 *   <li>"What do you need?" — service type (stylist/barber) + services (searchable)</li>
 *   <li>"Who & where" — person being served + the service address</li>
 *   <li>"When" — day picker then slot availability</li>
 *   <li>"Add extras" — add-ons, each showing "+$x.xx"</li>
 * </ol>
 *
 * <p>The same widget runs the <em>group</em> flow when constructed with {@code isGroup}, in which case
 * the titles become "Who’s coming · Services · Where · When" — see {@link #GROUP_TITLES} (note the
 * curly apostrophe U+2019, which is what the source actually contains).
 *
 * <p>The progress line "Step n of 4" is the most reliable landmark: it is a plain {@code Text}, always
 * on screen, and changes on every advance.
 */
public class ClientBookingFlowScreen extends MobileBasePage {

    // ---- step titles (solo flow) -------------------------------------------
    public static final String STEP_WHAT = "What do you need?";
    public static final String STEP_WHO_WHERE = "Who & where";
    public static final String STEP_WHEN = "When";
    public static final String STEP_EXTRAS = "Add extras";
    public static final String[] TITLES = {STEP_WHAT, STEP_WHO_WHERE, STEP_WHEN, STEP_EXTRAS};

    // ---- step titles (group flow) ------------------------------------------
    public static final String[] GROUP_TITLES = {"Who’s coming", "Services", "Where", "When"};

    public static final int TOTAL_STEPS = 4;

    // ---- copy used as assertions -------------------------------------------
    public static final String NO_SERVICE_MATCH = "No services match that search.";
    public static final String NO_OPEN_TIMES = "No open times on this day — try another day.";
    public static final String NO_ADDONS = "No add-ons available right now.";
    public static final String CHECKING_AVAILABILITY = "Checking availability…";
    public static final String FITTING_GROUP = "Fitting the group…";
    public static final String ADDRESS_HINT = "Where should the pro come? This updates your saved address.";
    public static final String SUBTOTAL = "SUBTOTAL";

    // ---- locators -----------------------------------------------------------
    private final By serviceSearch = descContains("Search services…");
    private final By addPerson = accId("Add person");
    private final By changeAddress = accId("Change");
    private final By saveAddress = accId("Save address");

    public ClientBookingFlowScreen(AndroidDriver driver) {
        super(driver);
    }

    /** True once the flow sheet is up (the step counter is the earliest stable landmark). */
    public boolean isLoaded() {
        return isPresent(descContains("Step "), Duration.ofSeconds(25));
    }

    /** The current step number parsed from "Step n of 4"; -1 if the counter isn't on screen. */
    public int currentStep() {
        for (int i = 1; i <= TOTAL_STEPS; i++) {
            if (isPresent(descContains("Step " + i + " of " + TOTAL_STEPS), Duration.ofSeconds(2))) {
                return i;
            }
        }
        return -1;
    }

    /** True if the step header shows the given title. */
    public boolean showsTitle(String title) {
        return isPresent(descContains(title), Duration.ofSeconds(10));
    }

    /** True if the flow reports {@code n} total steps (4 solo / 4 group, 2 for Style-Me-Now). */
    public boolean showsTotalSteps(int total) {
        return isPresent(descContains(" of " + total), Duration.ofSeconds(10));
    }

    // ---- step 1: what -------------------------------------------------------

    /** Types into the service search box and returns this screen. */
    public ClientBookingFlowScreen searchService(String query) {
        LOG.info("Booking: searching services for '{}'", query);
        type(serviceSearch, query);
        return this;
    }

    /** True when the search produced no matches. */
    public boolean showsNoServiceMatch() {
        return isPresent(descContains(NO_SERVICE_MATCH), Duration.ofSeconds(10));
    }

    /** Selects a service by its visible name. */
    public ClientBookingFlowScreen selectService(String serviceName) {
        scrollAndTap(serviceName);
        return this;
    }

    // ---- step 2: who & where ------------------------------------------------

    /** True if the service-address hint is displayed. */
    public boolean showsAddressHint() {
        return isPresentAfterScroll(ADDRESS_HINT);
    }

    /** Opens the address editor ("Change"). */
    public ClientBookingFlowScreen changeAddress() {
        tap(changeAddress);
        return this;
    }

    /** Fills the street/city fields of the address editor and saves. */
    public ClientBookingFlowScreen saveAddress(String street, String city) {
        type(editText(0), street);
        type(editText(1), city);
        hideKeyboard();
        tap(saveAddress);
        return this;
    }

    /** Group flow — adds another participant. */
    public ClientBookingFlowScreen addPerson() {
        tap(addPerson);
        return this;
    }

    // ---- step 3: when -------------------------------------------------------

    /** Selects a day of the month in the date strip. */
    public ClientBookingFlowScreen selectDay(int dayOfMonth) {
        LOG.info("Booking: selecting day {}", dayOfMonth);
        scrollAndTap(String.valueOf(dayOfMonth));
        return this;
    }

    /** Waits out the "Checking availability…" spinner; true once slots (or the empty state) settle. */
    public boolean waitForAvailability() {
        waitForAbsence(descContains(CHECKING_AVAILABILITY), Duration.ofSeconds(30));
        return !isPresent(descContains(CHECKING_AVAILABILITY), Duration.ofSeconds(2));
    }

    /** True when the selected day has no bookable slots. */
    public boolean showsNoOpenTimes() {
        return isPresent(descContains(NO_OPEN_TIMES), Duration.ofSeconds(20));
    }

    /** Selects a time slot by its rendered label (e.g. "10:30 AM"). */
    public ClientBookingFlowScreen selectSlot(String slotLabel) {
        scrollAndTap(slotLabel);
        return this;
    }

    // ---- step 4: extras -----------------------------------------------------

    /** True when the service has no add-ons to offer. */
    public boolean showsNoAddOns() {
        return isPresent(descContains(NO_ADDONS), Duration.ofSeconds(10));
    }

    /** Selects an add-on by name. */
    public ClientBookingFlowScreen selectAddOn(String addOnName) {
        scrollAndTap(addOnName);
        return this;
    }

    /**
     * The subtotal as a plain number, read from the "$x.xx" node next to SUBTOTAL.
     * Returns -1 when the amount can't be read (e.g. the row hasn't rendered yet).
     */
    public double subtotal() {
        scrollToDesc(SUBTOTAL);
        return readAmountNear(SUBTOTAL);
    }

    /** Reads the first "$…" amount that follows {@code anchor}; -1 when absent. */
    private double readAmountNear(String anchor) {
        By amount = descContains("$");
        if (!isPresent(amount, Duration.ofSeconds(5))) {
            return -1;
        }
        String raw = getText(amount);
        if (raw == null || raw.isBlank()) {
            raw = find(amount) == null ? "" : find(amount).getAttribute("content-desc");
        }
        return parseAmount(raw);
    }

    /** Parses "$1,234.56" (possibly with surrounding text) into 1234.56; -1 if nothing parses. */
    public static double parseAmount(String raw) {
        if (raw == null) {
            return -1;
        }
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)").matcher(raw);
        if (!m.find()) {
            return -1;
        }
        try {
            return Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---- flow control -------------------------------------------------------

    /** Taps the primary advance CTA ("Continue" / "Next"). */
    public ClientBookingFlowScreen continueStep() {
        hideKeyboard();
        if (isPresent(accId("Continue"), SHORT_TIMEOUT)) {
            tap(accId("Continue"));
        } else {
            tap(accId("Next"));
        }
        return this;
    }

    /** True if the flow refused to advance — i.e. we are still on {@code step}. */
    public boolean isBlockedOn(int step) {
        return currentStep() == step;
    }
}
