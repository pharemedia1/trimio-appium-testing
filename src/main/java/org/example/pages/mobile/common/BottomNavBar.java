package org.example.pages.mobile.common;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import java.time.Duration;

/**
 * The GNav bottom navigation shared by the three signed-in shells.
 *
 * <p>Every shell uses {@code google_nav_bar}'s {@code GButton}, whose {@code text} renders as a
 * plain {@code Text} widget — so each tab is addressable by its label as a content-desc:
 * <ul>
 *   <li>client (<em>bottomNavigationBar.dart</em>) — Home · Book · Appointments · Shop · Profile</li>
 *   <li>professional (<em>professional_bottom_navigation_bar.dart</em>) — DashBoard · My Bookings ·
 *       Client Hub · Store · Account</li>
 *   <li>admin (<em>Admin/NavigationBar/admin_bottom_navigation_bar.dart</em>) — Dashboard · Pros ·
 *       Quality · Reports</li>
 * </ul>
 *
 * <p><b>The selected tab's label is duplicated.</b> Verified on-device: an unselected tab exports
 * {@code content-desc="My Bookings"}, but the selected one exports
 * {@code content-desc="My Bookings\nMy Bookings"} — GNav renders the active tab as icon + label in a
 * Row while the label also remains as its own semantics node, and the two merge. An exact
 * {@code accessibilityId} therefore matches every tab <em>except the one you just opened</em>, which
 * is the opposite of what a test wants. Hence {@link #tab(String)} matches on <em>contains</em>.
 * Never assert on position either: the selected tab lays out horizontally and the rest vertically.
 */
public class BottomNavBar extends MobileBasePage {

    // ---- client tabs --------------------------------------------------------
    public static final String CLIENT_HOME = "Home";
    public static final String CLIENT_BOOK = "Book";
    public static final String CLIENT_APPOINTMENTS = "Appointments";
    public static final String CLIENT_SHOP = "Shop";
    public static final String CLIENT_PROFILE = "Profile";

    // ---- professional tabs --------------------------------------------------
    public static final String PRO_DASHBOARD = "DashBoard";
    public static final String PRO_BOOKINGS = "My Bookings";
    public static final String PRO_CLIENT_HUB = "Client Hub";
    public static final String PRO_STORE = "Store";
    public static final String PRO_ACCOUNT = "Account";

    // ---- admin tabs ---------------------------------------------------------
    public static final String ADMIN_DASHBOARD = "Dashboard";
    public static final String ADMIN_PROS = "Pros";
    public static final String ADMIN_QUALITY = "Quality";
    public static final String ADMIN_REPORTS = "Reports";

    public BottomNavBar(AndroidDriver driver) {
        super(driver);
    }

    /**
     * The nav tab whose label is exactly {@code label} — unselected or selected.
     *
     * <p><b>Not a contains-match, and this is the correction.</b> The class javadoc above is right
     * that an exact {@code accessibilityId} misses the SELECTED tab (it exports its label twice),
     * but the fix it reached for — {@code descContains(label)} — is unscoped, and the labels are
     * short, common English words. On the client Home feed "Book" also appears in
     * {@code "Book trusted professionals—anytime"}, {@code "Book Individual"},
     * {@code "Plan Group Booking"}, {@code "Book again"} and {@code "Book a service"}, and the
     * first match wins. So {@code nav().open(CLIENT_BOOK)} from Home did not open the Book tab at
     * all — it tapped the greeting or a booking CTA.
     *
     * <p>The damage was invisible because it fails LATER: the next assertion is about the Book
     * tab's content, so three tests reported "The Book tab should render — expected true, found
     * false" against a tab that renders perfectly and had simply never been opened. Chasing that
     * cost a manual dump of the tab to discover it was fine.
     *
     * <p>So: match the description EXACTLY, accepting either form. That is unambiguous whichever
     * tab is selected, and it cannot collide with body copy.
     */
    /**
     * Finds the bottom-nav tab whose label is {@code label}, tolerating every form it takes.
     *
     * <p>Three forms exist on-device, and a locator has to accept all three while rejecting body
     * copy that merely contains the word:
     * <ul>
     *   <li><b>unselected</b> — {@code "Shop"}, the plain label;</li>
     *   <li><b>selected</b> — {@code "Dashboard\nDashboard"}; GNav renders the active tab as icon
     *       + label in a Row while the label also remains its own semantics node, and the two
     *       merge;</li>
     *   <li><b>badged</b> — {@code "1\nShop"}; the cart count merges into the tab node.</li>
     * </ul>
     *
     * <p>Neither obvious selector works. A {@code descContains} is unscoped, and the labels are
     * short common words: on the client Home feed "Book" also appears in "Book trusted
     * professionals", "Book Individual", "Plan Group Booking", "Book again" and "Book a service",
     * so the first match wins and the tab is never opened. An exact {@code description} match
     * fixes that but then misses the selected tab and, worse, the badged Shop tab — which is how
     * {@code isClientShell()} started answering false and eleven store tests skipped with "the
     * client account did not land in the client shell" against an account that had.
     *
     * <p>So the rule is: <b>some newline-separated segment of the description equals the label
     * exactly</b>. {@code "1\nShop"} and {@code "Dashboard\nDashboard"} both qualify;
     * {@code "Book Individual"} and {@code "Book a service"} do not, because their only segment is
     * the whole phrase. Evaluated in Java rather than as a {@code descriptionMatches} regex,
     * because the pattern would have to survive Java escaping, JSON encoding and UiAutomator's own
     * parsing, and a lost backslash there fails silently.
     *
     * <p>When more than one node qualifies the lowest on screen wins — the nav bar is at the
     * bottom, and this is the last defence against a heading that happens to match.
     *
     * @return the element, or null if no tab with that label is present
     */
    private WebElement tabElement(String label, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        do {
            try {
                WebElement best = null;
                for (WebElement candidate : findAll(descContains(label))) {
                    String desc = candidate.getAttribute("content-desc");
                    if (desc == null || !hasSegment(desc, label)) {
                        continue;
                    }
                    if (best == null
                            || candidate.getLocation().getY() > best.getLocation().getY()) {
                        best = candidate;
                    }
                }
                if (best != null) {
                    return best;
                }
            } catch (StaleElementReferenceException | NoSuchElementException e) {
                // The shell was mid-transition: the candidates were found and then the frame they
                // belonged to was replaced before their attributes could be read.
                //
                // BOTH exceptions mean the same thing here and both have to be caught. A vanished
                // element raises StaleElementReference on some paths and NoSuchElement on others —
                // UiAutomator2 reports a getAttribute against a node that is no longer in the
                // hierarchy as the latter. Catching only staleness left the other one to escape a
                // method whose whole contract is to return null for "no such tab", and it did,
                // one navigation after a login.
                LOG.debug("BottomNav: '{}' went away mid-read ({}); looking again",
                        label, e.getClass().getSimpleName());
            }
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    /** True when one of {@code desc}'s newline-separated segments equals {@code label}. */
    private static boolean hasSegment(String desc, String label) {
        for (String segment : desc.split("\n")) {
            if (segment.trim().equals(label)) {
                return true;
            }
        }
        return false;
    }

    /** Taps the tab with the given label (use the constants above). */
    public BottomNavBar open(String label) {
        LOG.info("BottomNav: opening '{}'", label);
        WebElement element = tabElement(label, Duration.ofSeconds(20));
        if (element == null) {
            throw new org.openqa.selenium.NoSuchElementException(
                    "No bottom-nav tab labelled '" + label + "' is on screen. The shell may not "
                            + "have rendered, or a modal may be covering it.");
        }
        element.click();
        return this;
    }

    /** True if the tab is rendered — i.e. this shell owns that tab. */
    public boolean hasTab(String label) {
        return tabElement(label, Duration.ofSeconds(10)) != null;
    }

    /** True once every listed tab is present — used to assert which shell we landed in. */
    public boolean hasAllTabs(String... labels) {
        for (String label : labels) {
            if (!hasTab(label)) {
                LOG.warn("BottomNav: tab '{}' not found", label);
                return false;
            }
        }
        return true;
    }

    /** True if the signed-in shell is the client one. */
    public boolean isClientShell() {
        return hasAllTabs(CLIENT_HOME, CLIENT_BOOK, CLIENT_APPOINTMENTS, CLIENT_SHOP, CLIENT_PROFILE);
    }

    /** True if the signed-in shell is the professional one. */
    public boolean isProfessionalShell() {
        return hasAllTabs(PRO_DASHBOARD, PRO_BOOKINGS, PRO_CLIENT_HUB, PRO_STORE, PRO_ACCOUNT);
    }

    /** True if the signed-in shell is the admin console. */
    public boolean isAdminShell() {
        return hasAllTabs(ADMIN_DASHBOARD, ADMIN_PROS, ADMIN_QUALITY, ADMIN_REPORTS);
    }

    /**
     * The Shop tab's cart badge count, or 0 when no badge is drawn ("9+" reports as 10).
     *
     * <p><b>Only trust this on the Shop tab.</b> The badge is a bare number with no distinguishing
     * label, and the dashboards are full of bare numbers of their own (KPI values, alert counts —
     * an on-device dump of the professional dashboard showed standalone {@code "0"} and {@code "2"}
     * nodes). There is no way to tell them apart from the accessibility tree alone, so this method
     * is a best-effort read for a screen you already know is the storefront; prefer asserting on the
     * cart's actual contents ({@code ClientCartScreen}) whenever the count itself is the point.
     */
    public int cartBadgeCount() {
        // The badge is not a node of its own: it merges into the Shop tab's label, so a cart holding
        // one item exports content-desc "1\nShop" (verified on-device). Reading the digit off that
        // node is both simpler and safer than hunting for a bare number, which collides with every
        // KPI and counter elsewhere on screen.
        // Several nodes contain "Shop": the storefront's own app-bar title (bare "Shop", no count)
        // comes first in the tree, and the nav tab ("9+\nShop") comes later. Matching the first hit
        // therefore always reported 0. Scan them all and take the one carrying a count.
        for (var element : findAll(descContains(CLIENT_SHOP))) {
            String label = element.getAttribute("content-desc");
            if (label == null || label.equals(CLIENT_SHOP)) {
                continue;
            }
            if (label.contains("9+")) {
                return 10;  // the badge caps at "9+", so any larger cart reports as 10
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(label);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 0;
    }
}
