package org.example.pages.web;

import com.microsoft.playwright.Page;
import org.example.base.WebBasePage;

/**
 * The Marketplace destinations of the admin portal — Store (vendors, products, fulfillment, payouts)
 * and Applications, i.e. {@code screens/Admin/stores/*} rendered inside {@link WebShellPage}.
 *
 * <p>This is where a marketplace is actually operated: vendors are created and given logins,
 * applications become stores, products are curated and orders are marked shipped — which is the
 * event that releases a vendor's money. The copy constants below come from the Dart source verbatim
 * so a wording change fails the test loudly rather than silently breaking a selector.
 */
public class AdminStorePage extends WebBasePage {

    // ---- vendors ------------------------------------------------------------
    public static final String SEARCH_VENDORS = "Search vendors";
    public static final String NEW_VENDOR = "New vendor";
    public static final String EDIT_VENDOR = "Edit vendor";
    public static final String CREATE = "Create";
    public static final String CREATE_AND_INVITE = "Create & invite";
    public static final String CREATE_LOGIN = "Create login";
    public static final String INVITE_VENDOR = "Invite vendor";
    public static final String SEND_INVITE = "Send invite";
    public static final String ONBOARDING_LINK = "Onboarding link";
    public static final String REFRESH_STRIPE = "Refresh Stripe status";
    public static final String COPY_LINK = "Copy link";
    public static final String NAME_REQUIRED = "Name *";
    public static final String SLUG_REQUIRED = "Slug *";
    public static final String COMMISSION_HINT = "Commission rate (0–1, e.g. 0.15)";
    public static final String ADMIN_SIGN_IN_REQUIRED = "Admin sign-in required";
    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_SUSPENDED = "Suspended";

    // ---- applications -------------------------------------------------------
    public static final String APPROVE = "Approve";
    public static final String APPROVE_AND_INVITE = "Approve & invite";
    public static final String REJECT = "Reject";
    public static final String INTERNAL_NOTE = "Internal note (optional)";
    public static final String APPLIED_PREFIX = "Applied ";
    public static final String INVITE_EMAIL_FAILED = "Approved — invite email failed";

    // ---- products -----------------------------------------------------------
    public static final String SEARCH_PRODUCTS = "Search products";
    public static final String NEW_PRODUCT = "New product";
    public static final String NEEDS_VARIANT = "needs one to sell";
    public static final String PRO_ONLY = "Pro-only";
    public static final String DRAFT = "Draft";
    public static final String ARCHIVED = "Archived";

    // ---- fulfillment / payouts ---------------------------------------------
    public static final String SEARCH_ORDERS = "Search order # or item";
    public static final String SHIP = "Ship";
    public static final String MARK_SHIPPED = "Mark shipped";
    public static final String CARRIER = "Carrier (e.g. USPS)";
    public static final String TRACKING = "Tracking number";
    public static final String PAYOUT_ON_SHIP = "Payout on ship:";
    public static final String SEARCH_TRANSFERS = "Search transfers by order #";

    public AdminStorePage(Page page) {
        super(page);
    }

    /** True once a store destination has rendered. */
    public boolean isLoaded() {
        return isVisibleContaining(SEARCH_VENDORS, 25_000)
                || isVisibleContaining(NEW_VENDOR, 10_000);
    }

    /** True when the screen refused a non-admin session. */
    public boolean requiresAdminSignIn() {
        return isVisibleContaining(ADMIN_SIGN_IN_REQUIRED, 10_000);
    }

    // ---- vendors ------------------------------------------------------------

    /** Filters the vendor list. */
    public AdminStorePage searchVendors(String query) {
        fillField(0, query);
        return this;
    }

    public boolean hasVendor(String name) {
        return isVisibleContaining(name, 10_000);
    }

    /** True when vendor rows carry a status pill. */
    public boolean showsVendorStatuses() {
        return isVisibleContaining(STATUS_ACTIVE, 8_000)
                || isVisibleContaining(STATUS_PENDING, 8_000)
                || isVisibleContaining(STATUS_SUSPENDED, 8_000);
    }

    /** Opens the "New vendor" dialog. */
    public AdminStorePage openNewVendor() {
        clickContaining(NEW_VENDOR);
        return this;
    }

    /** Fills and submits the new-vendor dialog. */
    public AdminStorePage createVendor(String name, String slug, String commissionRate) {
        LOG.info("AdminStore(web): creating vendor '{}'", name);
        openNewVendor();
        fillField(0, name);
        fillField(1, slug);
        fillField(2, commissionRate);
        clickContaining(CREATE);
        return this;
    }

    /** True when the create dialog is still up — i.e. a required field blocked the submit. */
    public boolean createDialogStillOpen() {
        return isVisibleContaining(NAME_REQUIRED, 5_000) || isVisibleContaining(SLUG_REQUIRED, 5_000);
    }

    /** True when issued credentials are displayed once for copying. */
    public boolean showsIssuedCredentials() {
        return isVisibleContaining("Email:", 10_000) || isVisibleContaining("Password:", 10_000);
    }

    // ---- applications -------------------------------------------------------

    /** True when at least one application row with an applied date is listed. */
    public boolean hasPendingApplication() {
        return isVisibleContaining(APPLIED_PREFIX, 15_000);
    }

    /** Approves the first application and issues the invite. */
    public AdminStorePage approveApplicationWithInvite() {
        clickContaining(APPROVE_AND_INVITE);
        if (isVisible(APPROVE, 5_000)) {
            click(APPROVE);
        }
        return this;
    }

    /** Rejects an application with an internal note. */
    public AdminStorePage rejectApplication(String note) {
        clickContaining(REJECT);
        fillField(0, note);
        clickContaining(REJECT);
        return this;
    }

    /** True when approval succeeded but the invite email could not be sent. */
    public boolean showsInviteEmailFailure() {
        return isVisibleContaining(INVITE_EMAIL_FAILED, 15_000);
    }

    // ---- products -----------------------------------------------------------

    public AdminStorePage searchProducts(String query) {
        fillField(0, query);
        return this;
    }

    /** Creates a product from the "New product" dialog. */
    public AdminStorePage createProduct(String name, String slug) {
        LOG.info("AdminStore(web): creating product '{}'", name);
        clickContaining(NEW_PRODUCT);
        fillField(0, name);
        fillField(1, slug);
        clickContaining(CREATE);
        return this;
    }

    public boolean hasProduct(String name) {
        return isVisibleContaining(name, 10_000);
    }

    /** True when the product cannot sell because it has no variant. */
    public boolean showsNeedsVariantWarning() {
        return isVisibleContaining(NEEDS_VARIANT, 8_000);
    }

    // ---- fulfillment --------------------------------------------------------

    public AdminStorePage searchOrders(String query) {
        fillField(0, query);
        return this;
    }

    /** Marks the first unshipped group shipped. */
    public AdminStorePage shipFirstOrder(String carrier, String tracking) {
        LOG.info("AdminStore(web): shipping the first order via {} ({})", carrier, tracking);
        clickContaining(SHIP);
        fillField(0, carrier);
        fillField(1, tracking);
        clickContaining(MARK_SHIPPED);
        return this;
    }

    /** True when the fulfilment row discloses the payout released on shipping. */
    public boolean showsPayoutOnShip() {
        return isVisibleContaining("Payout", 10_000);
    }

    // ---- payouts ------------------------------------------------------------

    public AdminStorePage searchTransfers(String orderNumber) {
        fillField(0, orderNumber);
        return this;
    }

    public boolean hasTransferForOrder(String orderNumber) {
        return isVisibleContaining("Order #" + orderNumber, 10_000);
    }
}
