package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.example.pages.mobile.common.BottomNavBar;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The client Shop tab — {@code screens/client/store/client_shop_screen.dart}.
 *
 * <p>A storefront of bundles (each showing "Save $x") and individual take-home products. Adding
 * either produces a confirmation snackbar and bumps the cart count shown both in the app bar and on
 * the bottom-nav Shop badge.
 *
 * <p>The visibility rules matter more than the happy path: products flagged <em>Pro-only</em>, and
 * anything in Draft or Archived status, must never appear here. Those are the assertions in
 * {@code ClientStoreTest} that protect the marketplace's access model.
 */
public class ClientShopScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String TITLE = "Shop";
    public static final String EMPTY_SHOP = "The shop is empty right now";
    public static final String EMPTY_SHOP_SUB = "Check back soon for take-home products.";
    public static final String ADD_FAILED_PRODUCT = "Could not add to cart";
    public static final String ADD_FAILED_BUNDLE = "Could not add the bundle";

    /** Section headers, verified on-device. */
    public static final String SECTION_BUNDLES = "Save with bundles";
    public static final String SECTION_PRODUCTS = "Take-home products";

    private final By openCart = accId("Your cart");
    private final By openOrders = accId("Your orders");

    public ClientShopScreen(AndroidDriver driver) {
        super(driver);
    }

    public BottomNavBar nav() {
        return new BottomNavBar(driver);
    }

    public boolean isLoaded() {
        return isPresent(accId(TITLE), Duration.ofSeconds(25));
    }

    public boolean isEmpty() {
        return isPresent(descContains(EMPTY_SHOP), Duration.ofSeconds(10));
    }

    /** True if a product/bundle with this name is on the storefront (scrolling to find it). */
    public boolean hasItem(String name) {
        return isPresentAfterScroll(name);
    }

    /** True if the item is NOT on the storefront — the check behind the pro-only/draft rules. */
    public boolean lacksItem(String name) {
        return !hasItem(name);
    }

    /**
     * Accessibility ids of the storefront's add buttons, added to {@code client_shop_screen.dart}.
     *
     * <p>These replaced coordinate taps. Previously each card merged into one semantics node
     * ({@code "Argan Repair Shampoo\nHair Care\n$18\nAdd"}) and the buttons had to be hit by
     * position — which was brittle for products and never calibrated at all for bundle tiles, whose
     * geometry differs. Wrapping each button in {@code Semantics(container: true)} gives it a node
     * of its own, so it can simply be located and tapped. It also makes the buttons usable with a
     * screen reader, which they were not before.
     */
    public static final String ADD_PRODUCT_PREFIX = "add_to_cart";
    public static final String ADD_BUNDLE_PREFIX = "add_bundle_to_cart";

    /** Name of the product most recently added by {@link #addFirstItem()}. */
    private String lastAdded = "";

    /** The product {@link #addFirstItem()} added, so a test can assert it reached the cart. */
    public String lastAddedProduct() {
        return lastAdded;
    }

    /**
     * Adds the first take-home product to the cart and waits for the app to confirm it.
     *
     * <p>The wait is not cosmetic: adding is a server round-trip, so reading the cart or the badge
     * straight after the tap sees the pre-add state. The app's own "Added … to your cart" snackbar
     * is the signal that the round-trip finished.
     */
    public ClientShopScreen addFirstItem() {
        scrollToDesc(SECTION_PRODUCTS);
        var button = find(descContains(ADD_PRODUCT_PREFIX));
        String label = button == null ? "" : button.getAttribute("content-desc");
        lastAdded = label == null ? "" : label.replace(ADD_PRODUCT_PREFIX, "").split("\n")[0].trim();
        LOG.info("Shop: adding product '{}'", lastAdded);

        tap(descContains(ADD_PRODUCT_PREFIX));
        if (!isPresent(descContains("to your cart"), Duration.ofSeconds(20))) {
            LOG.warn("Shop: no add confirmation appeared for '{}'", lastAdded);
        }
        return this;
    }

    /** Adds a named product to the cart. */
    public ClientShopScreen addItem(String name) {
        LOG.info("Shop: adding product '{}'", name);
        scrollToDesc(name);
        tap(descContains(ADD_PRODUCT_PREFIX + " " + name));
        return this;
    }

    /** Adds the first bundle to the cart. */
    public ClientShopScreen addFirstBundle() {
        LOG.info("Shop: adding the first bundle");
        scrollToDesc(SECTION_BUNDLES);
        tap(descContains(ADD_BUNDLE_PREFIX));
        isPresent(descContains("to your cart"), Duration.ofSeconds(20));
        return this;
    }

    /** Adds a named bundle to the cart. */
    public ClientShopScreen addBundle(String name) {
        LOG.info("Shop: adding bundle '{}'", name);
        scrollToDesc(name);
        tap(descContains(ADD_BUNDLE_PREFIX + " " + name));
        return this;
    }

    /**
     * True if the "Added “<name>” to your cart" snackbar appeared. Matching is on the quoted name
     * only, because the source uses typographic quotes (U+201C/U+201D) that are awkward to type.
     */
    public boolean showsAddedConfirmation(String name) {
        return isPresent(descContains("to your cart"), Duration.ofSeconds(15))
                || isPresent(descContains(name), Duration.ofSeconds(5));
    }

    public boolean showsAddFailure() {
        return isPresent(descContains(ADD_FAILED_PRODUCT), Duration.ofSeconds(10))
                || isPresent(descContains(ADD_FAILED_BUNDLE), Duration.ofSeconds(10));
    }

    /** The count on the app-bar cart chip, or 0 when no badge is drawn. */
    public int cartCount() {
        return nav().cartBadgeCount();
    }

    public ClientCartScreen openCart() {
        tap(openCart);
        return new ClientCartScreen(driver);
    }

    public ClientOrdersScreen openOrders() {
        tap(openOrders);
        return new ClientOrdersScreen(driver);
    }
}
