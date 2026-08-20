package org.example.tests.mobile;

import org.example.base.RegisteredAccountTest;
import org.example.dataproviders.TestDataProvider;
import org.example.pages.mobile.ForgotPasswordScreen;
import org.example.pages.mobile.OtpVerificationScreen;
import org.example.utils.DbHelper;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * OTP verification coverage for the Trimio Flutter app (Appium + UiAutomator2, Page Object Model).
 *
 * <p>The account is registered once in {@code @BeforeClass} (see {@link RegisteredAccountTest});
 * each test drives Onboarding → Login → Forgot Password → "I'm a Client" → Reset → OTP screen.
 *
 * <ul>
 *   <li><b>Negative</b> (data-driven): an incomplete code is blocked client-side; a wrong 6-digit
 *       code is rejected by the backend ("Invalid OTP").</li>
 *   <li><b>Positive</b>: the real code — read from {@code users.two_factor_secret} via JDBC — is
 *       accepted. Self-skips unless {@code -Ddb.password=…} (or {@code DB_PASSWORD}) is provided.</li>
 * </ul>
 */
public class OtpTest extends RegisteredAccountTest {

    /** Drives forgot-password with the class account until the OTP screen is shown. */
    private OtpVerificationScreen reachOtpScreen() {
        ForgotPasswordScreen reset = onboarding().goToLogin()
                .goToForgotPassword()
                .chooseClientForReset();
        reset.requestReset(accountEmail, ""); // backend issues a fresh OTP and opens the OTP screen
        OtpVerificationScreen otp = new OtpVerificationScreen(driver);
        Assert.assertTrue(otp.isLoaded(), "OTP screen should be displayed after requesting a reset");
        return otp;
    }

    // ---- Negative cases (data-driven: client + backend) --------------------

    @Test(dataProvider = "otpNegative", dataProviderClass = TestDataProvider.class,
            description = "An invalid OTP is rejected with the right message")
    public void invalidOtpIsRejected(Map<String, Object> data) {
        String scenario = str(data, "scenario");
        OtpVerificationScreen otp = reachOtpScreen();

        otp.submit(str(data, "otp"));

        Assert.assertTrue(otp.isMessageShown(str(data, "expectedMessage")),
                "[" + scenario + "] expected: '" + str(data, "expectedMessage") + "'");
        Assert.assertTrue(otp.isStillOnScreen(), "[" + scenario + "] should remain on the OTP screen");
    }

    // ---- Positive: correct OTP read from the DB ----------------------------

    @Test(description = "The correct OTP (read from the DB) is accepted")
    public void validOtpIsAccepted() {
        if (!DbHelper.isConfigured()) {
            throw new SkipException("Set -Ddb.password=… (or DB_PASSWORD) to run the positive OTP "
                    + "test — it reads the issued code from users.two_factor_secret.");
        }

        OtpVerificationScreen otp = reachOtpScreen();
        String code = DbHelper.getOtp(accountEmail);
        Assert.assertNotNull(code, "An OTP should be stored for " + accountEmail);

        otp.submit(code);

        Assert.assertTrue(otp.isOtpAccepted(), "The correct OTP should advance past the OTP screen");
    }

    /**
     * AUTH-050 — resending an OTP should issue a new code and invalidate the previous one.
     *
     * <p>Skipped because the feature does not exist: {@code otp_page.dart} has no resend control at
     * all in this build. That is worth recording as a gap rather than deleting the case — a user who
     * loses the email currently has no way forward except restarting the whole reset flow.
     */
    @Test(description = "Resending an OTP invalidates the previous code")
    public void resendInvalidatesPreviousCode() {
        OtpVerificationScreen otp = new OtpVerificationScreen(driver);
        if (!otp.hasResendControl()) {
            throw new SkipException("The OTP screen exposes no resend control in this build "
                    + "(otp_page.dart has none), so there is nothing to test. Product gap: a user "
                    + "who never receives the code must restart the reset flow.");
        }
        throw new SkipException("Resend appeared — this test needs implementing against it.");
    }

    /**
     * AUTH-051 — account verification offers separate email and phone OTP resends.
     *
     * <p>Needs an account parked on the verification screen (registered but unverified), which this
     * suite does not currently provision.
     */
    @Test(description = "Account verification supports email and phone OTP resend")
    public void accountVerificationResendsBothChannels() {
        throw new SkipException("Needs an account sitting on accountVerificationPage (registered but "
                + "unverified). Provision one and drive it from registration to enable this.");
    }

}
