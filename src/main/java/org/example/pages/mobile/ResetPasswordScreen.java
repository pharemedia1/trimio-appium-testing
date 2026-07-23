package org.example.pages.mobile;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Reset-password screen (reset_password.dart), reached after a correct OTP in the email
 * forgot-password flow. Verified on-device:
 * <ul>
 *   <li>New password     — 1st {@code EditText} (hint "New password")</li>
 *   <li>Confirm password — 2nd {@code EditText} (hint "Confirm password")</li>
 *   <li>Submit — the {@code Button} with content-desc "Reset Password" (the AppBar title shares
 *       that text, so we target the Button class specifically).</li>
 * </ul>
 *
 * <p>Validation here is driven by the button handler (plain snackbars):
 * both empty → "All fields are required"; mismatch → "Passwords do not match". A valid, matching
 * password resets it and navigates back to the login screen.
 */
public class ResetPasswordScreen extends MobileBasePage {

    private final By newPasswordField = editText(0);
    private final By confirmPasswordField = editText(1);
    private final By resetButton = AppiumBy.androidUIAutomator(
            "new UiSelector().className(\"android.widget.Button\").description(\"Reset Password\")");
    private final By loginButton = accId("login_button");

    public static final String ALL_REQUIRED = "All fields are required";
    public static final String MISMATCH = "Passwords do not match";

    public ResetPasswordScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(resetButton);
    }

    public ResetPasswordScreen enterNewPassword(String password) {
        if (password != null && !password.isEmpty()) type(newPasswordField, password);
        return this;
    }

    public ResetPasswordScreen enterConfirmPassword(String password) {
        if (password != null && !password.isEmpty()) type(confirmPasswordField, password);
        return this;
    }

    public ResetPasswordScreen tapReset() {
        LOG.info("ResetPassword: tapping Reset Password");
        tap(resetButton);
        return this;
    }

    /** Fills both fields (skipping blanks) and submits. */
    public ResetPasswordScreen reset(String newPassword, String confirmPassword) {
        enterNewPassword(newPassword);
        enterConfirmPassword(confirmPassword);
        hideKeyboard();
        return tapReset();
    }

    /** True if the given snackbar message (exact content-desc) appears. */
    public boolean isMessageShown(String message) {
        return isPresent(accId(message), Duration.ofSeconds(12));
    }

    /** True if the reset succeeded: the app navigates back to the login screen. */
    public boolean isResetSucceeded() {
        return isPresent(loginButton, Duration.ofSeconds(20));
    }

    /** True while still on the reset form (used to assert a rejected attempt). */
    public boolean isStillOnForm() {
        return !isAbsent(resetButton);
    }
}
