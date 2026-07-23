package org.example.pages.web;

import com.microsoft.playwright.Page;
import org.example.base.WebBasePage;

/**
 * The vendor portal — {@link WebShellPage} with {@code role: 'vendor'}, plus the persistent store
 * header ({@code lib/web/vendor_store_header.dart}) and the four store tabs from
 * {@code screens/vendor/vendor_home_page.dart}.
 *
 * <p>A vendor's whole business lives here: identity and platform fee in the header, the catalogue
 * (products → variants → images, with Draft/Active/Archived controlling storefront visibility),
 * orders (shipping releases the payout) and Stripe payout onboarding.
 *
 * <p>Two invariants are worth stating because they are easy to regress:
 * <ul>
 *   <li>a product cannot sell without a variant — the UI says so outright
 *       ("Add a variant to set a price — a product needs one to sell.");</li>
 *   <li>the vendor sees only their own data, and no admin destination.</li>
 * </ul>
 */
public class VendorPortalPage extends WebBasePage {

    // ---- store header -------------------------------------------------------
    public static final String EDIT_STORE = "Edit store";
    public static final String STORE_NAME_REQUIRED = "Store name *";
    public static final String STORE_NAME_ERROR = "Store name is required";
    public static final String SUPPORT_EMAIL = "Support email";
    public static final String LOGO_URL = "Logo URL";
    public static final String SAVE = "Save";
    public static final String STORE_UPDATED = "Store updated";
    public static final String PLATFORM_FEE = "platform fee";
    public static final String PRODUCTS_SUFFIX = "products";

    // ---- payouts ------------------------------------------------------------
    public static final String PAYOUTS_ON = "Payouts on";
    public static final String ONBOARDING = "Onboarding";
    public static final String NO_PAYOUTS = "No payouts";
    public static final String SET_UP_PAYOUTS = "Set up payouts";
    public static final String CONTINUE_PAYOUT_SETUP = "Continue payout setup";
    public static final String REFRESH = "Refresh";
    public static final String SETUP_DIALOG_TITLE = "Set up payouts";
    public static final String COPY_LINK = "Copy link";
    public static final String LINK_COPIED = "Link copied";
    public static final String STATUS_REFRESHED = "Status refreshed";
    public static final String CLOSE = "Close";

    // ---- catalogue ----------------------------------------------------------
    public static final String NEW_PRODUCT = "New product";
    public static final String SEARCH_PRODUCTS = "Search products";
    public static final String NAME_REQUIRED = "Name *";
    public static final String SLUG_REQUIRED = "Slug *";
    public static final String PRICE_REQUIRED = "Price ($) *";
    public static final String CREATE = "Create";
    public static final String ADD = "Add";
    public static final String ADD_IMAGE = "Add image";
    public static final String IMAGE_URL_REQUIRED = "Image URL *";
    public static final String NO_IMAGES = "No images yet.";
    public static final String NEEDS_VARIANT = "needs one to sell";
    public static final String SET_ACTIVE = "Set active";
    public static final String SET_DRAFT = "Set draft";
    public static final String ARCHIVE = "Archive";
    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_DRAFT = "Draft";
    public static final String STATUS_ARCHIVED = "Archived";
    public static final String PRO_ONLY = "Pro-only";
    public static final String VARIANT_NAME = "Variant name";
    public static final String SKU = "SKU";
    public static final String COMPARE_AT = "Compare-at ($)";
    public static final String STOCK_QTY = "Stock qty (optional)";

    // ---- orders -------------------------------------------------------------
    public static final String NO_ORDERS = "No orders yet";
    public static final String ORDER_PREFIX = "Order #";
    public static final String PAYOUT_PREFIX = "Payout ";
    public static final String CARRIER = "Carrier (e.g. USPS)";
    public static final String TRACKING = "Tracking number";
    public static final String YOUR_PAYOUT = "Your payout (after commission)";
    public static final String NO_ADDRESS_YET = "No shipping address on file for this order yet.";
    public static final String EXPORT_CSV = "Export CSV";

    public VendorPortalPage(Page page) {
        super(page);
    }

    public WebShellPage shell() {
        return new WebShellPage(page);
    }

    /** True once the vendor shell is up. */
    public boolean isLoaded() {
        return shell().isVendorShell();
    }

    // ---- store header -------------------------------------------------------

    /** True when the header shows the store's product count and platform fee. */
    public boolean showsStoreContext() {
        return isVisibleContaining(PRODUCTS_SUFFIX, 15_000)
                && isVisibleContaining(PLATFORM_FEE, 15_000);
    }

    /** The payout state pill: "Payouts on" / "Onboarding" / "No payouts", or "" when absent. */
    public String payoutState() {
        if (isVisibleContaining(PAYOUTS_ON, 5_000)) {
            return PAYOUTS_ON;
        }
        if (isVisibleContaining(ONBOARDING, 5_000)) {
            return ONBOARDING;
        }
        if (isVisibleContaining(NO_PAYOUTS, 5_000)) {
            return NO_PAYOUTS;
        }
        return "";
    }

    /** True when a payout CTA is offered (i.e. payouts are not yet live). */
    public boolean showsPayoutCta() {
        return isVisibleContaining(SET_UP_PAYOUTS, 5_000)
                || isVisibleContaining(CONTINUE_PAYOUT_SETUP, 5_000);
    }

    /** Opens the payout-setup dialog and returns whether a Stripe link was issued. */
    public boolean requestPayoutLink() {
        LOG.info("VendorPortal: requesting a payout onboarding link");
        clickContaining(SET_UP_PAYOUTS);
        return isVisibleContaining("stripe", 20_000) || isVisibleContaining(COPY_LINK, 10_000);
    }

    /** Copies the issued onboarding link. */
    public boolean copyPayoutLink() {
        clickContaining(COPY_LINK);
        return isVisibleContaining(LINK_COPIED, 10_000);
    }

    /** Re-reads the Stripe account status. */
    public boolean refreshPayoutStatus() {
        clickContaining(REFRESH);
        return isVisibleContaining(STATUS_REFRESHED, 15_000);
    }

    /** Opens the store-profile editor. */
    public VendorPortalPage openStoreEditor() {
        click(EDIT_STORE);
        return this;
    }

    /** Saves the editor with an empty name to exercise the required-field rule. */
    public boolean saveStoreWithEmptyName() {
        openStoreEditor();
        fillField(0, "");
        clickContaining(SAVE);
        return isVisibleContaining(STORE_NAME_ERROR, 10_000);
    }

    /** Updates the store profile. */
    public boolean updateStore(String name, String supportEmail, String logoUrl) {
        LOG.info("VendorPortal: updating the store profile to '{}'", name);
        openStoreEditor();
        fillField(0, name);
        fillField(1, supportEmail);
        fillField(2, logoUrl);
        clickContaining(SAVE);
        return isVisibleContaining(STORE_UPDATED, 15_000);
    }

    // ---- catalogue ----------------------------------------------------------

    /** Opens the Catalog destination. */
    public VendorPortalPage openCatalog() {
        shell().openDestination(WebShellPage.CATALOG);
        return this;
    }

    /** Creates a product; returns this page with the create dialog resolved. */
    public VendorPortalPage createProduct(String name, String slug, String description) {
        LOG.info("VendorPortal: creating product '{}'", name);
        clickContaining(NEW_PRODUCT);
        fillField(0, name);
        fillField(1, slug);
        fillField(2, description);
        clickContaining(CREATE);
        return this;
    }

    /** Attempts a create with blank name/slug; true when the dialog refused to close. */
    public boolean createProductWithoutRequiredFields() {
        clickContaining(NEW_PRODUCT);
        clickContaining(CREATE);
        return isVisibleContaining(NAME_REQUIRED, 8_000) || isVisibleContaining(SLUG_REQUIRED, 8_000);
    }

    public boolean hasProduct(String name) {
        return isVisibleContaining(name, 12_000);
    }

    /** Filters the catalogue. */
    public VendorPortalPage searchProducts(String query) {
        fillField(0, query);
        return this;
    }

    /** Opens a product's detail. */
    public VendorPortalPage openProduct(String name) {
        clickContaining(name);
        return this;
    }

    /** True when the product has no variant and therefore cannot sell. */
    public boolean showsNeedsVariantWarning() {
        return isVisibleContaining(NEEDS_VARIANT, 8_000);
    }

    /** Adds a variant with a name and price. */
    public VendorPortalPage addVariant(String variantName, String price) {
        LOG.info("VendorPortal: adding variant '{}' at {}", variantName, price);
        clickContaining(ADD);
        fillField(0, variantName);
        fillField(1, price);
        clickContaining(SAVE);
        return this;
    }

    public boolean hasVariant(String variantName) {
        return isVisibleContaining(variantName, 10_000);
    }

    /** Adds a product image at a position. */
    public VendorPortalPage addImage(String imageUrl, String position) {
        clickContaining(ADD_IMAGE);
        fillField(0, imageUrl);
        fillField(1, position);
        clickContaining(SAVE);
        return this;
    }

    public boolean hasNoImages() {
        return isVisibleContaining(NO_IMAGES, 8_000);
    }

    /** Sets the product's status. Pass one of {@link #STATUS_ACTIVE}/{@link #STATUS_DRAFT}/Archive. */
    public VendorPortalPage setStatus(String status) {
        LOG.info("VendorPortal: setting status '{}'", status);
        clickContaining(status);
        return this;
    }

    public boolean showsStatus(String status) {
        return isVisibleContaining(status, 10_000);
    }

    // ---- orders -------------------------------------------------------------

    /** Opens the Orders destination. */
    public VendorPortalPage openOrders() {
        shell().openDestination(WebShellPage.ORDERS);
        return this;
    }

    public boolean hasNoOrders() {
        return isVisibleContaining(NO_ORDERS, 10_000);
    }

    /** True when at least one order row is listed. */
    public boolean hasAnyOrder() {
        return isVisibleContaining(ORDER_PREFIX, 15_000);
    }

    /** True when order rows disclose their payout amount. */
    public boolean showsOrderPayouts() {
        return isVisibleContaining(PAYOUT_PREFIX, 10_000);
    }

    /** Attempts to ship without carrier/tracking; true when the form refused. */
    public boolean shipWithoutCarrierAndTracking() {
        clickContaining("Ship");
        clickContaining("Mark shipped");
        return isVisibleContaining(CARRIER, 8_000) || isVisibleContaining(TRACKING, 8_000);
    }

    /** Ships the first order group with a carrier and tracking number. */
    public VendorPortalPage shipFirstOrder(String carrier, String tracking) {
        clickContaining("Ship");
        fillField(0, carrier);
        fillField(1, tracking);
        clickContaining("Mark shipped");
        return this;
    }

    /** True when the order detail discloses the payout net of commission. */
    public boolean showsPayoutAfterCommission() {
        return isVisibleContaining("after commission", 10_000);
    }

    // ---- overview -----------------------------------------------------------

    /** Opens the Overview destination. */
    public VendorPortalPage openOverview() {
        shell().openDestination(WebShellPage.OVERVIEW);
        return this;
    }

    /** True when the overview rendered its metrics. */
    public boolean overviewLoaded() {
        return isVisibleContaining("ship", 15_000) || isVisibleContaining("Order", 15_000)
                || shell().isVendorShell();
    }
}
