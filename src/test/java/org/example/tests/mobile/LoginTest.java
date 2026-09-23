package org.example.tests.mobile;

import org.example.base.MobileBaseTest;
import org.example.data.TestAccounts;
import org.example.dataproviders.TestDataProvider;
import org.example.pages.mobile.LoginScreen;
import org.example.pages.mobile.RoleSelectionScreen;
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
        // The assertion that actually protects the user: being shown an error and being signed in
        // are not mutually exclusive. A backend that rejects the credentials but a client that
        // routes anyway would satisfy both checks above and still hand over the account.
        Assert.assertFalse(new BottomNavBar(driver).isClientShell(),
                "[" + scenario + "] a rejected login must not reach a signed-in shell");
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

        form.dismissPostLoginModals();

        // A client whose profile has no name/address is pinned to "Your details" and the shell is
        // never built — the app is correct, the account simply isn't provisioned. Skip rather than
        // report a routing failure that doesn't exist (same convention as RoleSessionTest).
        if (new ClientHomeScreen(driver).isBlockedByProfileGate()) {
            throw new SkipException("The verified account is held on the '"
                    + ClientHomeScreen.PROFILE_GATE + "' profile gate, so the client shell is "
                    + "unreachable. Complete name + address for "
                    + TestAccounts.verifiedEmail() + " to assert AUTH-022 end to end.");
        }

        Assert.assertFalse(form.isStillOnForm(),
                "The login form must be gone once the credentials were accepted");

        BottomNavBar nav = new BottomNavBar(driver);
        Assert.assertTrue(nav.isClientShell(),
                "A verified client should land on the client bottom nav "
                        + "(Home/Book/Appointments/Shop/Profile)");
        // Landing in *a* shell is not the case; landing in the CLIENT one is. user_type_id drives
        // the routing, so a role regression shows up here as the wrong tab set, not as a crash.
        Assert.assertFalse(nav.isProfessionalShell(),
                "A client must not be routed into the professional dashboard");
    }

    /** AUTH-033 — a password must never be readable over the user's shoulder by default. */
    @Test(description = "The password field masks its input")
    public void passwordIsMasked() {
        LoginScreen form = openLoginForm();
        form.enterPassword("Trimio@2580");

        Assert.assertTrue(form.isPasswordMasked(),
                "The password field must mask input by default");
        Assert.assertFalse(form.isEmailMasked(),
                "The email field must NOT be masked — without this the masking assertion above "
                        + "would also pass on a platform that reported every field as a password");
    }

    /** AUTH-035 — the recovery entry point. */
    @Test(description = "'Forgot password?' opens the reset role page")
    public void forgotPasswordOpensResetRolePage() {
        LoginScreen form = openLoginForm();

        RoleSelectionScreen rolePage = form.goToForgotPassword();

        Assert.assertTrue(rolePage.isLoaded(), "The role page should open");
        // "in reset mode" was in the description but in no assertion: isLoaded() is true for the
        // signup variant too, so this test could not fail if the wrong one opened.
        Assert.assertTrue(rolePage.isResetVariant(),
                "The reset variant should offer the admin card, which signup does not");
    }

    /** AUTH-036 — the signup entry point from login. */
    @Test(description = "'Register' returns to the signup flow")
    public void registerLinkReturnsToSignup() {
        LoginScreen form = openLoginForm();

        RoleSelectionScreen rolePage = form.goToRegister();

        Assert.assertTrue(rolePage.isLoaded(), "The role page should open");
        Assert.assertTrue(rolePage.isSignupVariant(),
                "Signup must NOT offer the admin card — that card belongs to the reset flow only");
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

        // Assert the prompt was actually THERE. dismissBiometricPromptIfPresent() is a no-op when
        // it is not, so without this the test passed just as happily on a build that never offered
        // biometrics at all — asserting nothing about declining.
        Assert.assertTrue(form.dismissBiometricPromptIfPresent(),
                "The '" + LoginScreen.BIOMETRIC_PROMPT + "' opt-in should be shown after a first "
                        + "successful sign-in — there was nothing to decline");

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
        form.dismissPostLoginModals();

        restartApp();

        LoginScreen relaunched = new LoginScreen(driver);
        Assert.assertFalse(relaunched.isLoaded(),
                "After a relaunch the app should restore the session, not ask for credentials again");
        // Not being on the login screen is also true of a splash that never finished and of a
        // crash. Restoring the session means the shell comes back — behind the same interstitials
        // a cold start puts in front of it, so drain those first or this asserts the modal.
        relaunched.dismissPostLoginModals();
        Assert.assertTrue(new BottomNavBar(driver).isClientShell(),
                "A restored session should land back in the client shell");
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
        form.dismissPostLoginModals();

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
        form.dismissPostLoginModals();

        new BottomNavBar(driver).open(BottomNavBar.CLIENT_PROFILE);
        ClientProfileScreen profile = new ClientProfileScreen(driver);
        if (!profile.hasLogoutControl()) {
            throw new SkipException("No logout control was reachable on the client account page.");
        }
        profile.logout();

        Assert.assertTrue(new LoginScreen(driver).isLoaded(),
                "Logging out should return to the login screen");
        Assert.assertFalse(new BottomNavBar(driver).isClientShell(),
                "The signed-in shell must be torn down by logout, not merely covered by it");

        restartApp();
        Assert.assertTrue(new LoginScreen(driver).isLoaded(),
                "After logout a relaunch must NOT restore the session");
    }

}
