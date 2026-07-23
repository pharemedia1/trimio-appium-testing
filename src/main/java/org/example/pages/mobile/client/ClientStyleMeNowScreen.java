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
    public static final String WHERE_PROMPT = "Where should we meet you?";
    public static final String NO_CARD = "No card on file";
    public static final String ADD_CARD_TO_BOOK = "Add one to book";
    public static final String CARD_DECLINED = "Your card was declined";
    public static final String PRO_ON_THE_WAY = "Your pro is on the way";
    public static final String NOBODY_FREE = "Nobody";          // "Nobody's free right now"
    public static final String CANCEL_CONFIRM = "Cancel this request?";
    public static final String LIVE_MAP_NOTE = "They can see this live map too.";

    // ---- locators -----------------------------------------------------------
    private final By serviceSearch = descContains("Search services…");
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

    // ---- step 2 -------------------------------------------------------------

    public boolean showsWherePrompt() {
        return isPresentAfterScroll(WHERE_PROMPT);
    }

    /** Enters a manual street address instead of using the GPS location. */
    public ClientStyleMeNowScreen enterAddress(String address) {
        type(streetAddress, address);
        hideKeyboard();
        return this;
    }

    /** True when the client has no payment method and the CTA is therefore blocked. */
    public boolean showsNoCardOnFile() {
        return isPresentAfterScroll(NO_CARD);
    }

    /** The total shown on the TOTAL row; -1 if unreadable. */
    public double total() {
        scrollToDesc("TOTAL");
        return readFirstAmount();
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
