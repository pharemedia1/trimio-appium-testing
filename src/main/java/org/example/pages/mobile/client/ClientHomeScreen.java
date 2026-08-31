package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.example.pages.mobile.common.BottomNavBar;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The client Home + Book (discovery) tabs — {@code screens/client/home_screen.dart} and
 * {@code screens/client/activity/activityPage.dart}.
 *
 * <p>Home is the app's largest screen (~5.7k lines) and hosts the booking entry points; the Book tab
 * is pure discovery and explicitly hands off to Home ("Open the Home tab to start a booking."), which
 * the shell implements through {@code BottomnavigationBar.switchTab}.
 *
 * <p>Most of this screen is below the fold, so anything past the search box has to be scrolled into
 * view before it exists in the accessibility tree — hence the {@code scrollToDesc} calls.
 */
public class ClientHomeScreen extends MobileBasePage {

    // ---- Home ---------------------------------------------------------------
    /**
     * The Home feed's first stable landmark.
     *
     * <p>NOT the search box. This used to key on the hint "Search services, pros, or styles",
     * which is COMMENTED OUT in {@code home_screen.dart} — so {@link #isLoaded()} spent 25
     * seconds waiting for a control the app no longer builds, and every test downstream of Home
     * failed with "The Home tab should render" against a Home tab that was rendering perfectly.
     * Verified on-device: the feed shows the greeting, this heading, the booking cards and
     * Style Me Now.
     */
    private final By homeHeading = descContains("Book trusted professionals");
    private final By rebookButton = accId("Rebook");

    // ---- Book (discovery) ---------------------------------------------------
    private final By bookSearchBox = descContains("Search haircut, color, beard");
    private final By browseAllServices = accId("Browse all services");
    private final By bookCta = descContains("Book");

    // ---- booking entry points (Home feed) -----------------------------------
    /** The individual (one-to-one) booking CTA. Standalone and exact on-device. */
    public static final String BOOK_INDIVIDUAL = "Book Individual";
    /** The group/events booking CTA, which drives the same widget in group mode. */
    public static final String PLAN_GROUP_BOOKING = "Plan Group Booking";

    /** The rebook prompt raised between Home and the flow once the client has booking history. */
    public static final String REBOOK_PROMPT = "Book your professional?";
    /** Its "take me through the normal shortlist" option. */
    public static final String REBOOK_NEW_PRO = "New professional";
    /** Its "same professional as last time" option — the label carries the pro's name. */
    public static final String REBOOK_SAME_PRO = "Book ";

    // ---- copy used as assertions -------------------------------------------
    public static final String DISCOVERY_TAGLINE =
            "A licensed pro comes to you — pick a service to get started.";
    public static final String HANDOFF_HINT = "Open the Home tab to start a booking.";
    public static final String MIN_REVIEWS_NOTICE = "Minimum 3 reviews required to calculate rating.";
    public static final String REBOOK_FAILED = "Could not start rebooking. Please try again.";

    public ClientHomeScreen(AndroidDriver driver) {
        super(driver);
    }

    /**
     * Heading of the profile-completion gate that replaces the Home feed until a client has filled
     * in their details. Verified on-device.
     */
    public static final String PROFILE_GATE = "Your details";

    public BottomNavBar nav() {
        return new BottomNavBar(driver);
    }

    /** True once the Home tab has painted (its search box is the first stable landmark). */
    public boolean isLoaded() {
        return isPresent(homeHeading, Duration.ofSeconds(25));
    }

    /**
     * True when the app is holding the client on the "Your details" profile-completion screen
     * instead of the Home feed.
     *
     * <p>This is a real product gate, not a rendering delay: a client whose profile is incomplete
     * cannot reach the home feed, the booking flow or Style-Me-Now at all — the professional needs
     * an address to travel to. Tests that require those surfaces should therefore <b>skip</b> when
     * this is showing, because the environment is missing a provisioned client, not because a
     * locator is wrong. Distinguishing the two is the whole point: without this check, an
     * unprovisioned account produces a dozen identical "should render" failures that look like
     * broken selectors.
     */
    public boolean isBlockedByProfileGate() {
        return isPresent(descContains(PROFILE_GATE), Duration.ofSeconds(10));
    }

    /** True once the Book/discovery tab has painted. */
    public boolean isDiscoveryLoaded() {
        return isPresent(bookSearchBox, Duration.ofSeconds(25))
                || isPresentAfterScroll(DISCOVERY_TAGLINE);
    }

    // ---- search -------------------------------------------------------------

    /** Types into the Home search box (services, pros or styles). */
    public ClientHomeScreen searchFromHome(String query) {
        LOG.info("ClientHome: searching '{}'", query);
        type(editText(0), query);
        return this;
    }

    /** Types into the Book-tab service search box. */
    public ClientHomeScreen searchServices(String query) {
        type(editText(0), query);
        return this;
    }

    /** True if a result whose label contains {@code text} came back. */
    public boolean hasResult(String text) {
        return isPresentAfterScroll(text);
    }

    // ---- navigation ---------------------------------------------------------

    /** Book tab → "Browse all services". */
    public ClientHomeScreen browseAllServices() {
        scrollAndTap("Browse all services");
        return this;
    }

    /** True when the discovery tab is telling the user to start from Home. */
    public boolean showsHandoffHint() {
        return isPresentAfterScroll(HANDOFF_HINT);
    }

    /**
     * Opens the <b>individual</b> (one-to-one) booking flow from the Home feed.
     *
     * <p>Prefer this over {@link #startBooking()}. Home offers two entry points — "Book Individual"
     * and "Plan Group Booking" — and they build the same widget in different modes, so which one
     * you tap decides the titles, the steps and the payload. Verified on-device: the label is
     * standalone and exact, so it is matched by accessibility id rather than {@code descContains},
     * which would also hit the "Book" nav tab and the "Book a one-on-one session…" subtitle.
     *
     * <p>The flow asks for location on first open; {@link #allowLocationIfAsked()} answers that
     * prompt, without which the next locator waits out its timeout against an empty tree.
     */
    public ClientBookingFlowScreen bookIndividual() {
        return bookIndividual(false);
    }

    /**
     * Opens the individual booking flow, answering the rebook prompt if it appears.
     *
     * @param withPreviousProfessional true to accept "Book &lt;pro&gt;", false to take
     *     "New professional" and go through the normal shortlist
     */
    public ClientBookingFlowScreen bookIndividual(boolean withPreviousProfessional) {
        LOG.info("ClientHome: starting an INDIVIDUAL booking");
        scrollAndTap(BOOK_INDIVIDUAL);
        answerRebookPromptIfPresent(withPreviousProfessional);
        allowLocationIfAsked();
        return new ClientBookingFlowScreen(driver);
    }

    /**
     * Answers the "Book your professional?" prompt that stands between Home and the booking flow.
     *
     * <p>It only appears once the client has booked someone before, so it is invisible on a fresh
     * fixture and then intercepts every booking afterwards — the flow never opens, and the failure
     * reads as "the booking flow should open" with nothing to suggest a dialog is why. Same shape
     * as the post-login modal queue in {@code LoginScreen}: the app is fine, a question is
     * unanswered.
     *
     * <p>Defaults to "New professional" so the flow presents its usual shortlist rather than
     * silently preselecting a professional the test never chose.
     *
     * @return true if a prompt was present and answered
     */
    public boolean answerRebookPromptIfPresent(boolean withPreviousProfessional) {
        if (!isPresent(descContains(REBOOK_PROMPT), Duration.ofSeconds(12))) {
            return false;
        }
        // Button-scoped: the prompt's own message contains "New professional", and matching that
        // paragraph instead of the button taps a block of text and leaves the dialog up.
        By choice = withPreviousProfessional
                ? buttonDescContains(REBOOK_SAME_PRO)
                : buttonDescContains(REBOOK_NEW_PRO);
        LOG.info("ClientHome: answering the rebook prompt with '{}'",
                withPreviousProfessional ? REBOOK_SAME_PRO : REBOOK_NEW_PRO);
        tap(choice);
        sleepBriefly();
        return true;
    }

    /** Opens the group booking flow ("Plan Group Booking"). */
    public ClientBookingFlowScreen planGroupBooking() {
        LOG.info("ClientHome: starting a GROUP booking");
        scrollAndTap(PLAN_GROUP_BOOKING);
        allowLocationIfAsked();
        return new ClientBookingFlowScreen(driver);
    }

    /**
     * Taps a "Book" CTA on the discovery tab. The Book tab hosts no booking flow of its own — the
     * shell switches to Home, which owns it — so callers should assert on the Home landmark next.
     *
     * @deprecated for the Home feed, use {@link #bookIndividual()} — "Book" is ambiguous there.
     */
    @Deprecated
    public ClientBookingFlowScreen startBooking() {
        LOG.info("ClientHome: starting a booking");
        scrollAndTap("Book");
        allowLocationIfAsked();
        return new ClientBookingFlowScreen(driver);
    }

    /** Taps "Rebook" on a previous appointment card. */
    public ClientBookingFlowScreen rebook() {
        scrollAndTap("Rebook");
        return new ClientBookingFlowScreen(driver);
    }

    /** True if the "Book your professional?" rebook prompt is showing. */
    public boolean showsRebookPrompt() {
        return isPresent(descContains("Book your professional?"), Duration.ofSeconds(10));
    }

    /** True if a professional card shows the "fewer than 3 reviews" notice instead of a rating. */
    public boolean showsMinimumReviewsNotice() {
        return isPresentAfterScroll(MIN_REVIEWS_NOTICE);
    }
}
