package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

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

    // ---- step 3: the professional chooser -----------------------------------
    /**
     * Heading of the shortlist that appears on step 3 <em>after</em> a time slot is picked.
     *
     * <p>Easy to miss, and expensive to miss. The flow lets you tap Continue straight past it, and
     * nothing on steps 3 or 4 complains; the booking then reaches the review dialog with an empty
     * professional, renders "Assigned Professional" with no name, and dies at the charge with a
     * 409 the client never sees (the failure snackbar is drawn behind the dialog). Verified
     * on-device — the whole flow completes and only the payment is refused.
     */
    public static final String CHOOSE_PROFESSIONAL = "Choose your professional";

    /** Slot periods on step 3. Each is a collapsed section showing "n times" or "No times". */
    public static final String MORNING = "Morning";
    public static final String AFTERNOON = "Afternoon";
    public static final String EVENING = "Evening";
    public static final String NO_TIMES = "No times";

    /** The step-4 CTA. It is NOT "Continue" — the last step hands off to the pay dialog. */
    public static final String REVIEW_AND_PAY = "Review & pay";

    /** Matches a rendered slot label such as "4:30 PM". */
    private static final java.util.regex.Pattern SLOT =
            java.util.regex.Pattern.compile("^\\d{1,2}:\\d{2}\\s?[AP]M$");

    /** Matches a day chip in the date strip, e.g. {@code "Sun\n30"}. */
    private static final java.util.regex.Pattern DAY_CHIP =
            java.util.regex.Pattern.compile("^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\n\\d{1,2}$");

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

    /**
     * True once the flow sheet is up (the step counter is the earliest stable landmark).
     *
     * <p>Answers the system location prompt first. The flow requests location as it opens, and
     * while that dialog is up the Flutter semantics tree is <b>empty</b> — so waiting for "Step "
     * without clearing it burns the whole timeout against a screen that is there but covered, and
     * reports "the booking flow should open" about a flow that opened fine.
     */
    public boolean isLoaded() {
        allowLocationIfAsked();
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

    /**
     * Picks a service category ("Barbering (beard & shave)", "Hair (cut, colour & styling)", …).
     *
     * <p>Step 1 is category-then-service: the service list is filtered by the selected category, so
     * a service is only tappable once its category is showing. The default category is Hair, which
     * is why a barbering service appears "missing" until this is called.
     */
    public ClientBookingFlowScreen selectCategory(String category) {
        LOG.info("Booking: category '{}'", category);
        scrollAndTap(category);
        sleepBriefly();
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

    /**
     * Step 2 — books the visit for the signed-in client ("Myself").
     *
     * <p>The card merges its label with the client's name ({@code "CC\nMyself\nCasey Client"}), so
     * it is matched on the "Myself" fragment rather than exactly.
     */
    public ClientBookingFlowScreen chooseMyself() {
        LOG.info("Booking: booking for myself");
        scrollAndTap("Myself");
        sleepBriefly();
        return this;
    }

    /** Step 2 — books the visit for a family member ("Someone else"). */
    public ClientBookingFlowScreen chooseSomeoneElse() {
        scrollAndTap("Someone else");
        sleepBriefly();
        return this;
    }

    // ---- step 3: when -------------------------------------------------------

    /** Selects a day of the month in the date strip. */
    public ClientBookingFlowScreen selectDay(int dayOfMonth) {
        LOG.info("Booking: selecting day {}", dayOfMonth);
        scrollAndTap(String.valueOf(dayOfMonth));
        return this;
    }

    /**
     * The day chips in the date strip, as their merged labels ({@code "Sun\n30"}).
     *
     * <p>Read in screen order, so index 0 is the earliest offered day — normally today.
     */
    public java.util.List<String> offeredDays() {
        java.util.List<String> days = new java.util.ArrayList<>();
        for (WebElement e : driver.findElements(descContains("\n"))) {
            String desc = e.getAttribute("content-desc");
            if (desc != null && DAY_CHIP.matcher(desc.trim()).matches()) {
                days.add(desc.trim());
            }
        }
        return days;
    }

    /**
     * Walks the date strip until a day yields bookable times, and opens the period holding them.
     *
     * <p>Availability is a moving target: a suite that only ever asks about <em>today</em> passes
     * in the morning and skips after the last slot has gone by, which looks like an environment
     * problem and is really a clock. Trying the following days makes the test say what it means —
     * "this professional has no availability at all" rather than "not right now".
     *
     * @param maxDays how many days to try before giving up
     * @return the slot labels of the first day that had any, empty if none did
     */
    public java.util.List<String> openFirstDayWithTimes(int maxDays) {
        java.util.List<String> days = offeredDays();
        int limit = Math.min(maxDays, Math.max(days.size(), 1));
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                String day = days.get(i);
                LOG.info("Booking: no times left, trying {}", day.replace("\n", " "));
                scrollAndTap(day.split("\n")[1]);   // tap the date number
                waitForAvailability();
            }
            java.util.List<String> slots = openFirstPeriodWithTimes();
            if (!slots.isEmpty()) {
                return slots;
            }
        }
        return java.util.List.of();
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

    /**
     * Expands a slot period ("Morning" / "Afternoon" / "Evening").
     *
     * <p>Slots are collapsed behind these headers, so no time is tappable — or even present in the
     * tree — until the period holding it is opened. The header merges with its own count
     * ({@code "Afternoon\n7 times"} / {@code "Morning\nNo times"}), so it is matched on the period
     * name and the count is read from the same node.
     */
    public ClientBookingFlowScreen expandPeriod(String period) {
        LOG.info("Booking: expanding '{}'", period);
        scrollAndTap(period);
        sleepBriefly();
        return this;
    }

    /** True when {@code period} advertises at least one bookable time. */
    public boolean periodHasTimes(String period) {
        String label = periodLabel(period);
        return label != null && !label.contains(NO_TIMES);
    }

    /** The merged period node, e.g. {@code "Afternoon\n7 times"}; null when absent. */
    private String periodLabel(String period) {
        if (!isPresentAfterScroll(period)) {
            return null;
        }
        WebElement node = find(descContains(period));
        return node == null ? null : node.getAttribute("content-desc");
    }

    /**
     * Opens the first period that has times and returns the slot labels it revealed.
     *
     * <p>Returns an empty list when the whole day is unbookable, which is a legitimate state and
     * the caller's cue to try another day rather than to fail.
     */
    public java.util.List<String> openFirstPeriodWithTimes() {
        for (String period : new String[]{MORNING, AFTERNOON, EVENING}) {
            if (periodHasTimes(period)) {
                expandPeriod(period);
                java.util.List<String> slots = awaitSlots(period);
                if (!slots.isEmpty()) {
                    return slots;
                }
                LOG.warn("'{}' advertised times but rendered no slots", period);
            }
        }
        return java.util.List.of();
    }

    /**
     * Waits for a period's slot chips to render and returns their labels.
     *
     * <p>Polls rather than sleeping a fixed interval. The chips are built after the period expands
     * and Flutter drops the Semantics of anything off-screen, so a single read taken immediately
     * after the tap can legitimately see nothing at all — which reads as "no availability" when the
     * day is in fact bookable. Scrolls the header back into view between attempts so a list that
     * grew past the fold still yields its times.
     */
    private java.util.List<String> awaitSlots(String period) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        java.util.List<String> slots = visibleSlots();
        while (slots.isEmpty() && System.currentTimeMillis() < deadline) {
            sleepBriefly();
            slots = visibleSlots();
            if (slots.isEmpty()) {
                scrollToDesc(period);   // keep the expanded period on screen
                slots = visibleSlots();
            }
        }
        LOG.info("Booking: {} slot(s) available: {}", slots.size(), slots);
        return slots;
    }

    /** Every slot label currently rendered, in screen order. */
    public java.util.List<String> visibleSlots() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (WebElement e : driver.findElements(descContains(":"))) {
            String desc = e.getAttribute("content-desc");
            if (desc != null && SLOT.matcher(desc.trim()).matches()) {
                out.add(desc.trim());
            }
        }
        return out;
    }

    // ---- step 3: choosing the professional ----------------------------------

    /**
     * Picks a slot that actually has somebody free, and returns that shortlist.
     *
     * <p>A time being offered is not a promise that a professional can take it: the strip lists
     * times with availability in general, and the shortlist is computed per slot, so an edge slot
     * can come back empty. Trying one slot and giving up therefore reports "no professional" about
     * a day with plenty — verified on-device, where 11:45 AM offered nobody while the same morning
     * had eleven other times.
     *
     * @param slots candidate labels, tried in order
     * @param maxAttempts how many to try before giving up
     * @return the chosen slot and the professionals it offered, or null if none did
     */
    public SlotOffer selectSlotWithProfessionals(java.util.List<String> slots, int maxAttempts) {
        int attempts = Math.min(maxAttempts, slots.size());
        for (int i = 0; i < attempts; i++) {
            String slot = slots.get(i);
            LOG.info("Booking: trying slot {}", slot);
            selectSlot(slot);
            if (showsProfessionalChooser()) {
                java.util.List<String> pros = offeredProfessionals();
                if (!pros.isEmpty()) {
                    return new SlotOffer(slot, pros);
                }
            }
            LOG.info("Booking: {} offered nobody, trying the next slot", slot);
        }
        return null;
    }

    /** A slot together with the professionals free to take it. */
    public static final class SlotOffer {
        private final String slot;
        private final java.util.List<String> professionals;

        SlotOffer(String slot, java.util.List<String> professionals) {
            this.slot = slot;
            this.professionals = professionals;
        }

        public String slot() {
            return slot;
        }

        public java.util.List<String> professionals() {
            return professionals;
        }
    }

    /**
     * True once the "Choose your professional" shortlist is showing.
     *
     * <p>It only appears after a slot is selected — the shortlist is the set of professionals free
     * at that time.
     */
    public boolean showsProfessionalChooser() {
        return isPresentAfterScroll(CHOOSE_PROFESSIONAL);
    }

    /**
     * Selects a professional from the shortlist by name.
     *
     * <p>The card merges name, rating, travel time and price into one node
     * ({@code "PP\nPat Pro\n★ 4.5 (3) · 0.0 min away\n$85.00"}), so it is matched on the name.
     */
    public ClientBookingFlowScreen chooseProfessional(String name) {
        LOG.info("Booking: choosing professional '{}'", name);
        scrollAndTap(name);
        sleepBriefly();
        return this;
    }

    /**
     * The names offered by the shortlist, read out of the merged cards.
     *
     * <p>A card's node is {@code "<initials>\n<name>\n★ …"}, so the name is its second line. Used
     * to pick a professional without hard-coding one, and to report who was on offer when a
     * booking cannot be completed.
     */
    public java.util.List<String> offeredProfessionals() {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
        java.util.List<String> names = readProfessionalCards();
        while (names.isEmpty() && System.currentTimeMillis() < deadline) {
            // THE SHORTLIST LIVES BELOW THE SLOT CHIPS, and Flutter drops the Semantics of
            // anything off-screen — so on a busy day it is simply not in the tree. Measured: a
            // morning with 12 slots pushed it out of view entirely and four consecutive times
            // each reported "no professional offered", while two swipes revealed seven pros and
            // a "More professionals (34)" row. Scroll first, then read.
            scrollToDesc(CHOOSE_PROFESSIONAL);
            names = readProfessionalCards();
            if (names.isEmpty()) {
                flingToEnd();
                names = readProfessionalCards();
            }
            if (names.isEmpty()) {
                sleepBriefly();
            }
        }
        LOG.info("Booking: {} professional(s) offered: {}", names.size(), names);
        return names;
    }

    /**
     * Reads the shortlist once.
     *
     * <p>Anchored on "min away" rather than on the card's ★ rating glyph. The selector is
     * interpolated into a {@code UiSelector} expression and shipped to the device as source, so a
     * non-ASCII anchor is at the mercy of the encoding on that hop — it matched nothing on-device
     * while the cards were plainly there, which surfaced as "the slot offered no professional".
     * The travel-time suffix is plain ASCII and equally reliable.
     */
    private java.util.List<String> readProfessionalCards() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (WebElement e : driver.findElements(descContains("min away"))) {
            String desc = e.getAttribute("content-desc");
            if (desc == null) {
                continue;
            }
            // "<initials>\n<name>\n★ 4.5 (3) · 0.0 min away\n$85.00" — the name is the second line.
            String[] lines = desc.split("\n");
            if (lines.length >= 2 && !lines[1].isBlank()) {
                names.add(lines[1].trim());
            }
        }
        return names;
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

    /**
     * Step 4 → the review-and-pay dialog.
     *
     * <p>The final CTA is "Review & pay", not "Continue" — {@link #continueStep()} will not find it.
     */
    public ClientBookingReviewScreen reviewAndPay() {
        LOG.info("Booking: opening review & pay");
        scrollAndTap(REVIEW_AND_PAY);
        return new ClientBookingReviewScreen(driver);
    }
}
