package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Style-Me-Now, the on-demand ("a pro now") flow —
 * {@code screens/client/ClientRSNAppointmentBooking/style_me_now_flow_screen.dart} and its
 * {@code style_me_now_matching_screen.dart}.
 *
 * <p>Two steps only — "Who &amp; what" then "Where &amp; pay" — and the step-2 CTA doubles as the
 * price quote: it reads {@code "Find a pro · $x.xx"} and must agree with the TOTAL row. That
 * agreement is the single most valuable assertion on this screen, because it is where the client
 * commits money before any professional is known.
 *
 * <p>After dispatch the matching screen takes over: it either matches ("Your pro is on the way",
 * with a live "MIN AWAY" ETA) or expires ("Nobody's free right now" — note the apostrophe is a
 * plain ASCII {@code '} escaped in the Dart source).
 */
public class ClientStyleMeNowScreen extends MobileBasePage {

    public static final String STEP_WHO_WHAT = "Who & what";
    public static final String STEP_WHERE_PAY = "Where & pay";
    public static final int TOTAL_STEPS = 2;

    // ---- copy used as assertions -------------------------------------------
    /** Step 2's location eyebrow. */
    public static final String WHERE = "WHERE";
    /** What the WHERE row reads when the device supplied a GPS fix — then there is no text field. */
    public static final String CURRENT_LOCATION = "Current location";
    /** Step 2's payment eyebrow; the row beneath names the card, e.g. "VISA •••• 4242". */
    public static final String PAY_WITH = "PAY WITH";
    public static final String WHERE_PROMPT = "Where should we meet you?";
    public static final String NO_CARD = "No card on file";
    public static final String ADD_CARD_TO_BOOK = "Add one to book";
    public static final String CARD_DECLINED = "Your card was declined";
    public static final String PRO_ON_THE_WAY = "Your pro is on the way";
    public static final String NOBODY_FREE = "Nobody";          // "Nobody's free right now"
    public static final String CANCEL_CONFIRM = "Cancel this request?";
    public static final String LIVE_MAP_NOTE = "They can see this live map too.";

    // ---- step 1 copy --------------------------------------------------------
    /** The recipient picker's eyebrow — "Me / Self" plus any family members. */
    public static final String WHO_IS_THIS_FOR = "WHO'S THIS FOR";
    /** The Barber / Hair stylist segmented control. The service list is filtered by it. */
    public static final String SERVICE_TYPE = "SERVICE TYPE";
    public static final String TYPE_BARBER = "Barber";
    public static final String TYPE_STYLIST = "Hair stylist";
    /** Step 1's running total. Step 2 relabels it TOTAL. */
    public static final String SUBTOTAL = "SUBTOTAL";
    /** Step 1's CTA. Step 2's becomes "Find a pro · $x.xx". */
    public static final String CONTINUE = "Continue";

    // ---- locators -----------------------------------------------------------
    /**
     * The service search field — located by EditText index, NOT by its hint.
     *
     * <p>It is a bare {@code TextField} whose only text is {@code hintText: 'Search services…'},
     * and a Flutter hint does not become a content-desc: the node comes through as
     * {@code android.widget.EditText} with {@code NAF="true"} and an <b>empty</b> content-desc.
     * So {@code descContains("Search services…")} — which is what this used to be — could never
     * match, and the flow hung here waiting for a control that is on screen and simply has no
     * name. It is the only EditText on step 1, so index 0 is unambiguous.
     *
     * <p>The real fix is upstream: give the field a {@code Semantics(label:)} in
     * {@code style_me_now_flow_screen.dart}. Until then this is also a genuine accessibility
     * defect — a screen reader cannot announce the field either.
     */
    private final By serviceSearch = editText(0);
    private final By streetAddress = descContains("Street address");
    private final By cancelRequest = accId("Cancel request");
    private final By confirmCancel = accId("Cancel it");
    private final By keepLooking = accId("Keep looking");
    private final By tryAgain = accId("Try again");

    public ClientStyleMeNowScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains("Step "), Duration.ofSeconds(25));
    }

    /** True if the flow declares two total steps. */
    public boolean showsTwoSteps() {
        return isPresent(descContains(" of " + TOTAL_STEPS), Duration.ofSeconds(10));
    }

    public boolean showsTitle(String title) {
        return isPresent(descContains(title), Duration.ofSeconds(10));
    }

    // ---- step 1 -------------------------------------------------------------

    public ClientStyleMeNowScreen searchService(String query) {
        type(serviceSearch, query);
        return this;
    }

    public ClientStyleMeNowScreen selectService(String serviceName) {
        scrollAndTap(serviceName);
        return this;
    }

    /**
     * Picks who the visit is for ("Me", or a family member by name).
     *
     * <p>The cards merge label and relationship into one node
     * ({@code "M\nMe\nSelf"}, {@code "N\nNic Tompson\nSon"}), so match by substring.
     */
    public ClientStyleMeNowScreen chooseRecipient(String name) {
        LOG.info("StyleMeNow: choosing recipient '{}'", name);
        scrollAndTap(name);
        return this;
    }

    /**
     * Picks the Barber / Hair stylist segment, which <b>filters the service list</b>.
     *
     * <p>Worth doing explicitly rather than trusting the default: the same trap as the scheduled
     * booking flow, where the list is filtered by category and a barbering service looks missing
     * until the category is tapped. A test that searches for a service the current segment does
     * not offer gets an empty list and reads as "the service is gone".
     */
    public ClientStyleMeNowScreen chooseServiceType(String type) {
        LOG.info("StyleMeNow: choosing service type '{}'", type);
        scrollAndTap(type);
        return this;
    }

    /** True while step 1 is showing (the recipient picker is its landmark). */
    public boolean isOnStepOne() {
        return isPresentAfterScroll(WHO_IS_THIS_FOR);
    }

    /** Step 1's running subtotal; -1 if unreadable. */
    public double subtotal() {
        scrollToDesc(SUBTOTAL);
        return readFirstAmount();
    }

    /**
     * Advances step 1 → step 2 ("Continue").
     *
     * <p>A separate control from {@link #findAPro()}: the same button changes both label and
     * meaning between steps, so a test that only ever tapped "Find a pro" never left step 1 and
     * then could not read the TOTAL row or the quote — which is why the quote assertion used to
     * skip itself rather than run.
     */
    public ClientStyleMeNowScreen continueToPay() {
        LOG.info("StyleMeNow: advancing to step 2 (Where & pay)");
        scrollAndTap(CONTINUE);
        return this;
    }

    // ---- step 2 -------------------------------------------------------------

    public boolean showsWherePrompt() {
        return isPresentAfterScroll(WHERE_PROMPT);
    }

    /**
     * Enters a manual street address — <b>only if the screen is asking for one</b>.
     *
     * <p>Usually it is not. When the device has a location, step 2 renders WHERE as
     * {@code "Current location\nUsing your GPS location"} with a "Change" affordance and <b>no
     * text field at all</b>; the manual field only exists once you take that branch. A caller
     * that insisted on typing therefore waited out a 30s timeout on a screen that was complete and
     * correct, and the failure read as "the address field is gone".
     *
     * <p>So this is a no-op on the GPS path and returns quietly. Use {@link #usesCurrentLocation()}
     * when a test needs to assert which branch it is on rather than just proceed.
     *
     * @return this, whether or not anything was typed
     */
    public ClientStyleMeNowScreen enterAddress(String address) {
        if (!isPresent(streetAddress, SHORT_TIMEOUT)) {
            LOG.info("StyleMeNow: no manual address field — the flow is using the GPS location");
            return this;
        }
        type(streetAddress, address);
        hideKeyboard();
        return this;
    }

    /** True when step 2 is using the device's GPS fix rather than a typed address. */
    public boolean usesCurrentLocation() {
        return isPresentAfterScroll(CURRENT_LOCATION);
    }

    /** True when a card is on file and named on the pay step. */
    public boolean showsCardOnFile() {
        return isPresentAfterScroll(PAY_WITH) && !showsNoCardOnFile();
    }

    /** True when the client has no payment method and the CTA is therefore blocked. */
    public boolean showsNoCardOnFile() {
        return isPresentAfterScroll(NO_CARD);
    }

    /**
     * The amount on the TOTAL row; -1 if unreadable.
     *
     * <p><b>Matched as a STANDALONE amount, not as "the first $ near TOTAL".</b> Step 2 puts the
     * whole price breakdown in one merged node —
     * {@code "Services\n$60.00\nConvenience fee\n$12.00\nTotal\n$72.00\n…"} — because Flutter
     * merges the Semantics of the rows inside it. Taking the first {@code $} in the tree after
     * scrolling to TOTAL therefore returns the <em>services subtotal</em>, and the assertion that
     * the CTA quotes the total then fails with "quotes 72.0 but the TOTAL row says 60.0" — an
     * apparent money bug in an app that is displaying exactly the right numbers.
     *
     * <p>Same family as the booking-flow trap where a premium row shows its own formula
     * ({@code "(20 % × $85.00)\n$17.00"}) and reading the first amount yields the operand. The
     * defence there is to read from the end; here it is to match only a node that is
     * <em>nothing but</em> an amount, which the real TOTAL is and the breakdown block is not.
     */
    public double total() {
        scrollToDesc("TOTAL");
        return readStandaloneAmount();
    }

    /**
     * Reads a node whose entire content-desc is a currency amount (e.g. {@code "$72.00"}).
     *
     * @return the amount, or -1 when no such node is present
     */
    private double readStandaloneAmount() {
        // Every node carrying a "$", then the one that is ONLY an amount.
        //
        // Done by filtering rather than by a descriptionMatches selector: the regex has to survive
        // Java escaping, JSON encoding and UiAutomator's own parsing before it reaches a matcher,
        // and a '$' that loses one backslash on the way silently matches nothing — which is
        // indistinguishable from the amount being absent. Reading the descriptions and deciding
        // here costs one extra round trip and cannot be defeated by quoting.
        By anyAmount = descContains("$");
        if (!isPresent(anyAmount, Duration.ofSeconds(10))) {
            LOG.warn("StyleMeNow: no node containing an amount is on screen");
            return -1;
        }
        for (var element : findAll(anyAmount)) {
            String desc = element.getAttribute("content-desc");
            if (desc == null) {
                continue;
            }
            String trimmed = desc.trim();
            // The breakdown block merges every row into one node
            // ("Services\n$60.00\nConvenience fee\n$12.00\nTotal\n$72.00\n…"), so a newline is
            // the reliable sign that this is the explanation and not the figure.
            if (!trimmed.contains("\n") && trimmed.startsWith("$")) {
                return ClientBookingFlowScreen.parseAmount(trimmed);
            }
        }
        LOG.warn("StyleMeNow: every '$' node on screen was a merged block, none was a bare amount");
        return -1;
    }

    /**
     * The amount quoted on the dispatch CTA ("Find a pro · $x.xx"); -1 if unreadable.
     * Compare with {@link #total()} — they must match to the cent.
     */
    public double ctaQuote() {
        By cta = descContains("Find a pro");
        if (!isPresent(cta, Duration.ofSeconds(10))) {
            return -1;
        }
        return ClientBookingFlowScreen.parseAmount(descOf(cta));
    }

    private double readFirstAmount() {
        By amount = descContains("$");
        return isPresent(amount, Duration.ofSeconds(5))
                ? ClientBookingFlowScreen.parseAmount(descOf(amount)) : -1;
    }

    private String descOf(By by) {
        var element = find(by);
        if (element == null) {
            return "";
        }
        String desc = element.getAttribute("content-desc");
        return (desc == null || desc.isBlank()) ? element.getText() : desc;
    }

    /** Dispatches the request ("Find a pro · $x.xx"). */
    public ClientStyleMeNowScreen findAPro() {
        LOG.info("StyleMeNow: dispatching the request");
        scrollAndTap("Find a pro");
        return this;
    }

    // ---- matching -----------------------------------------------------------

    /** True once a professional accepted and is en route. */
    public boolean isMatched() {
        return isPresent(descContains(PRO_ON_THE_WAY), Duration.ofMinutes(2));
    }

    /** True when no professional was available before the request expired. */
    public boolean showsNobodyFree() {
        return isPresent(descContains(NOBODY_FREE), Duration.ofMinutes(2));
    }

    public boolean showsCardDeclined() {
        return isPresent(descContains(CARD_DECLINED), Duration.ofSeconds(30));
    }

    /** Cancels an in-flight request, confirming the dialog. */
    public ClientStyleMeNowScreen cancelRequest() {
        tap(cancelRequest);
        tap(confirmCancel);
        return this;
    }

    /** Opens the cancel dialog but backs out via "Keep looking". */
    public ClientStyleMeNowScreen cancelRequestThenKeepLooking() {
        tap(cancelRequest);
        tap(keepLooking);
        return this;
    }

    public boolean showsCancelConfirmation() {
        return isPresent(descContains(CANCEL_CONFIRM), Duration.ofSeconds(10));
    }

    public ClientStyleMeNowScreen retry() {
        tap(tryAgain);
        return this;
    }
}
