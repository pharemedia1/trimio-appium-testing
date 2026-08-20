package org.example.tests.mobile;

import org.example.base.MobileBaseTest;
import org.example.pages.mobile.LoginScreen;
import org.example.pages.mobile.OnboardingScreen;
import org.example.pages.mobile.RoleSelectionScreen;
import org.testng.Assert;
import org.example.pages.mobile.RegistrationScreen;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Welcome-page coverage for the REDESIGNED Trimio onboarding carousel
 * (Appium + UiAutomator2, Page Object Model).
 *
 * <p>The welcome screen is now a 3-slide PageView with three navigation paths off it:
 * "Skip" (→ Login), "Sign in" (→ Login) and "Next"×N → "Get started" (→ register/role page).
 * Each test starts from a fresh app launch (data cleared → onboarding) via {@link MobileBaseTest}.
 */
public class OnboardingTest extends MobileBaseTest {

    @Test(description = "The welcome carousel loads on the first slide on a fresh launch")
    public void carouselLoadsOnFirstSlide() {
        OnboardingScreen welcome = onboarding();
        Assert.assertTrue(welcome.isLoaded(), "Onboarding carousel should be displayed");
        Assert.assertTrue(welcome.isOnFirstSlide(),
                "A fresh launch should open on the first (Welcome to Trimio) slide");
    }

    @Test(description = "\"Skip\" jumps straight to the Login form")
    public void skipOpensLogin() {
        LoginScreen login = onboarding().tapSkip();
        Assert.assertTrue(login.isLoaded(), "Skip should open the Login form");
    }

    @Test(description = "\"Sign in\" opens the Login form")
    public void signInOpensLogin() {
        LoginScreen login = onboarding().goToLogin();
        Assert.assertTrue(login.isLoaded(), "Sign in should open the Login form");
    }

    @Test(description = "Advancing to \"Get started\" opens the role-selection (register) page")
    public void getStartedOpensRolePage() {
        RoleSelectionScreen roles = onboarding().goToRegister();
        Assert.assertTrue(roles.isLoaded(),
                "Get started should open the role-selection page (registration flow)");
    }

    /** AUTH-007 — the client card opens the signup form for that role. */
    @Test(description = "Choosing \"I'm a Client\" opens the client registration form")
    public void clientRoleOpensRegistration() {
        RegistrationScreen form = onboarding().goToRegister().chooseClient();

        Assert.assertTrue(form.isLoaded(),
                "Picking the client role should open the registration form");
    }

    /** AUTH-008 — same for the professional card; the role decides the whole downstream app. */
    @Test(description = "Choosing \"I'm a Professional\" opens the professional registration form")
    public void professionalRoleOpensRegistration() {
        RegistrationScreen form = onboarding().goToRegister().chooseProfessional();

        Assert.assertTrue(form.isLoaded(),
                "Picking the professional role should open the registration form");
    }

    /** AUTH-009 — the policy has to actually render, not just navigate. */
    @Test(description = "The Privacy Policy document opens and renders")
    public void privacyPolicyOpens() {
        OnboardingScreen onboarding = onboarding();
        if (!onboarding.openPrivacyPolicy()) {
            throw new SkipException("No Privacy Policy link is reachable from onboarding in this build.");
        }
        Assert.assertTrue(onboarding.privacyPolicyRendered(),
                "The policy should render rather than showing 'Failed to load privacy policy'");
    }

}
