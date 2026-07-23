package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The client's store orders — {@code screens/client/store/client_store_orders_screen.dart}.
 *
 * <p>Lists "Order #&lt;id&gt;" rows with the placed date, total and any bundle saving, and opens a
 * detail sheet that groups the items <em>per vendor</em> with each group's fulfilment status. The
 * per-vendor grouping is the client-visible half of the marketplace split: one order, several
 * shipments, several payouts.
 */
public class ClientOrdersScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String TITLE = "Your orders";
    public static final String EMPTY = "No orders yet";
    public static final String EMPTY_SUB = "Products you buy will show up here.";
    public static final String LOAD_FAILED = "Could not load this order.";
    public static final String SHIPPING_TO = "Shipping to";

    public ClientOrdersScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(accId(TITLE), Duration.ofSeconds(25));
    }

    public boolean isEmpty() {
        return isPresent(descContains(EMPTY), Duration.ofSeconds(10));
    }

    /** True if at least one "Order #" row is listed. */
    public boolean hasAnyOrder() {
        return isPresentAfterScroll("Order #");
    }

    /** True if the given order number is listed. */
    public boolean hasOrder(int orderId) {
        return isPresentAfterScroll("Order #" + orderId);
    }

    /** Opens the first order in the list. */
    public ClientOrdersScreen openFirstOrder() {
        scrollAndTap("Order #");
        return this;
    }

    /** Opens a specific order. */
    public ClientOrdersScreen openOrder(int orderId) {
        scrollAndTap("Order #" + orderId);
        return this;
    }

    /** True when the detail sheet has rendered the shipping section. */
    public boolean detailShowsShippingAddress() {
        return isPresentAfterScroll(SHIPPING_TO);
    }

    /** True when the detail sheet lists a group shipped by the named vendor. */
    public boolean detailShowsVendor(String vendorName) {
        return isPresentAfterScroll(vendorName);
    }

    /** True when the detail lists a bundle saving line. */
    public boolean showsSavings() {
        return isPresentAfterScroll("You saved");
    }

    public boolean showsLoadFailure() {
        return isPresent(descContains(LOAD_FAILED), Duration.ofSeconds(15));
    }
}
