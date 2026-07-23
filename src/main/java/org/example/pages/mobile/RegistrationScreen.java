package org.example.pages.mobile;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Registration form (registrationPage.dart) — REDESIGNED. Reached via
 * Onboarding → "Get started" → role → registration form.
 *
 * <p>Verified field order is unchanged (email/phone/password), but the screen now adds a
 * required Terms checkbox and full client-side validation on every field:
 * <ul>
 *   <li>Email    — 1st {@code EditText} (hint "Email address")</li>
 *   <li>Phone    — 2nd {@code EditText} (hint "Phone number", auto-formatted to "(305) 555-1234")</li>
 *   <li>Password — 3rd {@code EditText} (hint "Password", visibility toggle)</li>
 *   <li>Terms    — a {@code Checkbox} (located via {@code checkable(true)}) that must be ticked</li>
 *   <li>Submit   — content-desc "Create account"; Title — content-desc "Create your account"</li>
 * </ul>
 *
 * <p>Validation (utils/validators.dart, {@code AutovalidateMode.onUserInteraction}):
 * <ul>
 *   <li>email   — empty → "Please enter your email"; bad → "Please enter a valid email address"</li>
 *   <li>phone   — empty → "Please enter your phone number"; not 10 digits → "Phone number must be 10 digits"</li>
 *   <li>password (strong policy) — empty → "Please enter your password"; &lt;8 → "...at least 8 characters long";
 *       missing class → "...at least one uppercase/lowercase letter / one digit / one special character";
 *       sequence → "Password cannot contain sequential numbers like '123'"</li>
 *   <li>terms not ticked → snackbar "Please accept Trimio's Terms and Privacy Policy to continue."</li>
 *   <li>Firebase (only reached once client validation passes): existing email →
 *       "This email is already in use." Success → "Registration successful!"</li>
 * </ul>
 *
 * <p>NOTE: the previous generic password "Trimio@1234" is now REJECTED client-side because it
 * contains the sequence "123"/"234" — registration data must use a policy-compliant password.
 * Derived from source; not yet re-verified on-device.
 */
public class RegistrationScreen extends MobileBasePage {

    // ---- locators -----------------------------------------------------------
    // The BrandHeader title Text MERGES with its subtitle into one content-desc
    // (Flutter Semantics merge), so match by substring rather than exact id.
    private final By title = descContains("Create your account");
    private final By emailField = editText(0);
    private final By phoneField = editText(1);
    private final By passwordField = editText(2);
    private final By termsCheckbox = AppiumBy.androidUIAutomator("new UiSelector().checkable(true)");
    private final By createAccountButton = accId("Create account");

    // ---- client-side validation messages -----------------------------------
    public static final String EMAIL_REQUIRED = "Please enter your email";
    public static final String EMAIL_INVALID = "Please enter a valid email address";
    public static final String PHONE_REQUIRED = "Please enter your phone number";
    public static final String PHONE_INVALID = "Phone number must be 10 digits";
    public static final String PASSWORD_REQUIRED = "Please enter your password";
    public static final String PASSWORD_TOO_SHORT = "Password must be at least 8 characters long";
    public static final String PASSWORD_NO_UPPER = "Password must include at least one uppercase letter";
    public static final String PASSWORD_NO_LOWER = "Password must include at least one lowercase letter";
    public static final String PASSWORD_NO_DIGIT = "Password must include at least one digit";
    public static final String PASSWORD_NO_SPECIAL = "Password must include at least one special character";
    public static final String PASSWORD_SEQUENTIAL = "Password cannot contain sequential numbers like '123'";
    // The apostrophe is a curly U+2019, exactly as rendered by the app.
    public static final String TERMS_REQUIRED =
            "Please accept Trimio’s Terms and Privacy Policy to continue.";

    // ---- backend (Firebase) outcome messages -------------------------------
    public static final String EMAIL_IN_USE = "This email is already in use.";
    public static final String WEAK_PASSWORD = "The password is too weak.";
    public static final String GENERIC_ERROR = "An error occurred. Please try again.";
    public static final String SUCCESS = "Registration successful!";

    public RegistrationScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(title);
    }

    // ---- field actions ------------------------------------------------------

    public RegistrationScreen enterEmail(String email) {
        if (email != null && !email.isEmpty()) type(emailField, email);
        return this;
    }

    public RegistrationScreen enterPhone(String phone) {
        if (phone != null && !phone.isEmpty()) type(phoneField, phone);
        return this;
    }

    public RegistrationScreen enterPassword(String password) {
        if (password != null && !password.isEmpty()) type(passwordField, password);
        return this;
    }

    /** Ticks the "I agree to Trimio's Terms and Privacy Policy" checkbox. */
    public RegistrationScreen acceptTerms() {
        LOG.info("Registration: accepting terms");
        tap(termsCheckbox);
        return this;
    }

    /** Taps "Create account". Center tap lands on it even when the keyboard overlaps. */
    public RegistrationScreen tapCreateAccount() {
        LOG.info("Registration: tapping Create account");
        tap(createAccountButton);
        return this;
    }

    /** Fills the three fields (skipping blank values) — does NOT touch the terms checkbox. */
    public RegistrationScreen fillForm(String email, String phone, String password) {
        enterEmail(email);
        enterPhone(phone);
        enterPassword(password);
        return this;
    }

    /** Fills the form, accepts the terms, and submits (the happy-path helper). */
    public RegistrationScreen register(String email, String phone, String password) {
        fillForm(email, phone, password);
        // The Terms checkbox + Create button sit below the keyboard; close it so they're
        // present in the accessibility tree.
        hideKeyboard();
        acceptTerms();
        return tapCreateAccount();
    }

    /** Fills the form and submits WITHOUT ticking terms — drives the terms-required check. */
    public RegistrationScreen registerWithoutTerms(String email, String phone, String password) {
        fillForm(email, phone, password);
        hideKeyboard();
        return tapCreateAccount();
    }

    // ---- assertions / outcomes ---------------------------------------------

    /** True if the given outcome message (field error or snackbar) appears within {@code seconds}. */
    public boolean isMessageShown(String message, int seconds) {
        return isPresent(accId(message), Duration.ofSeconds(seconds));
    }

    public boolean isRegistrationSuccessful() {
        return isMessageShown(SUCCESS, 25);
    }

    /** True while still on the registration form (used to assert we did NOT proceed). */
    public boolean isStillOnForm() {
        return !isAbsent(createAccountButton);
    }
}
