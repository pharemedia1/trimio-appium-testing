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
     * Fractions locating the "Add" chip inside a product card, calibrated on-device.
     *
     * <p><b>There is no "Add" element to click.</b> Flutter merges each product card into a single
     * accessibility node — {@code "Argan Repair Shampoo\nHair Care\n$18\nAdd"} — so
     * {@code accessibilityId("Add")} matches nothing and times out. The chip has to be hit by
     * position within the card.
     *
     * <p>Calibrated by measurement, not by guessing: on a 996×226 product row the card centre and
     * the bottom-right corner (80%, 90%) both did nothing, while the right edge at ~70% height added
     * the item and produced a cart subtotal. Re-measure if the storefront layout changes.
     */
    private static final double ADD_X_FRACTION = 0.92;
    private static final double ADD_Y_FRACTION = 0.70;

    /**
     * Adds the first take-home <em>product</em> to the cart.
     *
     * <p>Skips bundle tiles deliberately. Bundles and products share the word "Add" in their merged
     * label but have different geometry — measured on-device, a bundle is a 609×478 tile while a
     * product is a 996×226 row — so the product fractions land nowhere on a bundle. A bundle's desc
     * always contains "Save $", which is what distinguishes the two.
     */
    public ClientShopScreen addFirstItem() {
        LOG.info("Shop: adding the first product");
        scrollToDesc(SECTION_PRODUCTS);
        for (org.openqa.selenium.WebElement card : driver.findElements(descContains("Add"))) {
            String label = card.getAttribute("content-desc");
            if (label != null && !label.contains("Save ")) {
                tapWithin(card, ADD_X_FRACTION, ADD_Y_FRACTION);
                return this;
            }
        }
        throw new IllegalStateException("No take-home product card found on the storefront");
    }

    /**
     * Scrolls to a named product card and taps the "Add" chip inside it.
     *
     * <p>Products only — see {@link #BUNDLE_ADD_UNCALIBRATED} for why bundles are not supported here.
     */
    public ClientShopScreen addItem(String name) {
        LOG.info("Shop: adding '{}'", name);
        scrollToDesc(name);
        tapWithin(descContains(name), ADD_X_FRACTION, ADD_Y_FRACTION);
        return this;
    }

    /**
     * Why bundles cannot be added by this page object yet.
     *
     * <p>A bundle tile merges into one accessibility node exactly like a product row, so its "Add"
     * control has no element to click and must be hit positionally. Unlike the product row — where
     * the chip was located by measurement (right edge, ~70% height, confirmed by the subtotal
     * changing) — three candidate positions on the bundle tile (bottom-right at 80%/90% and 85%/92%,
     * and bottom-centre) all produced no cart change on-device.
     *
     * <p>Rather than ship a guessed coordinate that silently does nothing, the bundle test skips with
     * this reason. Calibrating it needs a screenshot of the tile to see where the chip actually sits;
     * the accessibility tree cannot reveal it.
     */
    public static final String BUNDLE_ADD_UNCALIBRATED =
            "The bundle tile's 'Add' control has no accessibility node and its position is not yet "
                    + "calibrated — three measured candidates produced no cart change on-device. "
                    + "Product adds are calibrated and covered; see ClientShopScreen.";

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
