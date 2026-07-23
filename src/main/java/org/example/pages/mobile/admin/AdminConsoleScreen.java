package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.example.pages.mobile.common.BottomNavBar;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The admin console home — {@code screens/Admin/admin_home_page.dart}, inside the
 * {@code AdminBottomNavigationBar} shell.
 *
 * <p>The front door for every admin queue: a KPI header (users · active holds · open reports), a
 * "needs attention" strip driven by the notification centre, and a ten-tile management grid whose
 * tiles each carry their own attention badge.
 *
 * <p>The same tiles exist as sidebar destinations in the web portal, so a change here usually needs
 * checking in both places — see {@code pages.web.WebShellPage}.
 */
public class AdminConsoleScreen extends MobileBasePage {

    // ---- tiles (also the web sidebar labels) --------------------------------
    public static final String TILE_ALL_USERS = "All Users";
    public static final String TILE_SERVICES = "Services";
    public static final String TILE_QUALITY = "Quality Control";
    public static final String TILE_REPORTS = "Reports";
    public static final String TILE_PRICING = "Price Overrides";
    public static final String TILE_ENFORCEMENTS = "Enforcements";
    public static final String TILE_TRAINING = "Training";
    public static final String TILE_STORE = "Store";
    public static final String TILE_LICENSES = "Licenses";
    public static final String TILE_STATES = "States";

    public static final String[] ALL_TILES = {
            TILE_ALL_USERS, TILE_SERVICES, TILE_QUALITY, TILE_REPORTS, TILE_PRICING,
            TILE_ENFORCEMENTS, TILE_TRAINING, TILE_STORE, TILE_LICENSES, TILE_STATES,
    };

    // ---- copy used as assertions -------------------------------------------
    public static final String TITLE = "Admin Console";
    public static final String SECTION_MANAGE = "Manage";
    public static final String NEEDS_ATTENTION = "attention";
    public static final String SUSPENDED_BANNER = "currently suspended";
    public static final String REVIEW = "Review";
    public static final String NOTIFICATIONS = "Notifications";
    public static final String MARK_ALL_READ = "Mark all read";
    public static final String LOG_OUT = "Log out";

    public AdminConsoleScreen(AndroidDriver driver) {
        super(driver);
    }

    public BottomNavBar nav() {
        return new BottomNavBar(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(TITLE), Duration.ofSeconds(30));
    }

    /** True when the console shell (Dashboard/Pros/Quality/Reports) is the one on screen. */
    public boolean isAdminShell() {
        return nav().isAdminShell();
    }

    // ---- header -------------------------------------------------------------

    /** True when the KPI header rendered its three counters. */
    public boolean showsKpiHeader() {
        return isPresentAfterScroll("Users")
                && isPresentAfterScroll("Active holds")
                && isPresentAfterScroll("Open reports");
    }

    /** True when the notification-driven attention strip is showing. */
    public boolean showsAttentionStrip() {
        return isPresentAfterScroll(NEEDS_ATTENTION);
    }

    /** True when the suspension banner (shown only with no unread notifications) is up. */
    public boolean showsSuspensionBanner() {
        return isPresentAfterScroll(SUSPENDED_BANNER);
    }

    // ---- management grid ----------------------------------------------------

    /** True if a tile with this title is in the grid. */
    public boolean hasTile(String title) {
        return isPresentAfterScroll(title);
    }

    /** True when every management tile is present. */
    public boolean hasAllTiles() {
        for (String tile : ALL_TILES) {
            if (!hasTile(tile)) {
                LOG.warn("AdminConsole: tile '{}' not found", tile);
                return false;
            }
        }
        return true;
    }

    /** Opens a tile by title. */
    public AdminConsoleScreen openTile(String title) {
        LOG.info("AdminConsole: opening tile '{}'", title);
        scrollAndTap(title);
        return this;
    }

    /** Opens the "Manage" section's Store tile and returns the store page object. */
    public AdminStoreScreen openStore() {
        openTile(TILE_STORE);
        return new AdminStoreScreen(driver);
    }

    /** Opens Quality Control. */
    public AdminQualityScreen openQuality() {
        openTile(TILE_QUALITY);
        return new AdminQualityScreen(driver);
    }

    /** Opens Price Overrides. */
    public AdminPricingScreen openPricing() {
        openTile(TILE_PRICING);
        return new AdminPricingScreen(driver);
    }

    /** Opens Enforcements. */
    public AdminEnforcementScreen openEnforcements() {
        openTile(TILE_ENFORCEMENTS);
        return new AdminEnforcementScreen(driver);
    }

    /** Opens Training. */
    public AdminTrainingScreen openTraining() {
        openTile(TILE_TRAINING);
        return new AdminTrainingScreen(driver);
    }

    // ---- notifications ------------------------------------------------------

    /** Opens the admin notification centre via the app-bar bell. */
    public AdminConsoleScreen openNotifications() {
        tap(accId(NOTIFICATIONS));
        return this;
    }

    public AdminConsoleScreen markAllNotificationsRead() {
        scrollAndTap(MARK_ALL_READ);
        return this;
    }

    /** Signs the admin out from the console app bar. */
    public AdminConsoleScreen logout() {
        tap(accId(LOG_OUT));
        return this;
    }
}
