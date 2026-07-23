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
}
