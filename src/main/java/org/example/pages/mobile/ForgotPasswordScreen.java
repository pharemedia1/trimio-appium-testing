package org.example.pages.mobile;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Forgot-password form (forgetPasswordPage.dart) — REDESIGNED. Reached via
 * Onboarding → "Sign in" → "Forgot password?" → role → ForgotPasswordPage.
 *
 * <p>Selectors (derived from the redesigned Dart source):
 * <ul>
 *   <li>Title  — {@code BrandHeader "Forgot password?"} (MERGES with its subtitle, so match by
 *       substring)</li>
 *   <li>Email  — 1st {@code EditText} (hint "Email address")</li>
 *   <li>Phone  — 2nd {@code EditText} (hint "Phone number")</li>
 *   <li>Submit — {@code PrimaryButton} "Send reset code" (was "Reset Password")</li>
 * </ul>
 *
 * <p>Validation is driven by the button handler and surfaces as plain {@code AppSnackBar} text
 * (exact content-desc) — the messages are unchanged by the redesign:
 * <ul>
 *   <li>both fields empty → "Please enter your email or phone number."</li>
 *   <li>both fields filled → "Please enter either an email OR a phone number, not both."</li>
 *   <li>bad email format → "Please enter a valid email address."</li>
 *   <li>phone not 10 digits → "Please enter a valid 10-digit phone number."</li>
 *   <li>unknown email → backend "User not found."</li>
 *   <li>known email → success snackbar, then navigates to the OTP page (form is left).</li>
 * </ul>
 */
public class ForgotPasswordScreen extends MobileBasePage {

    // ---- locators -----------------------------------------------------------
    // BrandHeader title MERGES with its subtitle → match by substring.
    private final By title = descContains("Forgot password?");
    private final By emailField = editText(0);
    private final By phoneField = editText(1);
    private final By resetButton = accId("Send reset code");

    // ---- validation / outcome messages (exact content-desc) ----------------
    public static final String BOTH_EMPTY = "Please enter your email or phone number.";
    public static final String BOTH_FILLED = "Please enter either an email OR a phone number, not both.";
    public static final String EMAIL_INVALID = "Please enter a valid email address.";
    public static final String PHONE_INVALID = "Please enter a valid 10-digit phone number.";
    /**
     * The generic outcome for <em>any</em> accepted request — registered or not.
     *
     * <p>The backend deliberately does not reveal whether an account exists
     * ({@code passwordController.sendOtp}: "Do NOT reveal existence to the caller (no account
     * enumeration): if the email is unknown, return the same generic success as the happy path and
     * skip OTP generation / mail entirely"). Verified live: an unregistered address and a registered
     * one both return {@code 200 {"message":"OTP sent successfully."}}.
     *
     * <p><b>The old {@code USER_NOT_FOUND = "User not found."} constant is gone on purpose.</b>
     * Asserting it would pin the suite to the account-enumeration behaviour this replaced — the test
     * would only pass while the vulnerability existed. See
     * {@code ForgotPasswordTest#unknownEmailIsIndistinguishableFromRegistered}.
     */
    public static final String OTP_SENT = "OTP sent successfully.";

    public ForgotPasswordScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(resetButton);
    }

    // ---- field actions ------------------------------------------------------

    public ForgotPasswordScreen enterEmail(String email) {
        if (email != null && !email.isEmpty()) type(emailField, email);
        return this;
    }

    public ForgotPasswordScreen enterPhone(String phone) {
        if (phone != null && !phone.isEmpty()) type(phoneField, phone);
        return this;
    }

    /** Taps "Send reset code". Hide the keyboard first so the button is in the a11y tree. */
    public ForgotPasswordScreen tapReset() {
        LOG.info("ForgotPassword: tapping Send reset code");
        tap(resetButton);
        return this;
    }

    /** Fills the form (skipping blank values) and submits. */
    public ForgotPasswordScreen requestReset(String email, String phone) {
        enterEmail(email);
        enterPhone(phone);
        hideKeyboard();
        return tapReset();
    }

    // ---- assertions / outcomes ---------------------------------------------

    /** True if the given snackbar message (exact content-desc) appears. */
    public boolean isMessageShown(String message) {
        return isPresent(accId(message), Duration.ofSeconds(15));
    }

    /** True if the reset was accepted: the form is left (navigates to the OTP page). */
    public boolean isResetRequested() {
        return waitForAbsence(resetButton, Duration.ofSeconds(20));
    }

    /** True while still on the form (used to assert a rejected attempt). */
    public boolean isStillOnForm() {
        return !isAbsent(resetButton);
    }
}
