package org.example.pages.mobile;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Welcome / onboarding screen (onboarding_screen.dart) — REDESIGNED.
 *
 * <p>It is now a 3-slide carousel (PageView) rather than a single screen with
 * "Login"/"Register" buttons. Selectors are derived from the redesigned Dart source:
 * <ul>
 *   <li>Slide titles (Text) — "Welcome to Trimio" / "Top pros near you" / "Seamless &amp; secure"</li>
 *   <li>"Skip" — top-right {@code TextButton}, jumps straight to Login</li>
 *   <li>"Next" — {@code PrimaryButton} that advances the carousel; on the last slide its
 *       label becomes "Get started", which starts the register flow (role page)</li>
 *   <li>"Sign in" — bottom {@code GestureDetector} ("Already have an account?"), opens Login</li>
 * </ul>
 *
 * <p>NOTE: derived from source, not yet re-verified on-device. The CTA label flips from
 * "Next" to "Get started" only on the final slide.
 */
public class OnboardingScreen extends MobileBasePage {

    // ---- locators -----------------------------------------------------------
    private final By skipLink = accId("Skip");
    private final By signInLink = accId("Sign in");
    private final By nextButton = accId("Next");
    private final By getStartedButton = accId("Get started");
    // The slide's title Text MERGES with its description into one content-desc
    // (Flutter Semantics merge), so match by substring rather than exact id.
    private final By firstSlideTitle = descContains("Welcome to Trimio");

    public OnboardingScreen(AndroidDriver driver) {
        super(driver);
    }

    /** True once the carousel is up — "Skip" is present on every slide. */
    public boolean isLoaded() {
        return isPresent(skipLink);
    }

    /** True only on the first slide (welcome copy visible). */
    public boolean isOnFirstSlide() {
        return isPresent(firstSlideTitle, Duration.ofSeconds(3));
    }

    // ---- carousel navigation -----------------------------------------------

    /** Advances one slide ("Next"). */
    public OnboardingScreen tapNext() {
        LOG.info("Onboarding: tapping Next");
        tap(nextButton);
        return this;
    }

    /**
     * Walks the carousel to the last slide and taps "Get started" → opens the
     * role-selection page (registration flow).
     */
    public RoleSelectionScreen goToRegister() {
        LOG.info("Onboarding: advancing to last slide then Get started");
        // "Next" disappears on the final slide (its label becomes "Get started").
        while (isPresent(nextButton, Duration.ofSeconds(2))) {
            tap(nextButton);
        }
        tap(getStartedButton);
        return new RoleSelectionScreen(driver);
    }

    /** Taps the top-right "Skip" → opens the Login form directly. */
    public LoginScreen tapSkip() {
        LOG.info("Onboarding: tapping Skip");
        tap(skipLink);
        return new LoginScreen(driver);
    }

    /** Taps the bottom "Sign in" link → opens the Login form. */
    public LoginScreen goToLogin() {
        LOG.info("Onboarding: tapping Sign in");
        tap(signInLink);
        return new LoginScreen(driver);
    }

    // ---- privacy policy (AUTH-009) -----------------------------------------
    public static final String PRIVACY_TITLE = "Privacy Policy";
    public static final String PRIVACY_OPEN = "Open Privacy Policy";
    public static final String PRIVACY_FAILED = "Failed to load privacy policy";
    public static final String PRIVACY_UNSUPPORTED = "Unsupported document format";

    /**
     * Opens the Privacy Policy if a link to it is reachable from the current screen.
     *
     * @return false when no link is present, so the caller can skip rather than fail — the entry
     *         point differs between builds and its absence is not a policy-rendering defect
     */
    public boolean openPrivacyPolicy() {
        if (!isPresentAfterScroll(PRIVACY_TITLE)) {
            return false;
        }
        scrollAndTap(PRIVACY_TITLE);
        return true;
    }

    /**
     * True when the policy actually rendered.
     *
     * <p>Deliberately asserts the absence of the failure states as well as the presence of the
     * screen: {@code privacy_policy_screen.dart} shows its AppBar title even when the document
     * behind it fails to load, so a title-only check would pass over a blank policy.
     */
    public boolean privacyPolicyRendered() {
        boolean onScreen = isPresent(descContains(PRIVACY_TITLE), java.time.Duration.ofSeconds(20))
                || isPresent(descContains(PRIVACY_OPEN), java.time.Duration.ofSeconds(10));
        boolean failed = isPresent(descContains(PRIVACY_FAILED), SHORT_TIMEOUT)
                || isPresent(descContains(PRIVACY_UNSUPPORTED), SHORT_TIMEOUT);
        return onScreen && !failed;
    }

}
