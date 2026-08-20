package org.example.tests.mobile;

import org.example.base.RegisteredAccountTest;
import org.example.data.TestAccounts;
import org.example.dataproviders.TestDataProvider;
import org.example.pages.mobile.ForgotPasswordScreen;
import org.example.pages.mobile.OtpVerificationScreen;
import org.example.pages.mobile.ResetPasswordScreen;
import org.example.utils.DbHelper;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Reset-password coverage for the Trimio Flutter app (Appium + UiAutomator2, Page Object Model).
 *
 * <p>The reset screen sits at the end of the email recovery flow, so reaching it means completing
 * the OTP step: Forgot Password → OTP (correct code read from the DB) → Reset Password. All tests
 * therefore require {@code -Ddb.password=…} (to read the OTP) and self-skip otherwise.
 *
 * <ul>
 *   <li><b>Negative</b> (data-driven): empty fields → "All fields are required"; mismatch →
 *       "Passwords do not match".</li>
 *   <li><b>Positive</b>: a valid, matching password resets it and returns to the login screen.</li>
 * </ul>
 */
public class ResetPasswordTest extends RegisteredAccountTest {

    /** Drives forgot-password → OTP (correct code from DB) → reset-password screen. */
    private ResetPasswordScreen reachResetScreen() {
        if (!DbHelper.isConfigured()) {
            throw new SkipException("Set -Ddb.password=… (or DB_PASSWORD) — reaching the reset "
                    + "screen needs the OTP, which is read from users.two_factor_secret.");
        }
        ForgotPasswordScreen reset = onboarding().goToLogin()
                .goToForgotPassword()
                .chooseClientForReset();
        reset.requestReset(accountEmail, "");

        OtpVerificationScreen otp = new OtpVerificationScreen(driver);
        Assert.assertTrue(otp.isLoaded(), "OTP screen should be displayed");
        String code = DbHelper.getOtp(accountEmail);
        Assert.assertNotNull(code, "An OTP should be stored for " + accountEmail);
        otp.submit(code);

        ResetPasswordScreen rp = new ResetPasswordScreen(driver);
        Assert.assertTrue(rp.isLoaded(), "Reset Password screen should be displayed");
        return rp;
    }

    // ---- Negative cases (data-driven) --------------------------------------

    @Test(dataProvider = "resetPasswordNegative", dataProviderClass = TestDataProvider.class,
            description = "Invalid reset-password input is rejected with the right message")
    public void invalidResetIsRejected(Map<String, Object> data) {
        String scenario = str(data, "scenario");
        ResetPasswordScreen rp = reachResetScreen();

        rp.reset(str(data, "newPassword"), str(data, "confirmPassword"));

        Assert.assertTrue(rp.isMessageShown(str(data, "expectedMessage")),
                "[" + scenario + "] expected: '" + str(data, "expectedMessage") + "'");
        Assert.assertTrue(rp.isStillOnForm(), "[" + scenario + "] should remain on the reset form");
    }

    // ---- Positive case ------------------------------------------------------

    @Test(description = "A valid, matching password resets and returns to login")
    public void resetSucceeds() {
        ResetPasswordScreen rp = reachResetScreen();
        String pw = TestAccounts.resetPassword();

        rp.reset(pw, pw);

        Assert.assertTrue(rp.isResetSucceeded(),
                "A valid reset should navigate back to the login screen");
    }

    /**
     * AUTH-056 — a forced password reset must block the app until a new password is set.
     *
     * <p>Needs an account flagged for forced reset (the state a freshly-invited vendor lands in).
     * Provision one and point roleAccounts.forcedReset at it to enable this.
     */
    @Test(description = "A forced password reset cannot be bypassed")
    public void forcedResetBlocksTheApp() {
        if (!TestAccounts.hasAccountFor("forcedReset")) {
            throw new SkipException("Add roleAccounts.forcedReset (an account flagged for a forced "
                    + "password reset, e.g. a newly-invited vendor) to test-accounts.json.");
        }
        throw new SkipException("Account configured — implement against the 'Set a new password' screen.");
    }

}
