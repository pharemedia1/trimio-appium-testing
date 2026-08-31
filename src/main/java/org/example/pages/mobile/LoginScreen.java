package org.example.pages.mobile;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Login form (loginPage.dart) — REDESIGNED. Reached via Onboarding → "Skip"/"Sign in".
 *
 * <p>The form now lives inside a card with a BrandHeader, social sign-in row and an optional
 * biometric button, but the testable surface is unchanged in shape:
 * <ul>
 *   <li>Email    — 1st {@code EditText} (hint "Email address")</li>
 *   <li>Password — 2nd {@code EditText} (hint "Password", with a visibility toggle)</li>
 *   <li>Submit   — content-desc "login_button" (still wrapped in {@code Semantics(label:'login_button')})</li>
 *   <li>Forgot   — "Forgot password?" {@code TextButton} (note the lower-case "p" — it changed)</li>
 * </ul>
 *
 * <p>Validation (utils/validators.dart, confirmed in source):
 * <ul>
 *   <li>email: empty → "Please enter your email"; bad format → "Please enter a valid email address"</li>
 *   <li>password: empty → "Please enter your password". <b>The old "at least 6 characters" rule is
 *       gone</b> — login no longer enforces a minimum length (so existing accounts aren't locked out).</li>
 *   <li>wrong credentials → backend snackbar via {@code AppSnackBar.error}. The redesign dropped the
 *       {@code Semantics(label:'login_error_message')} wrapper, so the snackbar now surfaces as its
 *       <em>plain message text</em> (e.g. "Invalid email or password.") — match by {@code descContains}.</li>
 * </ul>
 */
public class LoginScreen extends MobileBasePage {

    // ---- locators -----------------------------------------------------------
    private final By emailField = editText(0);
    private final By passwordField = editText(1);
    private final By loginButton = accId("login_button");
    private final By forgotPasswordLink = accId("Forgot password?");

    // ---- validation / outcome messages -------------------------------------
    public static final String EMAIL_REQUIRED = "Please enter your email";
    public static final String EMAIL_INVALID = "Please enter a valid email address";
    public static final String PASSWORD_REQUIRED = "Please enter your password";
    public static final String INVALID_CREDENTIALS = "Invalid email or password.";

    public LoginScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(loginButton);
    }

    // ---- field actions ------------------------------------------------------

    public LoginScreen enterEmail(String email) {
        if (email != null && !email.isEmpty()) type(emailField, email);
        return this;
    }

    public LoginScreen enterPassword(String password) {
        if (password != null && !password.isEmpty()) type(passwordField, password);
        return this;
    }

    /** Taps "login_button". Center tap lands on it even when the keyboard overlaps. */
    public LoginScreen tapLogin() {
        LOG.info("Login: tapping login_button");
        tap(loginButton);
        return this;
    }

    /** Fills the form (skipping blank values) and submits. */
    public LoginScreen login(String email, String password) {
        enterEmail(email);
        enterPassword(password);
        return tapLogin();
    }

    /** Taps "Forgot password?" → opens the role page in the password-reset flow. */
    public RoleSelectionScreen goToForgotPassword() {
        LOG.info("Login: tapping Forgot password?");
        tap(forgotPasswordLink);
        return new RoleSelectionScreen(driver);
    }

    // ---- assertions / outcomes ---------------------------------------------

    /** True if the given client-side validation message (exact content-desc) is shown. */
    public boolean isValidationShown(String message) {
        return isPresent(accId(message), Duration.ofSeconds(8));
    }

    /** True if the backend error snackbar contains the given text. */
    public boolean isBackendErrorShown(String containedText) {
        return isPresent(descContains(containedText), Duration.ofSeconds(15));
    }

    /** True if we left the login form (login accepted) — the submit button disappears. */
    public boolean isLoginAccepted() {
        return waitForAbsence(loginButton, Duration.ofSeconds(20));
    }

    // ---- post-login interstitials ------------------------------------------

    /** Title of the biometric opt-in shown after the first successful sign-in. */
    public static final String BIOMETRIC_PROMPT = "Faster sign-in";
    /** The upcoming-appointment reminder that covers the shell, and the button that clears it. */
    public static final String APPOINTMENT_ALERT = "Appointment in";
    public static final String APPOINTMENT_ALERT_DISMISS = "Got it";
    public static final String BIOMETRIC_DECLINE = "Not now";

    /**
     * The rate-and-tip sheet raised for a visit that has finished but not been reviewed.
     *
     * <p>Third modal in the post-login queue, and the one that grows on you: it appears only once
     * the account has a <em>completed</em> appointment, so a fixture that books successfully today
     * breaks every signed-in client test tomorrow. Its scrim covers the Home feed while leaving the
     * bottom nav in the tree, so {@code isClientShell()} answers true and the shell still never
     * renders — which reads as "the Home feed should render" failing against a perfectly healthy
     * app. Verified on-device 2026-08-30 for a Casey Client with a finished Pat Pro visit.
     */
    public static final String REVIEW_PROMPT = "How was it with";
    /** Its decline button — the same label the biometric prompt uses. */
    public static final String REVIEW_PROMPT_DECLINE = "Not now";
    public static final String BIOMETRIC_ACCEPT = "Enable";

    /**
     * Dismisses the "Faster sign-in" biometric opt-in that appears over the landing screen after a
     * successful login, declining it with "Not now".
     *
     * <p>Why every signed-in test needs this: the dialog is <em>modal over the shell</em>. The login
     * itself has already succeeded and the submit button is gone — so
     * {@link #isLoginAccepted()} is happily true — but the bottom navigation is behind the dialog and
     * therefore absent from the accessibility tree. Verified on-device: the landing screen exposed
     * only "Faster sign-in / Not now / Enable / Dismiss", and the five client tabs appeared the
     * instant it was declined. Without this the shell looks like it failed to load, which reads as a
     * broken selector rather than an unhandled modal.
     *
     * <p>Declining rather than enabling is deliberate: enabling biometrics changes the account's
     * sign-in behaviour for every later run on that device.
     *
     * <p>No-op when the prompt does not appear (it is only offered on devices with biometrics
     * enrolled, and only until the user answers it).
     */
    public LoginScreen dismissBiometricPromptIfPresent() {
        if (isPresent(descContains(BIOMETRIC_PROMPT), Duration.ofSeconds(8))) {
            LOG.info("Login: declining the '{}' biometric prompt", BIOMETRIC_PROMPT);
            tap(accId(BIOMETRIC_DECLINE));
        }
        return this;
    }

    /**
     * Dismisses the upcoming-appointment reminder, if one is waiting.
     *
     * <p>Same shape of problem as the biometric prompt: a professional with a booking soon lands
     * on the shell with "Appointment in 2 hours" modal over it, so the bottom nav is behind the
     * dialog and simply ABSENT from the accessibility tree. The sign-in succeeded and the test
     * then skips reporting "did not land in the professional shell", which is the wrong story —
     * nothing is broken except an unanswered dialog.
     *
     * <p>Taps "Got it" rather than "View appointment": acknowledging must not navigate somewhere
     * the caller did not ask for.
     */
    public LoginScreen dismissAppointmentAlertIfPresent() {
        if (isPresent(descContains(APPOINTMENT_ALERT), Duration.ofSeconds(8))) {
            LOG.info("Login: acknowledging the '{}' reminder", APPOINTMENT_ALERT);
            tap(descOrText(APPOINTMENT_ALERT_DISMISS));
        }
        return this;
    }

    /**
     * Declines the rate-and-tip sheet for a completed visit, if one is waiting.
     *
     * <p>Taps "Not now" rather than rating or tipping: acknowledging a modal must not leave a
     * review or move money. See {@link #REVIEW_PROMPT} for why this is easy to acquire and hard to
     * recognise.
     */
    public LoginScreen dismissReviewPromptIfPresent() {
        if (isPresent(descContains(REVIEW_PROMPT), Duration.ofSeconds(8))) {
            LOG.info("Login: declining the rate-and-tip sheet for a completed visit");
            tap(accId(REVIEW_PROMPT_DECLINE));
        }
        return this;
    }

    /**
     * Answers every modal known to sit over the signed-in shell.
     *
     * <p>Order matters and the list is a queue, not a set: the modals are raised one after another,
     * so the next only becomes visible once the previous is answered. Anything that stops early
     * leaves the shell covered and the failure lands on whichever screen the test wanted next.
     */
    public LoginScreen dismissPostLoginModals() {
        dismissBiometricPromptIfPresent();
        dismissAppointmentAlertIfPresent();
        dismissReviewPromptIfPresent();
        return this;
    }

    /** True while still on the login form (used to assert a rejected attempt). */
    public boolean isStillOnForm() {
        return !isAbsent(loginButton);
    }

    /** True while the password field masks its input (AUTH-033). */
    public boolean isPasswordMasked() {
        var field = find(editText(1));
        return field != null && "true".equalsIgnoreCase(field.getAttribute("password"));
    }

    /** Taps the password visibility toggle, if the field exposes one. */
    public LoginScreen togglePasswordVisibility() {
        if (isPresent(descContains("visibility"), SHORT_TIMEOUT)) {
            tap(descContains("visibility"));
        }
        return this;
    }

    /** Taps "Register" back to the signup flow (AUTH-036). */
    public RoleSelectionScreen goToRegister() {
        scrollAndTap("Register");
        return new RoleSelectionScreen(driver);
    }

}
