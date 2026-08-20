package org.example.tests.mobile;

import org.example.base.MobileBaseTest;
import org.example.data.TestAccounts;
import org.example.dataproviders.TestDataProvider;
import org.example.pages.mobile.RegistrationScreen;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Registration coverage for the REDESIGNED Trimio Flutter registration page
 * (Appium + UiAutomator2, Page Object Model).
 *
 * <p>Flow under test: Onboarding (carousel) → "Get started" → "I'm a Client" → registration form.
 * The redesign adds full client-side validation on every field plus a required Terms checkbox.
 * Scenarios come from {@code testdata/mobile/registration-data.json}; real accounts use Gmail
 * aliases + the policy-compliant password from {@code test-accounts.json} — no data in code.
 */
public class RegistrationTest extends MobileBaseTest {

    /**
     * The state the positive flows register in.
     *
     * <p>Not arbitrary: the dropdown offers only states an admin has enabled AND published
     * documents for. Texas is currently the only one ({@code state.is_enabled}, with published
     * client_terms + privacy_policy), so registration is impossible anywhere else.
     */
    private static final String SIGNUP_STATE = "Texas";

    /** Email created by the positive test, reused by the duplicate-email test. */
    private static String registeredEmail;

    private RegistrationScreen openRegistrationForm() {
        RegistrationScreen form = onboarding().goToRegister().chooseClient();
        Assert.assertTrue(form.isLoaded(), "Registration form should be displayed");
        return form;
    }

    // ---- Negative cases: client-side field validation (data-driven) --------

    @Test(dataProvider = "registrationNegative", dataProviderClass = TestDataProvider.class,
            description = "Invalid registration input is rejected with the right validation message")
    public void invalidRegistrationIsRejected(Map<String, Object> data) {
        String scenario = str(data, "scenario");
        RegistrationScreen form = openRegistrationForm();

        form.submitFieldsOnly(str(data, "email"), str(data, "phone"), str(data, "password"));

        String expected = str(data, "expectedMessage");
        Assert.assertTrue(form.isMessageShown(expected, 20),
                "[" + scenario + "] expected message: '" + expected + "'");
        Assert.assertTrue(form.isStillOnForm(),
                "[" + scenario + "] should remain on the registration form");
    }

    // ---- Negative case: terms checkbox is required --------------------------

    @Test(description = "Submitting a valid form without accepting the terms is rejected")
    public void unacceptedTermsIsRejected() {
        RegistrationScreen form = openRegistrationForm();

        // All fields valid and every document read, but the checkbox is left unticked.
        form.registerWithoutTerms(TestAccounts.newClientEmail(),
                TestAccounts.defaultPhone(), TestAccounts.genericPassword(), SIGNUP_STATE);

        Assert.assertTrue(form.isMessageShown(RegistrationScreen.TERMS_REQUIRED, 12),
                "Expected the terms-required snackbar: '" + RegistrationScreen.TERMS_REQUIRED + "'");
        Assert.assertTrue(form.isStillOnForm(), "Should remain on the registration form");
    }

    // ---- Positive case (creates a real account in Postgres + Firebase) ------

    @Test(description = "A new client registers successfully with valid details and accepted terms")
    public void registerNewClientSucceeds() {
        String email = TestAccounts.newClientEmail();
        RegistrationScreen form = openRegistrationForm();

        form.register(email, TestAccounts.defaultPhone(), TestAccounts.genericPassword(),
                SIGNUP_STATE);

        Assert.assertTrue(form.isRegistrationSuccessful(),
                "Expected 'Registration successful!' for a brand-new account: " + email);
        registeredEmail = email; // hand off to the duplicate-email test
    }

    /**
     * The professional signup path — the role decides the entire downstream app, so the client
     * happy path does not cover it. {@code chooseProfessional()} opens the same form widget, but a
     * regression in role plumbing (a wrong {@code user_type_id}, or the professional card falling
     * through to the client form) would leave the client test green and this one red.
     */
    @Test(description = "A new professional registers successfully with valid details")
    public void registerNewProfessionalSucceeds() {
        String email = TestAccounts.newProfessionalEmail();
        RegistrationScreen form = onboarding().goToRegister().chooseProfessional();
        Assert.assertTrue(form.isLoaded(), "The professional registration form should open");

        form.register(email, TestAccounts.defaultPhone(), TestAccounts.genericPassword(),
                SIGNUP_STATE);

        Assert.assertTrue(form.isRegistrationSuccessful(),
                "Expected 'Registration successful!' for a brand-new professional: " + email);
    }

    // ---- Negative case that reuses the positive account ---------------------

    @Test(dependsOnMethods = "registerNewClientSucceeds",
            description = "Registering an already-used email is rejected by Firebase")
    public void duplicateEmailIsRejected() {
        Assert.assertNotNull(registeredEmail, "Positive test must have registered an email first");
        RegistrationScreen form = openRegistrationForm();

        form.register(registeredEmail, TestAccounts.defaultPhone(), TestAccounts.genericPassword(),
                SIGNUP_STATE);

        Assert.assertTrue(form.isMessageShown(RegistrationScreen.EMAIL_IN_USE, 20),
                "Expected '" + RegistrationScreen.EMAIL_IN_USE + "' for a duplicate email");
        Assert.assertTrue(form.isStillOnForm(), "Should remain on the registration form");
    }

    // ---- the state + read-before-agree gate --------------------------------

    /** Submitting without choosing a state is rejected by the dropdown's own validator. */
    @Test(description = "Submitting without choosing a state is rejected")
    public void missingStateIsRejected() {
        RegistrationScreen form = openRegistrationForm();

        form.submitFieldsOnly(TestAccounts.newClientEmail(), TestAccounts.defaultPhone(),
                TestAccounts.genericPassword());

        Assert.assertTrue(form.isMessageShown(RegistrationScreen.STATE_REQUIRED, 15),
                "Expected the state validator: '" + RegistrationScreen.STATE_REQUIRED + "'");
        Assert.assertTrue(form.isStillOnForm(), "Should remain on the registration form");
    }

    /**
     * The point of the read-before-agree redesign: consent comes after the documents.
     *
     * <p>Asserts the gate in both directions — the checkbox is unusable while a document is
     * unread, and becomes usable only once every one has been read to the end. A regression that
     * enabled it early would still pass the happy path, so the disabled half is the real check.
     */
    @Test(description = "The agree checkbox is gated until every document has been read")
    public void termsCheckboxIsGatedUntilDocumentsAreRead() {
        RegistrationScreen form = openRegistrationForm();
        form.fillForm(TestAccounts.newClientEmail(), TestAccounts.defaultPhone(),
                TestAccounts.genericPassword());
        form.chooseState(SIGNUP_STATE);

        Assert.assertTrue(form.isTermsGateShown(),
                "Before reading, the checkbox should show '"
                        + RegistrationScreen.DOCS_GATE_LABEL + "'");

        form.readAllDocuments();

        Assert.assertTrue(form.isTermsAgreeable(),
                "After reading every document the checkbox should offer '"
                        + RegistrationScreen.DOCS_AGREE_LABEL + "'");
    }

    /**
     * The three provider buttons are reachable and NAMED.
     *
     * <p>Worth its own test because the failure is silent. Each button's entire content is a logo
     * image, so an unlabelled one is still visible, still tappable by sighted touch, and still
     * looks perfectly correct in a screenshot — while being an anonymous box to a screen reader
     * and absent from every accessibility-driven query. This asserts the {@code Semantics} labels
     * in {@code SocialSignInRow} survive, on registration and login alike.
     *
     * <p>It deliberately stops at presence. Tapping one hands control to Google Play Services or
     * a provider web view, outside the app and outside Appium's reach, so the sign-up itself is
     * covered at the API level in {@code tests.api.SocialRegistrationTest}.
     */
    @Test(description = "The social sign-up buttons are present and labelled")
    public void socialButtonsAreLabelled() {
        RegistrationScreen form = openRegistrationForm();

        Assert.assertTrue(form.areSocialButtonsLabelled(),
                "Google, Facebook and Apple should each expose a name — '"
                        + RegistrationScreen.SOCIAL_GOOGLE + "' and friends");
    }

    /**
     * AUTH-021 — regression guard for the keyboard trap.
     *
     * <p>The state dropdown and submit sit low on the taller redesigned form. This asserts both
     * stay reachable once the keyboard is up. (It no longer checks the Terms checkbox: that is
     * gated behind choosing a state and reading every document, so its absence here would say
     * nothing about the keyboard.) It is also the guard for the defect that broke this whole
     * suite: {@code hideKeyboard()} used to send BACK, which popped the screen instead of closing
     * the keyboard, and every later lookup then failed against a screen that no longer existed.
     */
    @Test(description = "State dropdown and submit stay reachable with the keyboard open")
    public void termsAndSubmitReachableWithKeyboardOpen() {
        RegistrationScreen form = onboarding().goToRegister().chooseClient();
        Assert.assertTrue(form.isLoaded(), "The registration form should open");

        form.fillForm("keyboard+" + System.currentTimeMillis() + "@example.com", "5551234567", "Trimio@2580");

        Assert.assertTrue(form.areTermsAndSubmitReachable(),
                "The state dropdown and 'Create account' must remain in the accessibility tree "
                        + "after typing — and the screen must still be the registration form");
    }

}
