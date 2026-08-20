package org.example.base;

import io.appium.java_client.android.AndroidDriver;
import org.example.data.TestAccounts;
import org.example.factory.AppiumDriverFactory;
import org.example.pages.mobile.OnboardingScreen;
import org.example.pages.mobile.RegistrationScreen;
import org.testng.annotations.BeforeClass;

/**
 * Base for mobile tests that need a pre-existing registered account (forgot-password / OTP /
 * reset-password flows). Registers one real account in {@code @BeforeClass} using a dedicated,
 * short-lived driver session — so account creation is isolated from the test methods (reliable,
 * and not counted against per-test timing). The account persists server-side and every test
 * method in the class reuses {@link #accountEmail}.
 */
public abstract class RegisteredAccountTest extends MobileBaseTest {

    /** The only state currently enabled for signup with published documents. */
    protected static final String SIGNUP_STATE = "Texas";

    /** Email of the account registered for this class. */
    protected String accountEmail;

    @BeforeClass(alwaysRun = true)
    public void createTestAccount() {
        AndroidDriver setup = openSessionOrSkip();
        try {
            String email = TestAccounts.newClientEmail();
            RegistrationScreen reg = new OnboardingScreen(setup).goToRegister().chooseClient();
            // Registration now requires a state and every published document read — see
            // RegistrationScreen#register. Texas is the only state currently open for signup.
            reg.register(email, TestAccounts.defaultPhone(), TestAccounts.genericPassword(),
                    SIGNUP_STATE);
            if (!reg.isRegistrationSuccessful()) {
                throw new IllegalStateException("Setup registration failed for " + email);
            }
            accountEmail = email;
        } finally {
            AppiumDriverFactory.quitDriver();
        }
    }
}
