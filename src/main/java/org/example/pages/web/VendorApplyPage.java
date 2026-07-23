package org.example.pages.web;

import com.microsoft.playwright.Page;
import org.example.base.WebBasePage;

/**
 * The public "Sell on Trimio" application — {@code screens/vendor/vendor_apply_page.dart}, reached
 * from the login screen's "Want to sell on Trimio? Apply to sell" link.
 *
 * <p>The only unauthenticated write path in the portal ({@code POST /store/apply}), which makes it
 * the one place where input validation is a security control rather than a convenience: everything
 * submitted here lands in an admin queue and is read by a human, so unvalidated free text is a
 * stored-XSS vector into the admin console.
 */
public class VendorApplyPage extends WebBasePage {

    public static final String TITLE = "Sell on Trimio";
    public static final String SUBMIT = "Submit application";
    public static final String RECEIVED = "Application received";
    public static final String BACK_TO_SIGN_IN = "Back to sign in";

    public VendorApplyPage(Page page) {
        super(page);
    }

    /** True once the application form has rendered. */
    public boolean isLoaded() {
        return isVisibleContaining(TITLE, 25_000) || isVisibleContaining(SUBMIT, 10_000);
    }

    /**
     * Fills the application. Fields are addressed positionally because the form's inputs carry
     * labels but Flutter only materialises the DOM input for the focused field — the ordering
     * follows the form: store name, contact email, then the remaining details.
     */
    public VendorApplyPage fill(String storeName, String email, String details) {
        LOG.info("VendorApply: applying as '{}'", storeName);
        fillField(0, storeName);
        fillField(1, email);
        fillField(2, details);
        return this;
    }

    /** Submits the application. */
    public VendorApplyPage submit() {
        clickContaining(SUBMIT);
        return this;
    }

    /** True when the application was accepted. */
    public boolean showsReceived() {
        return isVisibleContaining(RECEIVED, 25_000);
    }

    /** True when the form is still on screen — i.e. validation blocked the submit. */
    public boolean stillOnForm() {
        return isVisibleContaining(SUBMIT, 8_000);
    }

    /** Returns to the login screen. */
    public WebLoginPage backToSignIn() {
        clickContaining(BACK_TO_SIGN_IN);
        return new WebLoginPage(page);
    }
}
