package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Price overrides — {@code screens/Admin/AdminOverride/pricing_admin_api.dart} (the list) and
 * {@code edit_override_page.dart} (the editor).
 *
 * <p>An override replaces what a client pays: either a fixed price or a multiplier, optionally
 * scoped to one service and/or pro level, and bounded by effective dates. It is the highest-leverage
 * screen in the console — one row here changes the price every matching client sees — which is why
 * both mandatory fields ("Service *", "Reason *") and the delete confirmation matter.
 */
public class AdminPricingScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String NEW = "New";
    public static final String EDIT = "Edit";
    public static final String DELETE = "Delete";
    public static final String DELETE_CONFIRM = "Delete override?";
    public static final String CANCEL = "Cancel";
    public static final String SAVE = "Save";
    public static final String SERVICE_REQUIRED = "Service *";
    public static final String REASON_REQUIRED = "Reason *";
    public static final String FIXED_PRICE = "Fixed price (\\$)";
    public static final String MULTIPLIER = "Multiplier";
    public static final String PRO_LEVEL = "Pro level";
    public static final String SCOPE = "Scope";
    public static final String EFFECTIVE_DATES = "Effective dates";
    public static final String EFFECTIVE_FROM = "Effective from";
    public static final String EFFECTIVE_TO = "Effective to";
    public static final String ANY = "— Any —";

    public AdminPricingScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(accId(NEW), Duration.ofSeconds(25))
                || isPresentAfterScroll("override");
    }

    /** Opens the create-override editor. */
    public AdminPricingScreen createNew() {
        tap(accId(NEW));
        return this;
    }

    /** True once the editor is open (its Pricing/Scope sections are rendered). */
    public boolean editorIsOpen() {
        return isPresentAfterScroll("Pricing") || isPresentAfterScroll(SCOPE);
    }

    /** Picks the service the override applies to. */
    public AdminPricingScreen selectService(String serviceName) {
        scrollAndTap("Service");
        scrollAndTap(serviceName);
        return this;
    }

    /** Sets a fixed price (the first numeric field under Pricing). */
    public AdminPricingScreen setFixedPrice(String amount) {
        scrollToDesc("Fixed price");
        type(editText(0), amount);
        hideKeyboard();
        return this;
    }

    /** Sets a multiplier instead of a fixed price. */
    public AdminPricingScreen setMultiplier(String multiplier) {
        scrollToDesc(MULTIPLIER);
        type(editText(0), multiplier);
        hideKeyboard();
        return this;
    }

    /** Fills the mandatory reason. */
    public AdminPricingScreen setReason(String reason) {
        scrollToDesc("Reason");
        type(editText(1), reason);
        hideKeyboard();
        return this;
    }

    /** Saves the override. */
    public AdminPricingScreen save() {
        scrollAndTap(SAVE);
        return this;
    }

    /** True when the editor is still open — i.e. a mandatory field blocked the save. */
    public boolean saveWasBlocked() {
        return editorIsOpen();
    }

    /** True if an override row mentioning {@code text} is listed. */
    public boolean hasOverride(String text) {
        return isPresentAfterScroll(text);
    }

    /** Deletes the first override, confirming the prompt. */
    public AdminPricingScreen deleteFirst() {
        scrollAndTap(DELETE);
        if (isPresent(descContains(DELETE_CONFIRM), SHORT_TIMEOUT)) {
            tap(accId(DELETE));
        }
        return this;
    }

    public boolean showsDeleteConfirmation() {
        return isPresent(descContains(DELETE_CONFIRM), Duration.ofSeconds(10));
    }
}
