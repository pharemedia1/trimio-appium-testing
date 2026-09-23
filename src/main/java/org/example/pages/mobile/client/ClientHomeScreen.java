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
    /**
     * The Book tab's search field — by EditText index, NOT by its hint.
     *
     * <p>{@code hintText: 'Search haircut, color, beard…'} is a hint, and a Flutter hint never
     * becomes a content-desc: the node arrives as a bare {@code EditText} with an empty one. The
     * old selector could therefore never match, on a tab that was rendering perfectly.
     */
    private final By bookSearchBox = editText(0);
    private final By browseAllServices = accId("Browse all services");
    private final By bookCta = descContains("Book");

    // ---- booking entry points (Home feed) -----------------------------------
    /** The individual (one-to-one) booking CTA. Standalone and exact on-device. */
    public static final String BOOK_INDIVIDUAL = "Book Individual";
    /** The group/events booking CTA, which drives the same widget in group mode. */
    public static final String PLAN_GROUP_BOOKING = "Plan Group Booking";
    /**
     * The on-demand CTA. A third, separate entry point — NOT a mode of the booking flow above.
     * It opens {@code StyleMeNowFlowScreen}, which no other button on this screen reaches.
     */
    public static final String STYLE_ME_NOW = "Style Me Now";
    /** What the app says when on-demand is refused because one is already running. */
    public static final String ON_DEMAND_IN_PROGRESS =
            "You cannot go online with an OnDemand appointment in progress.";

    // ---- the venue chooser (shop-visit model) -------------------------------
    /**
     * The sheet that now stands between "Book Individual" and the booking flow.
     *
     * <p><b>A new, MANDATORY step with no automation coverage until now.</b> Tapping the booking
     * CTA no longer opens the flow: it raises "Where should your appointment be?" and waits for
     * the client to choose between a professional who travels to them ({@code 'mobile'}) and a
     * chair at a licensed shop ({@code 'shop'}) — the shop-visit booking model. The source calls
     * it "deliberately the very first question" because it decides what the client is buying.
     *
     * <p>Every booking test therefore stopped at a sheet the page object did not know existed:
     * {@code flow.isLoaded()} looked for "Step " on a screen showing two venue cards, and the
     * whole booking suite skipped itself reporting "the booking flow did not open — the entry
     * point may require a saved address or an active service catalogue". Neither was ever true.
     */
    public static final String VENUE_PROMPT = "Where should your appointment be?";
    /** The 'mobile' venue — a professional travels to the client. The default for these tests. */
    public static final String VENUE_COME_TO_ME = "Come to me";
    /** The 'shop' venue — the client books a chair at a licensed shop. ASCII-safe anchor: the
     *  label is "I'll go to a shop" and carries a curly apostrophe. */
    public static final String VENUE_GO_TO_SHOP = "go to a shop";

    /**
     * The rebook prompt raised between the venue sheet and the flow, once the client has history.
     *
     * <p><b>Rewritten in the app, so all three of these constants moved.</b> It used to be titled
     * "Book your professional?" with a "New professional" option; it now reads
     * "Book &lt;Name&gt; again?" (or "Book your last professional?" when the name is unknown) and
     * offers "Book &lt;Name&gt;" / "Choose someone new" — see {@code _askReturning} in
     * {@code home_screen.dart}.
     *
     * <p>Detected by the CAPTION rather than the title, because the title carries the
     * professional's name and therefore changes with the fixture; "Your last professional" is
     * constant and name-independent.
     */
    public static final String REBOOK_PROMPT = "Your last professional";
    /** Its "take me through the normal shortlist" option. Was "New professional". */
    public static final String REBOOK_NEW_PRO = "Choose someone new";
    /** Its "same professional as last time" option — the label carries the pro's name. */
    public static final String REBOOK_SAME_PRO = "Book ";

    // ---- copy used as assertions -------------------------------------------
    /**
     * The discovery hero's tagline.
     *
     * <p><b>Kept for reference and NOT used as a locator.</b> It contains an em-dash (U+2014), and
     * a non-ASCII anchor in a {@code UiSelector} matches nothing — the same trap that made
     * {@code descContains("★")} find no professional cards that were plainly on screen. Combined
     * with the hint-based search box above, it meant {@code isDiscoveryLoaded()} was false on a
     * tab that renders correctly, and three Book-tab tests failed pointing at the app.
     *
     * <p>Use {@link #DISCOVERY_HEADING} or {@link #DISCOVERY_CATEGORIES} to detect the tab.
     */
    public static final String DISCOVERY_TAGLINE =
            "A licensed pro comes to you — pick a service to get started.";
    /** ASCII landmark for the discovery hero. Merges with the tagline into one node. */
    public static final String DISCOVERY_HEADING = "Book your next appointment";
    /** ASCII landmark further down the discovery tab. */
    public static final String DISCOVERY_CATEGORIES = "Popular categories";
    /** Every discovery category card reads "<Service>\nfrom $<price>\nBook". */
    public static final String CATEGORY_PRICE_HINT = "from $";
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
        // Three ASCII landmarks, any one of which is enough. Deliberately not the tagline: see
        // DISCOVERY_TAGLINE for why a non-ASCII anchor silently matches nothing.
        return isPresent(bookSearchBox, Duration.ofSeconds(25))
                || isPresentAfterScroll(DISCOVERY_HEADING)
                || isPresentAfterScroll(DISCOVERY_CATEGORIES);
    }

    // ---- search -------------------------------------------------------------

    /**
     * Types into the Home search box.
     *
     * @deprecated <b>Home has no search box.</b> The {@code TextField} with the hint
     *     "Search services, pros, or styles" is commented out in {@code home_screen.dart}, just
     *     below the "Book trusted professionals—anytime" line — the feed offers the booking cards
     *     and Style Me Now instead. Calling this waits out a 30-second timeout on an
     *     {@code EditText} the app never builds, which reads as a broken selector rather than as a
     *     feature that moved. Use {@link #searchServices(String)} on the Book tab.
     */
    @Deprecated
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
        return bookIndividual(withPreviousProfessional, true);
    }

    /**
     * Opens the individual booking flow, choosing a venue and answering the rebook prompt.
     *
     * @param withPreviousProfessional true to accept "Book &lt;pro&gt;" on the rebook prompt
     * @param comeToMe                 true for the mobile venue (a pro travels to the client),
     *                                 false to book a chair at a shop — a different product, a
     *                                 different price and a different set of steps, so it is the
     *                                 caller's choice rather than a default buried here
     */
    public ClientBookingFlowScreen bookIndividual(boolean withPreviousProfessional,
                                                  boolean comeToMe) {
        LOG.info("ClientHome: starting an INDIVIDUAL booking ({})",
                comeToMe ? VENUE_COME_TO_ME : VENUE_GO_TO_SHOP);
        scrollAndTapExact(BOOK_INDIVIDUAL);
        chooseVenueIfAsked(comeToMe);
        answerRebookPromptIfPresent(withPreviousProfessional);
        allowLocationIfAsked();
        return new ClientBookingFlowScreen(driver);
    }

    /**
     * Answers the venue sheet if it is up.
     *
     * <p>Conditional, not unconditional: the sheet is skipped when only one venue is open in the
     * client's state (the app asks {@code _venueOpenOrExplain} first), so a test that insisted on
     * it would fail in exactly the jurisdictions where the question does not arise.
     *
     * @return true if the sheet was present and answered
     */
    public boolean chooseVenueIfAsked(boolean comeToMe) {
        if (!isPresent(descContains(VENUE_PROMPT), Duration.ofSeconds(12))) {
            return false;
        }
        String choice = comeToMe ? VENUE_COME_TO_ME : VENUE_GO_TO_SHOP;
        LOG.info("ClientHome: venue sheet is up, choosing '{}'", choice);
        scrollAndTap(choice);
        sleepBriefly();
        return true;
    }

    /** True while the venue sheet is asking where the appointment should be. */
    public boolean showsVenuePrompt() {
        return isPresent(descContains(VENUE_PROMPT), Duration.ofSeconds(10));
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

    /**
     * Opens Style-Me-Now, the on-demand flow, from its own Home CTA.
     *
     * <p><b>This is not the generic booking button.</b> The Home feed carries three separate
     * entries — {@code Book Individual}, {@code Plan Group Booking} and {@code Style Me Now} — and
     * only this one reaches {@code StyleMeNowFlowScreen}. The on-demand tests used to arrive here
     * through the deprecated {@link #startBooking()}, which taps a bare "Book"; that lands in the
     * scheduled flow (or on the nav tab), so the two-step assertion failed and the test skipped
     * itself with "the flow was not reached", which reads as an environment problem and is not one.
     *
     * <p>The button is gated before it navigates: {@code home_screen.dart} refuses to open the flow
     * when an on-demand appointment is already in progress, and it needs a location — GPS if it is
     * granted, otherwise a manual-address dialog. {@link #allowLocationIfAsked()} answers the
     * system prompt; the in-app dialog is the caller's to handle, because typing an address is a
     * different test from dispatching a request.
     */
    public ClientStyleMeNowScreen styleMeNow() {
        LOG.info("ClientHome: opening Style Me Now (on-demand)");
        allowLocationIfAsked();
        scrollAndTapExact(STYLE_ME_NOW);
        allowLocationIfAsked();
        return new ClientStyleMeNowScreen(driver);
    }

    /** True while the app is refusing on-demand because one is already running. */
    public boolean showsOnDemandInProgress() {
        return isPresent(descContains(ON_DEMAND_IN_PROGRESS), Duration.ofSeconds(8));
    }

    /**
     * Books from a <b>category card on the Book (discovery) tab</b> — the tab's own way into the
     * booking flow.
     *
     * <p>Not {@link #startBooking()}, which taps a bare "Book" and on this tab matches the hero's
     * merged node first ({@code "IN-HOME\nBook your next appointment\n…\nBrowse all services"}),
     * hitting the hero container rather than a booking control. The category cards are
     * unambiguous: each reads {@code "<Service>\nfrom $<price>\nBook"}, so the price prefix
     * identifies one without hard-coding a service the catalogue may not carry.
     *
     * <p>What happens next is worth knowing, because it is not a plain tab switch:
     * {@code activityPage._openBooking} calls {@code BottomnavigationBar.switchTab(0)} and then,
     * a frame later, {@code HomePage.startIndividualBooking} with the tapped category
     * pre-selected. The end state is the booking flow, hosted on the Home tab.
     */
    /**
     * Books from a category card on the Book tab.
     *
     * <p><b>Tapped low and left, not in the centre, and the reason is in the card's layout.</b>
     * The whole card is one {@code GestureDetector} — "Book" is a Text inside it, not a control of
     * its own, so there is nothing separate to click and the merged node IS the target. But the
     * card's {@code Stack} holds a {@code Positioned(right: -24, top: -24)} 96px circle that
     * overflows the card, and the merged semantics rect grows to contain it. The rect's centre is
     * therefore ABOVE and RIGHT of the real hit area, which is why a plain tap landed on nothing
     * and the test concluded the hand-off was not automatable.
     *
     * <p>(0.25, 0.80) is inside the padded container on every category card — near the "Book"
     * pill at the bottom-left, and far from the overflowing decoration.
     */
    public ClientBookingFlowScreen bookFromDiscoveryCategory() {
        LOG.info("ClientHome: booking from a Book-tab category card");
        scrollToDesc(CATEGORY_PRICE_HINT);
        tapWithin(descContains(CATEGORY_PRICE_HINT), 0.25, 0.80);
        // The card DOES navigate -- it raises the venue sheet, "Where should your appointment
        // be?", exactly as Home's own booking entry point does. Leaving it unanswered parks the
        // flow behind a modal that is neither the booking flow nor the Home feed, which is what
        // made this look like a tap that had done nothing at all.
        chooseVenueIfAsked(true);
        // The SAME modal chain as Home's own entry point, because this lands in the same place:
        // activityPage._openBooking switches to the Home tab and starts the individual flow there.
        // So the rebook prompt ("Book your professional?") stands between the venue sheet and the
        // flow here too. Leaving it unanswered left the test on a modal that is neither the flow
        // nor the Home feed, which read as "the tap produced no navigation".
        answerRebookPromptIfPresent(false);
        allowLocationIfAsked();
        return new ClientBookingFlowScreen(driver);
    }

    /** Opens the group booking flow ("Plan Group Booking"). */
    public ClientBookingFlowScreen planGroupBooking() {
        LOG.info("ClientHome: starting a GROUP booking");
        scrollAndTapExact(PLAN_GROUP_BOOKING);
        // A group booking is ALWAYS a home visit (home_screen.dart calls _venueOpenOrExplain
        // with 'mobile' directly), so no venue sheet is expected — but answering one if it
        // appears costs nothing and survives that decision changing.
        chooseVenueIfAsked(true);
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
