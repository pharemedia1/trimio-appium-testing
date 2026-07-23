package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Store administration — {@code screens/Admin/stores/*}: vendors, applications, products,
 * fulfillment and payouts.
 *
 * <p>This is the marketplace's control panel. Three behaviours are worth guarding above the rest:
 * <ul>
 *   <li>creating a vendor login / approving an application issues <em>credentials</em>, shown once —
 *       and the flow must survive an invite-email failure ("Approved — invite email failed") rather
 *       than losing the account it just created;</li>
 *   <li>marking a group shipped releases the vendor's payout ("Payout on ship: $x"), so the ship
 *       action and the money movement are the same event;</li>
 *   <li>every screen here refuses a non-admin session ("Admin sign-in required").</li>
 * </ul>
 *
 * <p>The same screens back the web portal's Marketplace destinations, so {@code web.AdminStoreTest}
 * covers the browser half with the same copy.
 */
public class AdminStoreScreen extends MobileBasePage {

    // ---- vendors ------------------------------------------------------------
    public static final String SEARCH_VENDORS = "Search vendors";
    public static final String NEW_VENDOR = "New vendor";
    public static final String EDIT_VENDOR = "Edit vendor";
    public static final String NAME_REQUIRED = "Name *";
    public static final String SLUG_REQUIRED = "Slug *";
    public static final String COMMISSION_HINT = "Commission rate (0–1, e.g. 0.15)";
    public static final String CREATE = "Create";
    public static final String CREATE_LOGIN = "Create login";
    public static final String CREATE_VENDOR_LOGIN = "Create vendor login";
    public static final String INVITE_VENDOR = "Invite vendor";
    public static final String SEND_INVITE = "Send invite";
    public static final String LOGIN_EMAIL_REQUIRED = "Login email *";
    public static final String VENDOR_EMAIL_REQUIRED = "Vendor email *";
    public static final String ONBOARDING_LINK = "Onboarding link";
    public static final String REFRESH_STRIPE = "Refresh Stripe status";
    public static final String COPY_LINK = "Copy link";
    public static final String ADMIN_SIGN_IN_REQUIRED = "Admin sign-in required";

    // ---- status pills -------------------------------------------------------
    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_SUSPENDED = "Suspended";

    // ---- applications -------------------------------------------------------
    public static final String APPROVE = "Approve";
    public static final String APPROVE_AND_INVITE = "Approve & invite";
    public static final String APPROVE_APPLICATION = "Approve application";
    public static final String REJECT = "Reject";
    public static final String REJECT_APPLICATION = "Reject application";
    public static final String INTERNAL_NOTE = "Internal note (optional)";
    public static final String INVITE_EMAIL_FAILED = "Approved — invite email failed";
    public static final String APPLIED_PREFIX = "Applied ";

    // ---- products -----------------------------------------------------------
    public static final String SEARCH_PRODUCTS = "Search products";
    public static final String NEW_PRODUCT = "New product";
    public static final String PRICE_REQUIRED = "Price (\\$) *";
    public static final String NEEDS_VARIANT =
            "Add a variant to set a price — a product needs one to sell.";
    public static final String STATUS_DRAFT = "Draft";
    public static final String STATUS_ARCHIVED = "Archived";
    public static final String PRO_ONLY = "Pro-only";

    // ---- fulfillment / payouts ---------------------------------------------
    public static final String SEARCH_ORDERS = "Search order # or item";
    public static final String SHIP = "Ship";
    public static final String MARK_SHIPPED = "Mark shipped";
    public static final String CARRIER_HINT = "Carrier (e.g. USPS)";
    public static final String TRACKING_HINT = "Tracking number";
    public static final String PAYOUT_ON_SHIP = "Payout on ship:";
    public static final String SEARCH_TRANSFERS = "Search transfers by order #";

    public AdminStoreScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(SEARCH_VENDORS), Duration.ofSeconds(25))
                || isPresentAfterScroll(NEW_VENDOR);
    }

    /** True when the screen refused the session because it is not an admin. */
    public boolean requiresAdminSignIn() {
        return isPresent(descContains(ADMIN_SIGN_IN_REQUIRED), Duration.ofSeconds(10));
    }

    // ---- vendors ------------------------------------------------------------

    public AdminStoreScreen searchVendors(String query) {
        type(editText(0), query);
        hideKeyboard();
        return this;
    }

    public boolean hasVendor(String name) {
        return isPresentAfterScroll(name);
    }

    /** True when vendor rows carry a status pill. */
    public boolean showsVendorStatuses() {
        return isPresentAfterScroll(STATUS_ACTIVE)
                || isPresentAfterScroll(STATUS_PENDING)
                || isPresentAfterScroll(STATUS_SUSPENDED);
    }

    /** Creates a vendor from the "New vendor" dialog. */
    public AdminStoreScreen createVendor(String name, String slug, String commissionRate) {
        LOG.info("AdminStore: creating vendor '{}'", name);
        scrollAndTap(NEW_VENDOR);
        type(editText(0), name);
        type(editText(1), slug);
        type(editText(2), commissionRate);
        hideKeyboard();
        tap(accId(CREATE));
        return this;
    }

    /** Opens the manual "create vendor login" dialog. */
    public AdminStoreScreen createVendorLogin(String email, String password) {
        scrollAndTap(CREATE_LOGIN);
        type(editText(0), email);
        type(editText(1), password);
        hideKeyboard();
        tap(accId(CREATE));
        return this;
    }

    /** Sends an email invite to a vendor. */
    public AdminStoreScreen inviteVendor(String email) {
        scrollAndTap(INVITE_VENDOR);
        type(editText(0), email);
        hideKeyboard();
        scrollAndTap(SEND_INVITE);
        return this;
    }

    /** True when issued credentials are displayed (once) for copying. */
    public boolean showsIssuedCredentials() {
        return isPresentAfterScroll("Email:") || isPresentAfterScroll("Password:");
    }

    // ---- applications -------------------------------------------------------

    /** True if at least one application row with an applied date is listed. */
    public boolean hasPendingApplication() {
        return isPresentAfterScroll(APPLIED_PREFIX);
    }

    /** Approves the first application and issues an invite. */
    public AdminStoreScreen approveApplicationWithInvite() {
        scrollAndTap(APPROVE_AND_INVITE);
        if (isPresent(accId(APPROVE), SHORT_TIMEOUT)) {
            tap(accId(APPROVE));
        }
        return this;
    }

    /** Rejects the first application with an internal note. */
    public AdminStoreScreen rejectApplication(String note) {
        scrollAndTap(REJECT);
        type(editText(0), note);
        hideKeyboard();
        tap(accId(REJECT));
        return this;
    }

    /** True when the approval succeeded but the invite email could not be delivered. */
    public boolean showsInviteEmailFailure() {
        return isPresent(descContains(INVITE_EMAIL_FAILED), Duration.ofSeconds(15));
    }

    // ---- products -----------------------------------------------------------

    public AdminStoreScreen searchProducts(String query) {
        type(editText(0), query);
        hideKeyboard();
        return this;
    }

    /** Creates a product from the "New product" dialog. */
    public AdminStoreScreen createProduct(String name, String slug) {
        scrollAndTap(NEW_PRODUCT);
        type(editText(0), name);
        type(editText(1), slug);
        hideKeyboard();
        tap(accId(CREATE));
        return this;
    }

    public boolean hasProduct(String name) {
        return isPresentAfterScroll(name);
    }

    /** True when the "needs a variant to sell" warning is displayed. */
    public boolean showsNeedsVariantWarning() {
        return isPresentAfterScroll("needs one to sell");
    }

    // ---- fulfillment --------------------------------------------------------

    public AdminStoreScreen searchOrders(String query) {
        type(editText(0), query);
        hideKeyboard();
        return this;
    }

    /** Marks the first unshipped group shipped with a carrier and tracking number. */
    public AdminStoreScreen shipFirstOrder(String carrier, String tracking) {
        LOG.info("AdminStore: shipping the first order via {} ({})", carrier, tracking);
        scrollAndTap(SHIP);
        type(editText(0), carrier);
        type(editText(1), tracking);
        hideKeyboard();
        scrollAndTap(MARK_SHIPPED);
        return this;
    }

    /** True when the fulfilment row discloses the payout released on shipping. */
    public boolean showsPayoutOnShip() {
        return isPresentAfterScroll("Payout");
    }

    // ---- payouts ------------------------------------------------------------

    public AdminStoreScreen searchTransfers(String orderNumber) {
        type(editText(0), orderNumber);
        hideKeyboard();
        return this;
    }

    public boolean hasTransferForOrder(int orderId) {
        return isPresentAfterScroll("Order #" + orderId);
    }
}
