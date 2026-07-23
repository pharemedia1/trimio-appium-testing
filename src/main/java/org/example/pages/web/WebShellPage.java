package org.example.pages.web;

import com.microsoft.playwright.Page;
import org.example.base.WebBasePage;

/**
 * The desktop portal shell — {@code lib/web/web_shell.dart}.
 *
 * <p>A persistent sidebar plus top bar wrapped around a content area whose screens are swapped in
 * place (kept alive) rather than pushed — the "website" feel. Below 900px the sidebar collapses into
 * a drawer behind a hamburger button, which is the one responsive behaviour worth automating.
 *
 * <p>The sidebar's destination list is role-dependent and is itself an access-control assertion: an
 * admin sees the Manage and Marketplace groups, a vendor sees only "My Store". A vendor who can see
 * an admin destination is a defect, not a cosmetic issue.
 */
public class WebShellPage extends WebBasePage {

    // ---- admin destinations (web_shell.dart _adminDests) --------------------
    public static final String DASHBOARD = "Dashboard";
    public static final String ALL_USERS = "All Users";
    public static final String SERVICES = "Services";
    public static final String QUALITY = "Quality Control";
    public static final String REPORTS = "Reports";
    public static final String PRICING = "Price Overrides";
    public static final String ENFORCEMENTS = "Enforcements";
    public static final String TRAINING = "Training";
    public static final String STORE = "Store";
    public static final String APPLICATIONS = "Applications";
    public static final String REGIONS = "Countries & States";

    public static final String[] ADMIN_DESTINATIONS = {
            DASHBOARD, ALL_USERS, SERVICES, QUALITY, REPORTS, PRICING,
            ENFORCEMENTS, TRAINING, STORE, APPLICATIONS, REGIONS,
    };

    // ---- vendor destinations (web_shell.dart _vendorDests) ------------------
    public static final String OVERVIEW = "Overview";
    public static final String CATALOG = "Catalog";
    public static final String ORDERS = "Orders";
    public static final String PAYOUTS = "Payouts";

    public static final String[] VENDOR_DESTINATIONS = {OVERVIEW, CATALOG, ORDERS, PAYOUTS};

    // ---- sidebar group headers ----------------------------------------------
    public static final String GROUP_OVERVIEW = "OVERVIEW";
    public static final String GROUP_MANAGE = "MANAGE";
    public static final String GROUP_MARKETPLACE = "MARKETPLACE";
    public static final String GROUP_MY_STORE = "MY STORE";

    // ---- chrome -------------------------------------------------------------
    public static final String BRAND = "Trimio";
    public static final String ADMIN_CONSOLE = "Admin Console";
    public static final String VENDOR_PORTAL = "Vendor Portal";
    public static final String ADMINISTRATOR = "Administrator";
    public static final String SIGNED_IN = "Signed in";
    public static final String PAYOUTS_ON = "Payouts on";
    public static final String PAYOUTS_OFF = "Payouts off";
    public static final String NOTIFICATIONS = "Notifications";
    public static final String ACCOUNT = "Account";
    public static final String LOG_OUT = "Log out";
    public static final String SEARCH_HINT = "Search users, orders, services…";

    public WebShellPage(Page page) {
        super(page);
    }

    /** True once the shell has rendered (the brand block in the sidebar is the landmark). */
    public boolean isLoaded() {
        return isVisibleContaining(BRAND, 30_000);
    }

    /** True when the signed-in role is admin (the sidebar subtitle says so). */
    public boolean isAdminShell() {
        return isVisibleContaining(ADMIN_CONSOLE, 20_000);
    }

    /** True when the signed-in role is vendor. */
    public boolean isVendorShell() {
        return isVisibleContaining(VENDOR_PORTAL, 20_000);
    }

    // ---- navigation ---------------------------------------------------------

    /** Clicks a sidebar destination by label. */
    public WebShellPage openDestination(String label) {
        LOG.info("WebShell: opening destination '{}'", label);
        click(label);
        return this;
    }

    /** True if a destination with this label is in the sidebar. */
    public boolean hasDestination(String label) {
        return isVisible(label, 8_000);
    }

    /** True when every listed destination is present. */
    public boolean hasAllDestinations(String... labels) {
        for (String label : labels) {
            if (!hasDestination(label)) {
                LOG.warn("WebShell: destination '{}' not found", label);
                return false;
            }
        }
        return true;
    }

    /** True when NONE of the listed destinations is present — the vendor-isolation assertion. */
    public boolean hasNoneOfDestinations(String... labels) {
        for (String label : labels) {
            if (isVisible(label, 2_000)) {
                LOG.warn("WebShell: unexpected destination '{}' is visible", label);
                return false;
            }
        }
        return true;
    }

    /** True when the top bar reflects the selected destination. */
    public boolean topBarShows(String label) {
        return isVisible(label, 15_000);
    }

    /** True when the sidebar group headers rendered. */
    public boolean showsAdminGroups() {
        return isVisibleContaining(GROUP_MANAGE, 8_000)
                && isVisibleContaining(GROUP_MARKETPLACE, 8_000);
    }

    // ---- badges -------------------------------------------------------------

    /**
     * The badge value next to a destination, or empty when it carries none.
     *
     * <p>Badges render as their own text node inside the nav item, so this reads the label of the
     * nearest numeric node; callers should treat "" as "no attention count", not as zero-vs-missing.
     */
    public String badgeFor(String destination) {
        return labelOf(destination);
    }

    /** True when at least one red attention badge is showing in the sidebar. */
    public boolean hasAnyBadge() {
        return isVisibleContaining("99+", 2_000) || page.locator("flt-semantics[aria-label]")
                .filter(new com.microsoft.playwright.Locator.FilterOptions()
                        .setHasText(java.util.regex.Pattern.compile("^\\d{1,2}$")))
                .count() > 0;
    }

    // ---- top bar ------------------------------------------------------------

    /** Opens the notification centre / panel from the bell. */
    public WebShellPage openNotifications() {
        click(NOTIFICATIONS);
        return this;
    }

    /** Signs out through the avatar menu. */
    public WebLoginPage logout() {
        LOG.info("WebShell: logging out");
        if (isVisible(ACCOUNT, 5_000)) {
            click(ACCOUNT);
        }
        clickContaining(LOG_OUT);
        return new WebLoginPage(page);
    }

    /** True when the sidebar footer identifies an admin principal. */
    public boolean showsAdminIdentity() {
        return isVisibleContaining(ADMINISTRATOR, 8_000) || isVisibleContaining(SIGNED_IN, 8_000);
    }

    // ---- responsive ---------------------------------------------------------

    /** Narrows the viewport below the 900px breakpoint so the sidebar collapses into a drawer. */
    public WebShellPage useNarrowViewport() {
        page.setViewportSize(800, 900);
        return this;
    }

    /** Restores a desktop-width viewport. */
    public WebShellPage useWideViewport() {
        page.setViewportSize(1440, 900);
        return this;
    }

    /** True when the collapsed layout is active (the hamburger button exists). */
    public boolean isDrawerLayout() {
        return isVisibleContaining("menu", 8_000) || !isVisible(GROUP_MANAGE, 3_000);
    }

    /** Opens the mobile drawer. */
    public WebShellPage openDrawer() {
        clickContaining("menu");
        return this;
    }

    // ---- typed sub-pages ----------------------------------------------------

    public AdminWebDashboardPage dashboard() {
        return new AdminWebDashboardPage(page);
    }

    public AdminStorePage store() {
        return new AdminStorePage(page);
    }

    public VendorPortalPage vendor() {
        return new VendorPortalPage(page);
    }
}
