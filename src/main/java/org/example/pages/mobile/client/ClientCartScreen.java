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

    /** The subtotal as a number; -1 if it can't be read. */
    public double subtotal() {
        scrollToDesc(SUBTOTAL);
        By amount = descContains("$");
        if (!isPresent(amount, Duration.ofSeconds(5))) {
            return -1;
        }
        var element = find(amount);
        String raw = element == null ? "" : element.getAttribute("content-desc");
        return ClientBookingFlowScreen.parseAmount(raw);
    }

    public boolean showsBundleSavingsNote() {
        return isPresentAfterScroll(BUNDLE_NOTE);
    }

    // ---- quantity -----------------------------------------------------------

    /**
     * Horizontal positions of the quantity stepper inside a cart line, as fractions of the line's
     * width. Both calibrated on-device by watching the subtotal move: a 996-wide line increments at
     * 95% and decrements at 80%, vertically centred.
     *
     * <p>Coordinates are needed because the whole line — name, unit price, line total <em>and</em>
     * the quantity controls — merges into one accessibility node
     * ({@code "Argan Repair Shampoo\n$18.00 each\n$18.00\n1"}). There are no +/- elements to find.
     */
    private static final double INCREMENT_X = 0.95;
    private static final double DECREMENT_X = 0.80;
    private static final double STEPPER_Y = 0.50;

    /** Increments the quantity of the first line item. */
    public ClientCartScreen increaseFirstQuantity() {
        tapWithin(descContains(" each"), INCREMENT_X, STEPPER_Y);
        return this;
    }

    /** Decrements the quantity of the first line item. */
    public ClientCartScreen decreaseFirstQuantity() {
        tapWithin(descContains(" each"), DECREMENT_X, STEPPER_Y);
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
        scrollAndTap("Checkout");
        return this;
    }

    /** True once the shipping-address step is showing its fields. */
    public boolean isAddressStepOpen() {
        return isPresent(editText(0), Duration.ofSeconds(15));
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
