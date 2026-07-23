package org.example.pages.mobile.professional;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The professional store and its cart — {@code screens/professional/store/professional_store.dart}
 * and {@code store_cart_screen.dart}.
 *
 * <p>Same marketplace as the client shop, but this surface additionally sells <em>Pro-only</em>
 * products, and it carries product reviews gated on purchase: a professional who has not bought the
 * item is told "Only verified purchasers can leave a review." That gate is the reason this screen is
 * worth automating — it is the only place in the app where review eligibility depends on order
 * history.
 */
public class ProfessionalStoreScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String PRO_ONLY = "Pro-only";
    public static final String RATE_PRODUCT = "Rate this product";
    public static final String PURCHASERS_ONLY = "Only verified purchasers can leave a review.";
    public static final String NO_REVIEWS = "Be the first to review this product.";
    public static final String REVIEW_TITLE_HINT = "Title (optional)";
    public static final String REVIEW_BODY_HINT = "Share how it worked for you (optional)";

    // ---- cart ---------------------------------------------------------------
    public static final String CART_TITLE = "Your cart";
    public static final String CART_EMPTY = "Your cart is empty";
    public static final String BROWSE_STORE = "Browse the store";
    public static final String SUBTOTAL = "Subtotal";
    public static final String ESTIMATED_TOTAL = "Estimated total";
    public static final String SHIPPING_NOTE = "Vendors ship your order to this address.";
    public static final String ORDER_PLACED = "Order placed";

    /** App-bar title, verified on-device. Note it is NOT the bare word "Store". */
    public static final String TITLE = "Professional Store";
    public static final String SHOP_ESSENTIALS = "Shop essentials";
    public static final String RECOMMENDED = "Recommended for you";

    public ProfessionalStoreScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(TITLE), Duration.ofSeconds(25))
                || isPresentAfterScroll(SHOP_ESSENTIALS);
    }

    /** True if the catalogue includes at least one pro-only product. */
    public boolean showsProOnlyProducts() {
        return isPresentAfterScroll(PRO_ONLY);
    }

    /**
     * True if a product with this name is on the storefront.
     *
     * <p>Product cards merge into a <em>single</em> semantics node — an on-device dump showed
     * {@code "Titan Shears\nAndis\n5.0 (1)\n$179"} for one card — so the name is only ever a
     * substring of a larger label, never a node of its own.
     */
    public boolean hasProduct(String name) {
        return isPresentAfterScroll(name);
    }

    /**
     * Opens the first recommended product and adds it to the cart.
     *
     * <p>The storefront has no per-card "Add" button: verified on-device, the browse screen renders
     * merged product cards and the add action lives on the product detail. So this navigates first
     * rather than tapping an "Add" that does not exist at this level.
     */
    public ProfessionalStoreScreen addFirstProduct() {
        scrollToDesc(RECOMMENDED);
        tap(descContains("$"));
        if (isPresent(accId("Add"), SHORT_TIMEOUT)) {
            tap(accId("Add"));
        } else {
            scrollAndTap("Add to cart");
        }
        return this;
    }

    /** Opens a product's detail by name. */
    public ProfessionalStoreScreen openProduct(String name) {
        scrollAndTap(name);
        return this;
    }

    // ---- reviews ------------------------------------------------------------

    /** True when the purchase gate is blocking a review. */
    public boolean showsPurchasersOnlyGate() {
        return isPresentAfterScroll(PURCHASERS_ONLY);
    }

    /** True when the review form is offered (i.e. the professional bought the item). */
    public boolean canReview() {
        return isPresentAfterScroll(RATE_PRODUCT) && !showsPurchasersOnlyGate();
    }

    public boolean showsNoReviewsYet() {
        return isPresentAfterScroll(NO_REVIEWS);
    }

    /** Leaves a product review. */
    public ProfessionalStoreScreen reviewProduct(int stars, String title, String body) {
        scrollAndTap(RATE_PRODUCT);
        tap(accId(String.valueOf(stars)));
        type(editText(0), title);
        type(editText(1), body);
        hideKeyboard();
        scrollAndTap("Submit");
        return this;
    }

    // ---- cart ---------------------------------------------------------------

    /** Opens the professional store cart. */
    public ProfessionalStoreScreen openCart() {
        scrollAndTap(CART_TITLE);
        return this;
    }

    public boolean cartIsEmpty() {
        return isPresent(descContains(CART_EMPTY), Duration.ofSeconds(10));
    }

    /** True when the cart states its item count ("<n> item(s)"). */
    public boolean cartShowsItemCount() {
        return isPresentAfterScroll("item");
    }

    public boolean cartShowsSubtotal() {
        return isPresentAfterScroll(SUBTOTAL);
    }

    public boolean cartShowsEstimatedTotal() {
        return isPresentAfterScroll(ESTIMATED_TOTAL);
    }

    /** True when the per-vendor shipping explanation is shown. */
    public boolean cartShowsVendorShippingNote() {
        return isPresentAfterScroll(SHIPPING_NOTE) || isPresentAfterScroll("Shipped by");
    }

    /** Fills the shipping address and starts checkout. */
    public ProfessionalStoreScreen checkout(String street, String city) {
        scrollToDesc("Shipping address");
        type(editText(0), street);
        type(editText(1), city);
        hideKeyboard();
        scrollAndTap("Continue to payment");
        return this;
    }

    public boolean showsOrderPlaced() {
        return isPresent(descContains(ORDER_PLACED), Duration.ofSeconds(60));
    }
}
