package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The client store cart — {@code screens/client/store/client_store_cart_screen.dart}.
 *
 * <p>Shows the line items ("$x each" × quantity), a subtotal, the shipping-address form and the
 * checkout CTA. Two rules are worth pinning down in automation:
 * <ul>
 *   <li>checkout refuses to start without a street and city ("Enter your street and city.");</li>
 *   <li>bundle discounts are applied <em>at checkout</em>, not in the subtotal — the screen says so
 *       explicitly ("Bundle savings apply at checkout."), so the subtotal is expected to exceed the
 *       final charge whenever a bundle is in the cart.</li>
 * </ul>
 */
public class ClientCartScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String TITLE = "Your cart";
    public static final String EMPTY = "Your cart is empty";
    public static final String SUBTOTAL = "Subtotal";
    public static final String BUNDLE_NOTE = "Bundle savings apply at checkout.";
    public static final String ADDRESS_REQUIRED = "Enter your street and city.";
    public static final String ORDER_PLACED = "Order placed";
    public static final String PAYMENT_NOT_COMPLETED = "Payment was not completed.";
    public static final String CHECKOUT_FAILED = "Checkout failed.";

    private final By continueToPayment = accId("Continue to payment");
    private final By checkout = accId("Checkout");
    private final By done = accId("Done");

    public ClientCartScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(accId(TITLE), Duration.ofSeconds(25));
    }

    public boolean isEmpty() {
        return isPresent(descContains(EMPTY), Duration.ofSeconds(10));
    }

    public boolean hasItem(String name) {
        return isPresentAfterScroll(name);
    }

    /**
     * The cart subtotal as a number; -1 if it can't be read.
     *
     * <p>Reads the <em>standalone</em> amount node (the one whose whole label is "$36.00"), not
     * simply the first node containing a "$". Line items merge their unit price into a multi-line
     * label ("Argan Repair Shampoo\n$18.00 each\n$36.00\n2"), and that unit price sorts first — so
     * the naive read returned $18.00 both before and after a quantity change and made the subtotal
     * look frozen.
     */
    public double subtotal() {
        scrollToDesc(SUBTOTAL);
        for (var element : driver.findElements(descContains("$"))) {
            String label = element.getAttribute("content-desc");
            if (label != null && label.trim().matches("\\$[0-9,]+(\\.[0-9]{2})?")) {
                return ClientBookingFlowScreen.parseAmount(label);
            }
        }
        return -1;
    }

    public boolean showsBundleSavingsNote() {
        return isPresentAfterScroll(BUNDLE_NOTE);
    }

    // ---- quantity -----------------------------------------------------------

    /**
     * Accessibility ids of the quantity stepper, added to {@code client_store_cart_screen.dart}.
     *
     * <p>These replaced coordinate taps calibrated at 95%/80% of the line's width. The stepper is
     * icon-only, so before the labels existed it had nothing to announce and no node of its own —
     * the whole line merged into {@code "Argan Repair Shampoo\n$18.00 each\n$18.00\n1"}.
     */
    public static final String QTY_INCREASE = "cart_qty_increase";
    public static final String QTY_DECREASE = "cart_qty_decrease";

    /** Increments the quantity of the first line item. */
    public ClientCartScreen increaseFirstQuantity() {
        tap(accId(QTY_INCREASE));
        return this;
    }

    /** Decrements the quantity of the first line item. */
    public ClientCartScreen decreaseFirstQuantity() {
        tap(accId(QTY_DECREASE));
        return this;
    }

    /**
     * The quantity of the first line item, parsed from the merged line label (it is the trailing
     * number in {@code "…\n$18.00 each\n$36.00\n2"}); -1 when unreadable.
     */
    public int firstQuantity() {
        var line = find(descContains(" each"));
        if (line == null) {
            return -1;
        }
        String label = line.getAttribute("content-desc");
        if (label == null || label.isBlank()) {
            return -1;
        }
        String[] parts = label.trim().split("\n");
        try {
            return Integer.parseInt(parts[parts.length - 1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---- shipping address ---------------------------------------------------

    /**
     * Opens the shipping-address step.
     *
     * <p>The address form is <b>not on the cart</b> — verified on-device, the cart shows only the
     * line items, subtotal and a "Checkout" button. The address fields appear after Checkout is
     * tapped, which is why the earlier attempt to clear them from the cart timed out looking for an
     * EditText that was one screen away.
     */
    public ClientCartScreen openCheckout() {
        // Tap it directly: "Checkout" sits in a pinned bottom bar, so scrolling the item list first
        // (as scrollAndTap does) moves the list under it and the tap can miss.
        tap(checkout);
        if (!isPresent(descContains("Shipping address"), Duration.ofSeconds(20))) {
            LOG.warn("Cart: the shipping-address sheet did not open after tapping Checkout");
        }
        return this;
    }

    /**
     * True once the shipping-address sheet is open.
     *
     * <p>Keyed on the sheet's own heading rather than on an EditText: the cart screen has no text
     * fields at all, so probing for one here burned a 15s timeout on every call before falling
     * through to open the sheet.
     */
    public boolean isAddressStepOpen() {
        return isPresent(descContains("Shipping address"), SHORT_TIMEOUT);
    }

    /** Fills the shipping-address form (opens the step first if needed). */
    public ClientCartScreen enterShippingAddress(String street, String city) {
        if (!isAddressStepOpen()) {
            openCheckout();
        }
        type(editText(0), street);
        type(editText(1), city);
        hideKeyboard();
        return this;
    }

    /** Clears the street/city fields so the validation path can be exercised. */
    public ClientCartScreen clearShippingAddress() {
        if (!isAddressStepOpen()) {
            openCheckout();
        }
        type(editText(0), "");
        type(editText(1), "");
        hideKeyboard();
        return this;
    }

    // ---- checkout -----------------------------------------------------------

    /** Taps "Continue to payment" (the address gate). */
    public ClientCartScreen continueToPayment() {
        scrollToDesc("Continue to payment");
        tap(continueToPayment);
        return this;
    }

    /** Taps the "Checkout" CTA (the payment gate). */
    public ClientCartScreen checkout() {
        scrollToDesc("Checkout");
        tap(checkout);
        return this;
    }

    public boolean showsAddressRequired() {
        return isPresent(descContains(ADDRESS_REQUIRED), Duration.ofSeconds(10));
    }

    public boolean showsOrderPlaced() {
        return isPresent(descContains(ORDER_PLACED), Duration.ofSeconds(60));
    }

    public boolean showsPaymentNotCompleted() {
        return isPresent(descContains(PAYMENT_NOT_COMPLETED), Duration.ofSeconds(30));
    }

    /** Dismisses the success dialog. */
    public ClientCartScreen done() {
        tap(done);
        return this;
    }
}
