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
    private final By homeSearchBox = descContains("Search services, pros, or styles");
    private final By rebookButton = accId("Rebook");

    // ---- Book (discovery) ---------------------------------------------------
    private final By bookSearchBox = descContains("Search haircut, color, beard");
    private final By browseAllServices = accId("Browse all services");
    private final By bookCta = descContains("Book");

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
        return isPresent(homeSearchBox, Duration.ofSeconds(25));
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
     * Taps a "Book" CTA on the discovery tab. The Book tab hosts no booking flow of its own — the
     * shell switches to Home, which owns it — so callers should assert on the Home landmark next.
     */
    public ClientBookingFlowScreen startBooking() {
        LOG.info("ClientHome: starting a booking");
        scrollAndTap("Book");
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
