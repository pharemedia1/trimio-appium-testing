package org.example.pages.mobile.common;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

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

    /** Contains-match, because the selected tab exports its label twice — see the class javadoc. */
    private static By tab(String label) {
        return descContains(label);
    }

    /** Taps the tab with the given label (use the constants above). */
    public BottomNavBar open(String label) {
        LOG.info("BottomNav: opening '{}'", label);
        tap(tab(label));
        return this;
    }

    /** True if the tab is rendered — i.e. this shell owns that tab. */
    public boolean hasTab(String label) {
        return isPresent(tab(label), Duration.ofSeconds(10));
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
        for (var element : driver.findElements(descContains(CLIENT_SHOP))) {
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
