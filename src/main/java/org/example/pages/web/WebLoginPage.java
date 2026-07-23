package org.example.pages.web;

import com.microsoft.playwright.Page;
import org.example.base.WebBasePage;

/**
 * The portal login screen — the same {@code screens/auth/loginPage.dart} the app uses, rendered
 * full-bleed by {@code main_web.dart} until a role lands in its shell.
 *
 * <p>This page carries the product's sharpest rule: <b>the browser portal is staff only</b>. A client
 * or professional who signs in here is refused by {@code WebPortalAccess.deny()}, which signs the
 * Firebase session out, clears the stored prefs and shows the "Use the Trimio app" dialog. The
 * backend refuses the same login independently (403 {@code WEB_PORTAL_NOT_AVAILABLE}), so the UI
 * message is a courtesy, not the control.
 *
 * <p>On success the role decides the destination: admin and support get
 * {@code WebShell(role:'admin')}, vendors get {@code WebShell(role:'vendor')}.
 */
public class WebLoginPage extends WebBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String BLOCKED_TITLE = "Use the Trimio app";
    public static final String BLOCKED_CLIENT_COPY = "Book and manage your";
    public static final String BLOCKED_PRO_COPY = "schedule, offers and earnings";
    public static final String FORGOT_PASSWORD = "Forgot password?";
    public static final String REGISTER = "Register";
    public static final String APPLY_TO_SELL = "Apply to sell";
    public static final String OK = "OK";

    public WebLoginPage(Page page) {
        super(page);
    }

    /** Opens the portal root. */
    public WebLoginPage open() {
        open("");
        return this;
    }

    /** True once the login form is interactive. */
    public boolean isLoaded() {
        // The submit button keeps its Semantics(label:'login_button') wrapper on web too — it is one
        // of only two explicit Semantics labels in the whole app, so it is the most stable landmark.
        return isVisible("login_button") || isVisibleContaining("Sign in");
    }

    /** Fills the credentials and submits. */
    public WebLoginPage login(String email, String password) {
        LOG.info("WebLogin: signing in as {}", email);
        fillField(0, email);
        fillField(1, password);
        submit();
        return this;
    }

    /** Submits the form. */
    public WebLoginPage submit() {
        if (isVisible("login_button", 5_000)) {
            click("login_button");
        } else {
            clickContaining("Sign in");
        }
        return this;
    }

    // ---- the staff-only gate -----------------------------------------------

    /** True when the portal refused the login with the "Use the Trimio app" dialog. */
    public boolean isBlockedFromPortal() {
        return isVisibleContaining(BLOCKED_TITLE, 20_000);
    }

    /** True when the refusal used the professional-specific copy. */
    public boolean showsProfessionalBlockCopy() {
        return isVisibleContaining(BLOCKED_PRO_COPY, 10_000);
    }

    /** True when the refusal used the client-specific copy. */
    public boolean showsClientBlockCopy() {
        return isVisibleContaining(BLOCKED_CLIENT_COPY, 10_000);
    }

    /** Dismisses the refusal dialog. */
    public WebLoginPage acknowledgeBlock() {
        click(OK);
        return this;
    }

    /**
     * True when no usable session survived a refused login — the half of the rule that matters.
     * {@code WebPortalAccess.deny()} signs Firebase out and removes userId/userType/userPhone/
     * userEmail/vendor_id, so after a reload we must be back on the login form.
     */
    public boolean leftNoSessionBehind() {
        page.reload();
        waitForFlutter();
        enableSemantics();
        return isLoaded();
    }

    // ---- navigation ---------------------------------------------------------

    /** Opens the public vendor application ("Want to sell on Trimio? Apply to sell"). */
    public VendorApplyPage openVendorApplication() {
        clickContaining(APPLY_TO_SELL);
        return new VendorApplyPage(page);
    }

    /** Returns the shell that a successful sign-in should have produced. */
    public WebShellPage shell() {
        return new WebShellPage(page);
    }
}
