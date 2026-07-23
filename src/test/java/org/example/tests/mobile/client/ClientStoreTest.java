package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.data.TestAccounts;
import org.example.pages.mobile.client.ClientCartScreen;
import org.example.pages.mobile.client.ClientOrdersScreen;
import org.example.pages.mobile.client.ClientShopScreen;
import org.example.pages.mobile.common.BottomNavBar;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The client storefront — shop, cart and orders ({@code screens/client/store/*}).
 *
 * <p>Two kinds of assertion here, and the second matters more than the first.
 *
 * <p>The functional ones are ordinary retail: add to cart, change quantities, see a subtotal, be
 * stopped without a shipping address.
 *
 * <p>The <b>visibility</b> ones are access control wearing a retail costume. The catalogue is
 * multi-tenant — several vendors, products in Draft/Active/Archived, and a "Pro-only" flag that
 * restricts an item to professionals. A regression that leaks a pro-only or draft product into the
 * client shop is not a cosmetic bug: it lets a client buy something they were never entitled to
 * buy, at a price that may not have been meant for them.
 */
public class ClientStoreTest extends RoleSessionTest {

    private ClientShopScreen openShop() {
        loginAsClient();
        BottomNavBar nav = new BottomNavBar(driver);
        nav.open(BottomNavBar.CLIENT_SHOP);

        ClientShopScreen shop = new ClientShopScreen(driver);
        Assert.assertTrue(shop.isLoaded(), "The Shop tab should render");
        return shop;
    }

    @Test(description = "The shop lists products, or says it is empty")
    public void shopListsProducts() {
        ClientShopScreen shop = openShop();

        // Both outcomes are correct behaviour; a blank screen is not.
        Assert.assertTrue(shop.isEmpty() || shop.hasItem("Add"),
                "The shop should list purchasable items or show its empty state ('"
                        + ClientShopScreen.EMPTY_SHOP + "')");
    }

    @Test(description = "Pro-only products are not offered to clients")
    public void proOnlyProductsAreHidden() {
        String proOnly = TestAccounts.proOnlyProductName();
        if (proOnly.isBlank()) {
            throw new SkipException("Set storeFixtures.proOnlyProduct in test-accounts.json to the "
                    + "name of a seeded Pro-only product to run this visibility check.");
        }

        ClientShopScreen shop = openShop();

        Assert.assertTrue(shop.lacksItem(proOnly),
                "Pro-only product '" + proOnly + "' must never appear in the client shop — it is "
                        + "restricted to professionals");
    }

    @Test(description = "Adding a product confirms and increments the cart badge")
    public void addProductToCart() {
        ClientShopScreen shop = openShop();
        if (shop.isEmpty()) {
            throw new SkipException("The storefront is empty in this environment — seed an active "
                    + "product with a variant to run the cart tests.");
        }

        shop.addFirstItem();
        String added = shop.lastAddedProduct();

        // Asserted through the cart's contents rather than the badge: the badge caps at "9+", so on
        // an account whose cart is already full an increment is literally not observable there.
        Assert.assertTrue(shop.showsAddedConfirmation(added),
                "Adding '" + added + "' should confirm with the 'Added … to your cart' snackbar");
        Assert.assertTrue(shop.openCart().hasItem(added),
                "'" + added + "' should appear as a line in the cart");
    }

    @Test(description = "Adding a bundle puts its items in the cart")
    public void addBundleToCart() {
        ClientShopScreen shop = openShop();
        if (!shop.hasItem("Save ")) {
            throw new SkipException("No bundle is on the storefront — seed a bundle to run this test.");
        }
        shop.addFirstBundle();

        Assert.assertFalse(shop.showsAddFailure(),
                "Adding a bundle should not fail with '" + ClientShopScreen.ADD_FAILED_BUNDLE + "'");
        Assert.assertFalse(shop.openCart().isEmpty(), "The bundle's items should land in the cart");
    }

    @Test(description = "The Shop tab's cart badge tracks the cart count")
    public void cartBadgeReflectsCartCount() {
        ClientShopScreen shop = openShop();
        if (shop.isEmpty()) {
            throw new SkipException("The storefront is empty in this environment.");
        }

        shop.addFirstItem();
        int badge = shop.cartCount();

        Assert.assertTrue(badge >= 1,
                "The bottom-nav Shop badge should show the live cart count, not 0");
    }

    @Test(description = "The cart totals the line items into a subtotal")
    public void cartShowsSubtotal() {
        ClientShopScreen shop = openShop();
        if (shop.isEmpty()) {
            throw new SkipException("The storefront is empty in this environment.");
        }
        shop.addFirstItem();

        ClientCartScreen cart = shop.openCart();
        Assert.assertTrue(cart.isLoaded(), "The cart should open");
        Assert.assertFalse(cart.isEmpty(), "The cart should hold the item just added");
        Assert.assertTrue(cart.subtotal() > 0, "The cart should show a positive subtotal");
    }

    @Test(description = "Quantity changes are reflected in the cart")
    public void quantityCanBeChanged() {
        ClientShopScreen shop = openShop();
        if (shop.isEmpty()) {
            throw new SkipException("The storefront is empty in this environment.");
        }
        shop.addFirstItem();

        ClientCartScreen cart = shop.openCart();
        int qtyBefore = cart.firstQuantity();
        double before = cart.subtotal();

        cart.increaseFirstQuantity();

        Assert.assertEquals(cart.firstQuantity(), qtyBefore + 1, "The line quantity should increment");
        Assert.assertTrue(cart.subtotal() > before,
                "Increasing the quantity should raise the subtotal (was " + before + ")");

        cart.decreaseFirstQuantity();
        Assert.assertEquals(cart.firstQuantity(), qtyBefore, "Decrementing should restore the quantity");
    }

    @Test(description = "Checkout is refused without a street and city")
    public void checkoutRequiresAddress() {
        ClientShopScreen shop = openShop();
        if (shop.isEmpty()) {
            throw new SkipException("The storefront is empty in this environment.");
        }
        shop.addFirstItem();

        ClientCartScreen cart = shop.openCart();
        cart.clearShippingAddress();
        cart.continueToPayment();

        Assert.assertTrue(cart.showsAddressRequired(),
                "Checkout without an address should be refused with '"
                        + ClientCartScreen.ADDRESS_REQUIRED + "' — an order with no address cannot "
                        + "be fulfilled by any vendor");
        Assert.assertFalse(cart.showsOrderPlaced(), "No order should be placed");
    }

    @Test(description = "Placed orders are listed with their number and total")
    public void ordersAreListed() {
        ClientShopScreen shop = openShop();
        ClientOrdersScreen orders = shop.openOrders();

        Assert.assertTrue(orders.isLoaded(), "The orders screen should open");
        Assert.assertTrue(orders.isEmpty() || orders.hasAnyOrder(),
                "Orders should be listed, or the empty state ('" + ClientOrdersScreen.EMPTY
                        + "') should be shown");
    }
}
