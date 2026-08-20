package org.example.tests.mobile;

import org.example.base.MobileBaseTest;
import org.example.data.TestAccounts;
import org.example.dataproviders.TestDataProvider;
import org.example.pages.mobile.LoginScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.example.pages.mobile.client.ClientProfileScreen;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Login coverage for the Trimio Flutter app (Appium + UiAutomator2, Page Object Model).
 *
 * <p>Flow under test: Onboarding → Login → login form. Scenarios come from
 * {@code testdata/mobile/login-data.json}; rows are tagged {@code type} = "validation"
 * (client-side) or "backend". The positive case uses the verified account from
 * {@code test-accounts.json} and self-skips until one is configured.
 */
public class LoginTest extends MobileBaseTest {


    private LoginScreen openLoginForm() {
        LoginScreen form = onboarding().goToLogin();
        Assert.assertTrue(form.isLoaded(), "Login form should be displayed");
        return form;
    }

    // ---- Negative cases (data-driven: validation + backend) ----------------

    @Test(dataProvider = "loginNegative", dataProviderClass = TestDataProvider.class,
            description = "Invalid login input is rejected with the right message")
    public void invalidLoginIsRejected(Map<String, Object> data) {
        String scenario = str(data, "scenario");
        String expected = str(data, "expectedMessage");
        LoginScreen form = openLoginForm();

        form.login(str(data, "email"), str(data, "password"));

        if ("backend".equalsIgnoreCase(str(data, "type"))) {
            Assert.assertTrue(form.isBackendErrorShown(expected),
                    "[" + scenario + "] expected backend error: '" + expected + "'");
        } else {
            Assert.assertTrue(form.isValidationShown(expected),
                    "[" + scenario + "] expected validation: '" + expected + "'");
        }
        Assert.assertTrue(form.isStillOnForm(), "[" + scenario + "] should remain on the login form");
    }

    // ---- Positive case (verified account from JSON) ------------------------

    /**
     * AUTH-022 — a verified client logs in and lands on the client home tabs.
     *
     * <p>Leaving the form is necessary but not sufficient: a login that succeeded and then routed to
     * the wrong shell (or stalled behind the biometric modal) would also leave the form. The case
     * calls for the client bottom nav, so decline the "Faster sign-in" prompt and assert the shell.
     */
    @Test(description = "A registered user logs in successfully and lands on the client shell")
    public void validCredentialsAreAccepted() {
        if (!TestAccounts.hasVerifiedAccount()) {
            throw new SkipException("Set verifiedAccount in test-accounts.json to run the "
                    + "positive login test.");
        }

        LoginScreen form = openLoginForm();
        form.login(TestAccounts.verifiedEmail(), TestAccounts.verifiedPassword());

        Assert.assertTrue(form.isLoginAccepted(),
                "Login should be accepted (form left) for: " + TestAccounts.verifiedEmail());

        form.dismissBiometricPromptIfPresent();

        // A client whose profile has no name/address is pinned to "Your details" and the shell is
        // never built — the app is correct, the account simply isn't provisioned. Skip rather than
        // report a routing failure that doesn't exist (same convention as RoleSessionTest).
        if (new ClientHomeScreen(driver).isBlockedByProfileGate()) {
            throw new SkipException("The verified account is held on the '"
                    + ClientHomeScreen.PROFILE_GATE + "' profile gate, so the client shell is "
                    + "unreachable. Complete name + address for "
                    + TestAccounts.verifiedEmail() + " to assert AUTH-022 end to end.");
        }

        Assert.assertTrue(new BottomNavBar(driver).isClientShell(),
                "A verified client should land on the client bottom nav "
                        + "(Home/Book/Appointments/Shop/Profile)");
    }

    /** AUTH-033 — a password must never be readable over the user's shoulder by default. */
    @Test(description = "The password field masks its input")
    public void passwordIsMasked() {
        LoginScreen form = openLoginForm();
        form.enterPassword("Trimio@2580");

        Assert.assertTrue(form.isPasswordMasked(),
                "The password field must mask input by default");
    }

    /** AUTH-035 — the recovery entry point. */
    @Test(description = "'Forgot password?' opens the reset role page")
    public void forgotPasswordOpensResetRolePage() {
        LoginScreen form = openLoginForm();

        Assert.assertTrue(form.goToForgotPassword().isLoaded(),
                "'Forgot password?' should open the role page in reset mode");
    }

    /** AUTH-036 — the signup entry point from login. */
    @Test(description = "'Register' returns to the signup flow")
    public void registerLinkReturnsToSignup() {
        LoginScreen form = openLoginForm();

        Assert.assertTrue(form.goToRegister().isLoaded(),
                "'Register' should open the role/registration flow");
    }

    /** AUTH-034 — declining biometrics must not enable it, and must not block the app. */
    @Test(description = "The biometric opt-in can be declined")
    public void biometricPromptCanBeDeclined() {
        if (!TestAccounts.hasVerifiedAccount()) {
            throw new SkipException("Set verifiedAccount in test-accounts.json to run this test.");
        }
        LoginScreen form = openLoginForm();
        form.login(TestAccounts.verifiedEmail(), TestAccounts.verifiedPassword());
        Assert.assertTrue(form.isLoginAccepted(), "Login should be accepted");

        form.dismissBiometricPromptIfPresent();

        Assert.assertTrue(new BottomNavBar(driver).isClientShell(),
                "Declining the prompt should reveal the shell rather than leaving a modal in place");
    }

    /**
     * AUTH-038 — the session survives a cold start.
     *
     * <p>Uses a full app restart rather than a new driver session: {@code @BeforeMethod} creates the
     * driver with {@code noReset=false}, which clears app data, so a fresh session could never
     * observe a persisted login.
     */
    @Test(description = "A persisted session skips the login screen on relaunch")
    public void sessionPersistsAcrossRelaunch() {
        if (!TestAccounts.hasVerifiedAccount()) {
            throw new SkipException("Set verifiedAccount in test-accounts.json to run this test.");
        }
        LoginScreen form = openLoginForm();
        form.login(TestAccounts.verifiedEmail(), TestAccounts.verifiedPassword());
        Assert.assertTrue(form.isLoginAccepted(), "Login should be accepted");
        form.dismissBiometricPromptIfPresent();

        restartApp();

        Assert.assertFalse(new LoginScreen(driver).isLoaded(),
                "After a relaunch the app should restore the session, not ask for credentials again");
    }

    /** AUTH-024 — a professional whose profile is pending never reaches the dashboard. */
    @Test(description = "A professional with a pending profile is routed to the profile screen")
    public void pendingProfessionalIsRouted() {
        if (!TestAccounts.hasAccountFor("professionalPending")) {
            throw new SkipException("Add roleAccounts.professionalPending (a professional whose "
                    + "profile.approval_status is 'pending') to test-accounts.json to run this test.");
        }
        LoginScreen form = openLoginForm();
        form.login(TestAccounts.emailFor("professionalPending"),
                TestAccounts.passwordFor("professionalPending"));
        Assert.assertTrue(form.isLoginAccepted(), "Login should be accepted");
        form.dismissBiometricPromptIfPresent();

        Assert.assertFalse(new BottomNavBar(driver).isProfessionalShell(),
                "A pending professional must not reach the dashboard tabs");
    }


    /**
     * AUTH-039 — logging out must actually clear the session, not just navigate.
     *
     * <p>The relaunch is the real assertion. Returning to the login screen proves only that the app
     * navigated; if the stored prefs survived, the next cold start would silently sign the user back
     * in — which on a shared device is somebody else reading their appointments.
     */
    @Test(description = "Logging out clears the session and survives a relaunch")
    public void logoutClearsSession() {
        if (!TestAccounts.hasVerifiedAccount()) {
            throw new SkipException("Set verifiedAccount in test-accounts.json to run this test.");
        }
        LoginScreen form = openLoginForm();
        form.login(TestAccounts.verifiedEmail(), TestAccounts.verifiedPassword());
        Assert.assertTrue(form.isLoginAccepted(), "Login should be accepted");
        form.dismissBiometricPromptIfPresent();

        new BottomNavBar(driver).open(BottomNavBar.CLIENT_PROFILE);
        ClientProfileScreen profile = new ClientProfileScreen(driver);
        if (!profile.hasLogoutControl()) {
            throw new SkipException("No logout control was reachable on the client account page.");
        }
        profile.logout();

        Assert.assertTrue(new LoginScreen(driver).isLoaded(),
                "Logging out should return to the login screen");

        restartApp();
        Assert.assertTrue(new LoginScreen(driver).isLoaded(),
                "After logout a relaunch must NOT restore the session");
    }

}
