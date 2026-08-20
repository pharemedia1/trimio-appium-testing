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
    // The state dropdown's label differs by role: a client is asked where they receive
    // services, a professional where they work.
    private final By clientStateDropdown = descContains(CLIENT_STATE_QUESTION);
    private final By proStateDropdown = descContains(PRO_STATE_QUESTION);
    // A document card merges into one node ("<title>\nTap to read — Texas"); once read the
    // subtitle flips to "Read — Texas", so "Tap to read" locates exactly the UNREAD ones.
    private final By unreadDocumentCard = descContains(DOC_UNREAD_HINT);
    private final By readThisButton = descOrText(DOC_READ_BUTTON);

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
    public static final String STATE_REQUIRED = "Please choose your state";
    /**
     * The terms gate message. NOTE the wording changed with the read-before-agree redesign: it is
     * no longer "Please accept Trimio’s Terms and Privacy Policy to continue." Agreement is now
     * downstream of actually opening each document.
     */
    public static final String TERMS_REQUIRED =
            "Please open and read the Terms, then tick the box to continue.";

    // ---- the document-reading gate ------------------------------------------
    public static final String CLIENT_STATE_QUESTION = "Where will you receive services?";
    public static final String PRO_STATE_QUESTION = "Where will you be working?";
    public static final String DOC_UNREAD_HINT = "Tap to read";
    public static final String DOC_READ_BUTTON = "I\'ve read this";
    public static final String DOCS_GATE_LABEL = "Open each document above to continue";
    public static final String DOCS_AGREE_LABEL = "I have read and agree to the documents above";
    // ---- social sign-up ------------------------------------------------------
    // These read as content-desc only because SocialSignInRow wraps each button in
    // MergeSemantics + Semantics(label:). The buttons are logo images with no text, so before
    // that they were unnamed tappable boxes — invisible to a screen reader and to UiAutomator2.
    public static final String SOCIAL_GOOGLE = "Continue with Google";
    public static final String SOCIAL_FACEBOOK = "Continue with Facebook";
    public static final String SOCIAL_APPLE = "Continue with Apple";

    public static final String NO_STATES_OPEN =
            "Trimio is not open for new accounts in any state right now.";

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

    // ---- the state + document gate ------------------------------------------

    /**
     * Picks a state from the dropdown, which is what makes the terms appear at all.
     *
     * <p>The list is not static: it holds only the states an admin has both enabled
     * ({@code state.is_enabled}) and published documents for. Choosing one clears any previous
     * tick, because agreeing to one state's terms cannot carry over to another's.
     */
    public RegistrationScreen chooseState(String stateName) {
        LOG.info("Registration: choosing state '{}'", stateName);
        hideKeyboard();
        By dropdown = isPresent(clientStateDropdown, SHORT_TIMEOUT)
                ? clientStateDropdown : proStateDropdown;
        scrollToDesc(isPresent(clientStateDropdown, SHORT_TIMEOUT)
                ? CLIENT_STATE_QUESTION : PRO_STATE_QUESTION);
        tap(dropdown);
        scrollAndTap(stateName);
        return this;
    }

    /**
     * Opens every document and reads it to the end, which is what enables the checkbox.
     *
     * <p>Reaching the end is the precondition the app enforces: {@code ClientTermsPage} only
     * enables "I\'ve read this" once the scroll position is within 48px of the bottom, and only
     * that button pops back {@code true}. So this scrolls each document down before tapping.
     * Loops on the UNREAD hint rather than a fixed count — the number of documents is decided by
     * the state's published set, not by the test.
     */
    public RegistrationScreen readAllDocuments() {
        int guard = 0;
        while (guard++ < 6) {
            if (!isPresentAfterScroll(DOC_UNREAD_HINT)) break;
            // Re-scroll immediately before the tap: Flutter drops the semantics of off-screen
            // widgets, so a card located a moment ago can be genuinely absent by the time we
            // reach for it.
            scrollToDesc(DOC_UNREAD_HINT);
            if (!isPresent(unreadDocumentCard, SHORT_TIMEOUT)) break;
            LOG.info("Registration: opening document {} to read it", guard);
            tap(unreadDocumentCard);

            // Reaching the END is the precondition, and it cannot be inferred from the button
            // being on screen: while disabled it is still in the tree. Fling to the bottom, then
            // require it to actually be enabled before tapping.
            flingToEnd();
            if (!isEnabled(readThisButton, SHORT_TIMEOUT)) {
                flingToEnd();
            }
            tap(readThisButton);
            // Wait until we are back on the form before hunting for the next card.
            waitForAbsence(readThisButton, Duration.ofSeconds(10));
        }
        if (guard >= 6) LOG.warn("Registration: gave up after {} document opens", guard);
        return this;
    }

    /** True while the checkbox is still gated on unread documents. */
    public boolean isTermsGateShown() {
        return isPresentAfterScroll(DOCS_GATE_LABEL);
    }

    /** True once every document has been read and the checkbox is tickable. */
    public boolean isTermsAgreeable() {
        return isPresentAfterScroll(DOCS_AGREE_LABEL);
    }

    /** True if every social provider button is present AND named in the accessibility tree. */
    public boolean areSocialButtonsLabelled() {
        for (String label : new String[]{SOCIAL_GOOGLE, SOCIAL_FACEBOOK, SOCIAL_APPLE}) {
            if (!isPresentAfterScroll(label)) {
                LOG.warn("Registration: social button '{}' is not in the tree", label);
                return false;
            }
        }
        return true;
    }

    /** True if the app is refusing registration because no state is open for signup. */
    public boolean isSignupClosedEverywhere() {
        return isPresentAfterScroll(NO_STATES_OPEN);
    }

    // ---- composite flows ----------------------------------------------------

    /**
     * The full happy path: fields → state → read every document → tick → submit.
     *
     * <p>Kept as one helper because the app treats them as one gate; a caller that skipped the
     * documents would be testing the gate, not registration.
     */
    public RegistrationScreen register(String email, String phone, String password,
                                       String stateName) {
        fillForm(email, phone, password);
        // The Terms checkbox + Create button sit below the keyboard; close it so they're
        // present in the accessibility tree.
        hideKeyboard();
        chooseState(stateName);
        readAllDocuments();
        acceptTerms();
        return tapCreateAccount();
    }

    /**
     * Everything valid and the documents read, but the box left unticked — drives the
     * terms-required snackbar without also tripping the state validator.
     */
    public RegistrationScreen registerWithoutTerms(String email, String phone, String password,
                                                   String stateName) {
        fillForm(email, phone, password);
        hideKeyboard();
        chooseState(stateName);
        readAllDocuments();
        return tapCreateAccount();
    }

    /**
     * Fills the three fields and submits with NO state chosen — the driver for pure field
     * validation, where the state/terms gate is irrelevant and would only add flakiness.
     */
    public RegistrationScreen submitFieldsOnly(String email, String phone, String password) {
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

    /**
     * True when the Terms checkbox and the submit button are both still addressable (AUTH-021).
     *
     * <p>Flutter drops the semantics of widgets pushed off-screen, so on the taller form the keyboard
     * can hide both. It also verifies we are still <em>on</em> the registration form: the historical
     * failure here was not the controls scrolling away but the screen being popped outright by a
     * BACK-based keyboard dismissal, which looks identical from a single missing-element check.
     */
    public boolean areTermsAndSubmitReachable() {
        hideKeyboard();
        boolean submit = isPresent(accId("Create account"), java.time.Duration.ofSeconds(15));
        // NOT the Terms checkbox any more: it does not exist until a state is chosen and every
        // document has been read, so its absence here says nothing about the keyboard. The state
        // dropdown is the control that now sits lowest on the un-gated form.
        boolean state = isPresent(clientStateDropdown, java.time.Duration.ofSeconds(10))
                || isPresentAfterScroll(CLIENT_STATE_QUESTION);
        if (!submit || !state) {
            LOG.warn("Registration: submit present={}, state dropdown present={}", submit, state);
        }
        return submit && state;
    }

}
