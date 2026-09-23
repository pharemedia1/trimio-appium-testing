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
    /**
     * The backend's rate-limit refusal ({@code 429 RATE_LIMITED}), shown as an ordinary error
     * snackbar and therefore indistinguishable on screen from a wrong password.
     *
     * <p>It has to be told apart, because the two mean opposite things for a test run. A wrong
     * password is a defect or bad data; this is the suite having made too many sign-in attempts
     * too quickly, and it is <b>not</b> about the account at all — the same credentials work from
     * curl at the same moment.
     *
     * <p>It is not hypothetical: {@code full-regression.xml} runs the auth block (which spends
     * something like forty logins, registrations and OTP requests) immediately before the
     * signed-in journeys, and the limiter is per-source and shared. Every client journey then
     * skipped with "the account may be unverified, suspended, or the password may have changed",
     * which sent the reader to look at an account that was in perfect order.
     */
    public static final String RATE_LIMITED = "Too many attempts";
    /** What the app says when the per-account lockout (423) has engaged rather than the limiter. */
    public static final String ACCOUNT_LOCKED = "temporarily locked";

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

    /** What a sign-in attempt actually resulted in. */
    public enum LoginOutcome {
        /** The form is gone — we are in a shell. */
        ACCEPTED,
        /** The backend refused because of the rate limiter or the per-account lockout. */
        RATE_LIMITED,
        /** Still on the form for some other reason (bad credentials, unverified account…). */
        REJECTED
    }

    /**
     * Waits for the sign-in to resolve and reports <em>which</em> way, distinguishing a
     * rate-limit refusal from a credential one.
     *
     * <p><b>Why this cannot be two separate checks.</b> The obvious shape — call
     * {@link #isLoginAccepted()}, and if it is false look for the rate-limit snackbar — does not
     * work, and quietly: {@code isLoginAccepted()} spends up to 20 seconds waiting for the submit
     * button to disappear, and the snackbar lives about four. By the time anybody asks, the only
     * evidence has been gone for a quarter of a minute, so every rate-limited sign-in is reported
     * as a bad password. That is exactly what happened across a full regression run — twenty-odd
     * journeys skipped pointing at accounts that were in perfect order.
     *
     * <p>So both conditions are polled together, and the first one to become true wins.
     */
    public LoginOutcome awaitLoginOutcome() {
        return awaitLoginOutcome(Duration.ofSeconds(20));
    }

    /** As {@link #awaitLoginOutcome()}, with an explicit budget. */
    public LoginOutcome awaitLoginOutcome(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isAbsent(loginButton)) {
                return LoginOutcome.ACCEPTED;
            }
            if (isPresent(descContains(RATE_LIMITED), Duration.ofMillis(250))
                    || isPresent(descContains(ACCOUNT_LOCKED), Duration.ofMillis(250))) {
                LOG.warn("Login: refused by the backend's RATE LIMITER, not by the credentials");
                return LoginOutcome.RATE_LIMITED;
            }
        }
        return isAbsent(loginButton) ? LoginOutcome.ACCEPTED : LoginOutcome.REJECTED;
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
    public boolean dismissBiometricPromptIfPresent() {
        if (isPresent(descContains(BIOMETRIC_PROMPT), Duration.ofSeconds(8))) {
            LOG.info("Login: declining the '{}' biometric prompt", BIOMETRIC_PROMPT);
            tap(accId(BIOMETRIC_DECLINE));
            return true;
        }
        return false;
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
    public boolean dismissAppointmentAlertIfPresent() {
        if (isPresent(descContains(APPOINTMENT_ALERT), Duration.ofSeconds(8))) {
            LOG.info("Login: acknowledging the '{}' reminder", APPOINTMENT_ALERT);
            tap(descOrText(APPOINTMENT_ALERT_DISMISS));
            return true;
        }
        return false;
    }

    /**
     * Declines the rate-and-tip sheet for a completed visit, if one is waiting.
     *
     * <p>Taps "Not now" rather than rating or tipping: acknowledging a modal must not leave a
     * review or move money. See {@link #REVIEW_PROMPT} for why this is easy to acquire and hard to
     * recognise.
     */
    public boolean dismissReviewPromptIfPresent() {
        if (isPresent(descContains(REVIEW_PROMPT), Duration.ofSeconds(8))) {
            LOG.info("Login: declining the rate-and-tip sheet for a completed visit");
            tap(accId(REVIEW_PROMPT_DECLINE));
            return true;
        }
        return false;
    }

    /** How many modals to clear before giving up. Six unreviewed visits fit inside this. */
    private static final int MAX_POST_LOGIN_MODALS = 10;

    /**
     * Answers every modal sitting over the signed-in shell, until none is left.
     *
     * <p><b>A loop, not three calls.</b> These are a queue and the queue can repeat: the rate-and-tip
     * sheet is raised <em>per unreviewed completed visit</em>, so answering one uncovers the next.
     * Measured on-device against a client with six completed appointments — the log showed all three
     * known modals dismissed and the Home feed still absent, because a fourth sheet was already
     * behind them. A single pass is correct only for an account with no history, which is the one
     * account nobody keeps.
     *
     * <p>Each pass retries every modal, since answering one can reveal a different kind rather than
     * another of the same. Stops as soon as a pass finds nothing, so the common case costs one pass.
     *
     * @return this screen, for chaining
     */
    public LoginScreen dismissPostLoginModals() {
        for (int pass = 1; pass <= MAX_POST_LOGIN_MODALS; pass++) {
            boolean dismissedSomething = dismissBiometricPromptIfPresent()
                    | dismissAppointmentAlertIfPresent()
                    | dismissReviewPromptIfPresent();
            if (!dismissedSomething) {
                return this;
            }
            LOG.debug("Post-login modal pass {} cleared at least one dialog", pass);
        }
        LOG.warn("Still finding post-login modals after {} passes — continuing anyway",
                MAX_POST_LOGIN_MODALS);
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

    /**
     * True if the EMAIL field reports itself as masked — which it must never be.
     *
     * <p>The control for {@link #isPasswordMasked()}. Asserting only that the password field says
     * "password=true" cannot distinguish a correctly-masked field from a platform that reports
     * every text field that way; a broken masking check would look identical. Reading the same
     * attribute off a field that is known to be plain text makes the pair discriminating.
     */
    public boolean isEmailMasked() {
        var field = find(editText(0));
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
