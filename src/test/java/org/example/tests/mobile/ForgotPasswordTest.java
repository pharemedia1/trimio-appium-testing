package org.example.tests.mobile;

import org.example.base.MobileBaseTest;
import org.example.data.TestAccounts;
import org.example.dataproviders.TestDataProvider;
import org.example.pages.mobile.ForgotPasswordScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.example.pages.mobile.OtpVerificationScreen;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Forgot-password coverage for the Trimio Flutter app (Appium + UiAutomator2, Page Object Model).
 *
 * <p>Flow under test: Onboarding → Login → "Forgot Password?" → "I'm a Client" → reset form.
 * Scenarios come from {@code testdata/mobile/forgot-password-data.json}. The positive case uses
 * the verified account from {@code test-accounts.json} and self-skips until one is configured.
 */
public class ForgotPasswordTest extends MobileBaseTest {

    private ForgotPasswordScreen openForgotPasswordForm() {
        ForgotPasswordScreen form = onboarding().goToLogin()
                .goToForgotPassword()
                .chooseClientForReset();
        Assert.assertTrue(form.isLoaded(), "Forgot-password form should be displayed");
        return form;
    }

    // ---- Negative cases (data-driven: validation + backend) ----------------

    @Test(dataProvider = "forgotPasswordNegative", dataProviderClass = TestDataProvider.class,
            description = "Invalid reset input is rejected with the right message")
    public void invalidResetRequestIsRejected(Map<String, Object> data) {
        String scenario = str(data, "scenario");
        String expected = str(data, "expectedMessage");
        ForgotPasswordScreen form = openForgotPasswordForm();

        form.requestReset(str(data, "email"), str(data, "phone"));

        Assert.assertTrue(form.isMessageShown(expected),
                "[" + scenario + "] expected message: '" + expected + "'");
        Assert.assertTrue(form.isStillOnForm(),
                "[" + scenario + "] should remain on the reset form");
    }

    // ---- Positive case (verified account from JSON) ------------------------

    @Test(description = "Reset for a registered email is accepted (proceeds to OTP)")
    public void registeredEmailResetIsAccepted() {
        if (TestAccounts.verifiedEmail().isBlank()) {
            throw new SkipException("Set verifiedAccount.email in test-accounts.json to run the "
                    + "positive forgot-password test.");
        }

        ForgotPasswordScreen form = openForgotPasswordForm();
        form.requestReset(TestAccounts.verifiedEmail(), "");

        Assert.assertTrue(form.isResetRequested(),
                "Reset should be accepted (form left for OTP) for: " + TestAccounts.verifiedEmail());
    }

    // ---- Account enumeration ----------------------------------------------

    /**
     * An unregistered email must produce exactly the same outcome as a registered one.
     *
     * <p>This replaces an older case that asserted "User not found." for an unknown address. That
     * expectation is now wrong, and was worth removing rather than merely updating: it asserted that
     * the API <em>told the caller whether an account existed</em>, so it would have passed only while
     * Trimio was enumerable and failed the moment someone fixed it. A test that fails when a
     * vulnerability is closed is worse than no test.
     *
     * <p>The backend now returns the same generic {@code 200 "OTP sent successfully."} either way and
     * skips OTP generation entirely for unknown addresses (verified live against both). So the
     * meaningful assertion is indistinguishability, which is what this checks from the UI: the
     * unknown address must advance past the form just like a real one, and must not surface any
     * "not found" wording.
     */
    @Test(description = "An unknown email is indistinguishable from a registered one (no account enumeration)")
    public void unknownEmailIsIndistinguishableFromRegistered() {
        ForgotPasswordScreen form = openForgotPasswordForm();

        form.requestReset("trimiotest+notregistered" + System.currentTimeMillis() + "@gmail.com", "");

        Assert.assertFalse(form.isMessageShown("not found"),
                "The app must not reveal that the account does not exist — that is account "
                        + "enumeration, and the backend deliberately returns a generic success");
        Assert.assertTrue(form.isResetRequested(),
                "An unknown email should advance exactly as a registered one does, so the two "
                        + "cannot be told apart from the client");
    }

    /** AUTH-045 — a registered email must actually reach the OTP screen, not just be accepted. */
    @Test(description = "A registered email advances to the OTP screen")
    public void registeredEmailOpensOtpScreen() {
        if (TestAccounts.verifiedEmail().isBlank()) {
            throw new SkipException("Set verifiedAccount.email in test-accounts.json.");
        }
        ForgotPasswordScreen form = openForgotPasswordForm();
        form.requestReset(TestAccounts.verifiedEmail(), "");

        Assert.assertTrue(new OtpVerificationScreen(driver).isLoaded(),
                "A reset request for a registered email should open the verification-code screen");
    }

    /** AUTH-046 — role only scopes the lookup; all three reach the same form. */
    @Test(description = "The reset role page routes every role to the same form")
    public void resetRolePageRoutesEveryRole() {
        Assert.assertTrue(onboarding().goToLogin().goToForgotPassword().chooseClientForReset().isLoaded(),
                "Client should reach the reset form");
        restartApp();
        Assert.assertTrue(onboarding().goToLogin().goToForgotPassword().chooseProfessionalForReset().isLoaded(),
                "Professional should reach the reset form");
        restartApp();
        Assert.assertTrue(onboarding().goToLogin().goToForgotPassword().chooseAdminForReset().isLoaded(),
                "Admin should reach the reset form");
    }

}
