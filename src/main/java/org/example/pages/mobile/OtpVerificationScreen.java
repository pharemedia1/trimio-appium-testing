package org.example.pages.mobile;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * OTP verification screen (otp_page.dart) — REDESIGNED. Reached from the forgot-password email
 * flow once the backend issues an OTP (HTTP 200). Selectors derived from the redesigned source:
 * <ul>
 *   <li>Title  — {@code BrandHeader "Verification code"} (MERGES with its subtitle → match by
 *       substring)</li>
 *   <li>Code   — a {@code Pinput} (length 6) that exposes a single {@code EditText} (type all 6
 *       digits into it)</li>
 *   <li>Submit — {@code PrimaryButton} "Verify" (was "Submit OTP")</li>
 * </ul>
 *
 * <p>Behaviour: fewer than 6 digits → client-side snackbar "Please enter a valid 6-digit OTP";
 * the correct code (read from {@code users.two_factor_secret}) → leaves this screen for the
 * reset-password step.
 */
public class OtpVerificationScreen extends MobileBasePage {

    // BrandHeader title MERGES with its subtitle → match by substring.
    private final By title = descContains("Verification code");
    private final By otpField = editText(0);
    private final By submitButton = accId("Verify");

    public static final String INVALID_LENGTH = "Please enter a valid 6-digit OTP";

    public OtpVerificationScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(submitButton) && isPresent(title);
    }

    public OtpVerificationScreen enterOtp(String otp) {
        type(otpField, otp);
        return this;
    }

    public OtpVerificationScreen tapSubmit() {
        LOG.info("OTP: tapping Verify");
        tap(submitButton);
        return this;
    }

    public OtpVerificationScreen submit(String otp) {
        enterOtp(otp);
        // The numeric keyboard covers the "Verify" button; close it first.
        hideKeyboard();
        return tapSubmit();
    }

    /** True if the given client-side snackbar (exact content-desc) appears. */
    public boolean isMessageShown(String message) {
        return isPresent(accId(message), Duration.ofSeconds(10));
    }

    /** True if the OTP was accepted: the screen is left (advances to reset password). */
    public boolean isOtpAccepted() {
        return waitForAbsence(submitButton, Duration.ofSeconds(20));
    }

    /** True while still on the OTP screen (used to assert a rejected attempt). */
    public boolean isStillOnScreen() {
        return !isAbsent(submitButton);
    }
}
