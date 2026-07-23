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

        form.register(str(data, "email"), str(data, "phone"), str(data, "password"));

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

        // All fields valid, but the Terms checkbox is left unticked.
        form.registerWithoutTerms(TestAccounts.newClientEmail(),
                TestAccounts.defaultPhone(), TestAccounts.genericPassword());

        Assert.assertTrue(form.isMessageShown(RegistrationScreen.TERMS_REQUIRED, 12),
                "Expected the terms-required snackbar: '" + RegistrationScreen.TERMS_REQUIRED + "'");
        Assert.assertTrue(form.isStillOnForm(), "Should remain on the registration form");
    }

    // ---- Positive case (creates a real account in Postgres + Firebase) ------

    @Test(description = "A new client registers successfully with valid details and accepted terms")
    public void registerNewClientSucceeds() {
        String email = TestAccounts.newClientEmail();
        RegistrationScreen form = openRegistrationForm();

        form.register(email, TestAccounts.defaultPhone(), TestAccounts.genericPassword());

        Assert.assertTrue(form.isRegistrationSuccessful(),
                "Expected 'Registration successful!' for a brand-new account: " + email);
        registeredEmail = email; // hand off to the duplicate-email test
    }

    // ---- Negative case that reuses the positive account ---------------------

    @Test(dependsOnMethods = "registerNewClientSucceeds",
            description = "Registering an already-used email is rejected by Firebase")
    public void duplicateEmailIsRejected() {
        Assert.assertNotNull(registeredEmail, "Positive test must have registered an email first");
        RegistrationScreen form = openRegistrationForm();

        form.register(registeredEmail, TestAccounts.defaultPhone(), TestAccounts.genericPassword());

        Assert.assertTrue(form.isMessageShown(RegistrationScreen.EMAIL_IN_USE, 20),
                "Expected '" + RegistrationScreen.EMAIL_IN_USE + "' for a duplicate email");
        Assert.assertTrue(form.isStillOnForm(), "Should remain on the registration form");
    }
}
