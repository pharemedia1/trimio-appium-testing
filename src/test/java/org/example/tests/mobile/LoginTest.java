package org.example.tests.mobile;

import org.example.base.MobileBaseTest;
import org.example.data.TestAccounts;
import org.example.dataproviders.TestDataProvider;
import org.example.pages.mobile.LoginScreen;
import org.testng.Assert;
import org.testng.SkipException;
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

    @Test(description = "A registered user logs in successfully with valid credentials")
    public void validCredentialsAreAccepted() {
        if (!TestAccounts.hasVerifiedAccount()) {
            throw new SkipException("Set verifiedAccount in test-accounts.json to run the "
                    + "positive login test.");
        }

        LoginScreen form = openLoginForm();
        form.login(TestAccounts.verifiedEmail(), TestAccounts.verifiedPassword());

        Assert.assertTrue(form.isLoginAccepted(),
                "Login should be accepted (form left) for: " + TestAccounts.verifiedEmail());
    }
}
