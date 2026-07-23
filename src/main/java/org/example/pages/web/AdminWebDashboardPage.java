package org.example.pages.web;

import com.microsoft.playwright.Page;
import org.example.base.WebBasePage;

/**
 * The native web dashboard — {@code lib/web/admin_web_dashboard.dart}.
 *
 * <p>Unlike the rest of the portal, this screen was built for the browser rather than reused from the
 * app: a KPI row, a needs-attention callout, the management grid and a real recent-activity table
 * fed by the admin notification feed. It renders as a plain scrollable widget with no Scaffold, so it
 * drops straight into the shell's content area.
 *
 * <p>Its tiles navigate by <em>key</em> ('users', 'reports', 'store', …) rather than by pushing a
 * route, so a tile click is asserted through the shell's selection changing — see
 * {@link WebShellPage#topBarShows(String)}.
 */
public class AdminWebDashboardPage extends WebBasePage {

    // ---- KPI cards ----------------------------------------------------------
    public static final String KPI_TOTAL_USERS = "Total users";
    public static final String KPI_ACTIVE_HOLDS = "Active holds";
    public static final String KPI_OPEN_REPORTS = "Open reports";
    public static final String KPI_SERVICES = "Services";

    public static final String[] KPIS = {KPI_TOTAL_USERS, KPI_ACTIVE_HOLDS, KPI_OPEN_REPORTS, KPI_SERVICES};

    // ---- sections -----------------------------------------------------------
    public static final String SECTION_MANAGE = "Manage";
    public static final String SECTION_RECENT = "Recent activity";
    public static final String LATEST_EVENTS = "Latest events";
    public static final String VIEW_ALL = "View all";
    public static final String NO_RECENT_ACTIVITY = "No recent activity";

    // ---- attention ----------------------------------------------------------
    public static final String ALL_CLEAR = "You're all caught up";
    public static final String NEEDS_ATTENTION = "need attention";
    public static final String OPEN_NOTIFICATION_CENTER = "Open the notification center";

    // ---- greeting -----------------------------------------------------------
    public static final String GREETING_SUFFIX = ", Admin";
    public static final String GLANCE = "everything at a glance";

    // ---- management tiles (same labels as the sidebar) ----------------------
    public static final String[] TILES = {
            WebShellPage.ALL_USERS, WebShellPage.SERVICES, WebShellPage.QUALITY,
            WebShellPage.REPORTS, WebShellPage.PRICING, WebShellPage.ENFORCEMENTS,
            WebShellPage.TRAINING, WebShellPage.STORE, WebShellPage.REGIONS,
    };

    public AdminWebDashboardPage(Page page) {
        super(page);
    }

    /** True once the dashboard has loaded (the greeting is its first line). */
    public boolean isLoaded() {
        return isVisibleContaining(GREETING_SUFFIX, 30_000)
                || isVisibleContaining(SECTION_MANAGE, 10_000);
    }

    /** True when the greeting line and today's date are rendered. */
    public boolean showsGreeting() {
        return isVisibleContaining(GREETING_SUFFIX, 10_000)
                && isVisibleContaining(GLANCE, 10_000);
    }

    // ---- KPIs ---------------------------------------------------------------

    /** True when all four KPI cards are present. */
    public boolean showsAllKpis() {
        for (String kpi : KPIS) {
            if (!isVisibleContaining(kpi, 10_000)) {
                LOG.warn("Dashboard: KPI '{}' not found", kpi);
                return false;
            }
        }
        return true;
    }

    /**
     * The numeric value of a KPI card, or -1 when it cannot be read.
     * The value renders as its own semantics node next to the card label.
     */
    public int kpiValue(String kpiLabel) {
        String label = labelOf(kpiLabel);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(label);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** True when the users KPI shows its clients/pros split. */
    public boolean showsUserSplit() {
        return isVisibleContaining("clients", 10_000) && isVisibleContaining("pros", 10_000);
    }

    // ---- attention ----------------------------------------------------------

    /** True when unread notifications produced the amber attention callout. */
    public boolean showsAttentionCallout() {
        return isVisibleContaining(NEEDS_ATTENTION, 8_000);
    }

    /** True when there is nothing to action. */
    public boolean showsAllClear() {
        return isVisibleContaining("caught up", 8_000);
    }

    /** True when either state rendered — the assertion that works regardless of environment data. */
    public boolean showsAttentionOrAllClear() {
        return showsAttentionCallout() || showsAllClear();
    }

    // ---- manage grid --------------------------------------------------------

    /** True when every management tile is in the grid. */
    public boolean showsAllTiles() {
        for (String tile : TILES) {
            if (!isVisible(tile, 8_000)) {
                LOG.warn("Dashboard: tile '{}' not found", tile);
                return false;
            }
        }
        return true;
    }

    /** Clicks a management tile, which switches the shell's destination. */
    public WebShellPage openTile(String title) {
        LOG.info("Dashboard: opening tile '{}'", title);
        click(title);
        return new WebShellPage(page);
    }

    // ---- recent activity ----------------------------------------------------

    /** True when the recent-activity panel rendered (rows or its empty state). */
    public boolean showsRecentActivity() {
        return isVisibleContaining(LATEST_EVENTS, 10_000);
    }

    /** True when the panel has no events to show. */
    public boolean recentActivityIsEmpty() {
        return isVisibleContaining(NO_RECENT_ACTIVITY, 8_000);
    }

    /** True when at least one event carries a relative timestamp ("just now", "2h ago", …). */
    public boolean showsRelativeTimestamps() {
        return isVisibleContaining(" ago", 8_000) || isVisibleContaining("just now", 8_000);
    }

    /** True when events are tagged with their category pill (Moderation, Store, Support, …). */
    public boolean showsCategoryPills() {
        return isVisibleContaining("Moderation", 5_000)
                || isVisibleContaining("Store", 5_000)
                || isVisibleContaining("Support", 5_000)
                || isVisibleContaining("Users", 5_000)
                || isVisibleContaining("Update", 5_000);
    }

    /** Follows "View all" out of the recent-activity panel. */
    public WebShellPage viewAllActivity() {
        click(VIEW_ALL);
        return new WebShellPage(page);
    }

    // ---- responsive ---------------------------------------------------------

    /** Sets the viewport and returns whether the dashboard still renders its sections. */
    public boolean rendersAt(int width, int height) {
        page.setViewportSize(width, height);
        return isVisibleContaining(SECTION_MANAGE, 10_000);
    }
}
