package org.example.tests.web;

import org.example.base.WebBaseTest;
import org.example.pages.web.VendorApplyPage;
import org.example.pages.web.WebLoginPage;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The public vendor application — "Want to sell on Trimio? Apply to sell".
 *
 * <p>The only unauthenticated write path in the portal. Everything submitted here is queued for an
 * admin to read, so its validation is the boundary between the open internet and the admin console —
 * which is why the negative case matters at least as much as the happy one.
 *
 * <p>Actually submitting an application is disabled by default: each run would add a row an
 * administrator has to triage.
 */
public class VendorApplyTest extends WebBaseTest {

    @Test(description = "The apply entry point opens the application form")
    public void applyEntryPointOpensForm() {
        WebLoginPage login = openPortal();
        Assert.assertTrue(login.isLoaded(), "The portal login should render");

        VendorApplyPage apply = login.openVendorApplication();

        Assert.assertTrue(apply.isLoaded(),
                "'Apply to sell' should open the '" + VendorApplyPage.TITLE + "' form");
    }

    @Test(description = "Required application fields are enforced")
    public void requiredFieldsAreEnforced() {
        VendorApplyPage apply = openPortal().openVendorApplication();
        Assert.assertTrue(apply.isLoaded(), "The application form should render");

        apply.submit();

        Assert.assertTrue(apply.stillOnForm(),
                "An empty application must not be accepted — this form feeds straight into the "
                        + "admin queue");
        Assert.assertFalse(apply.showsReceived(), "No application should be recorded");
    }

    @Test(description = "'Back to sign in' returns to the login screen")
    public void backToSignInReturns() {
        VendorApplyPage apply = openPortal().openVendorApplication();
        Assert.assertTrue(apply.isLoaded(), "The application form should render");

        Assert.assertTrue(apply.backToSignIn().isLoaded(),
                "'Back to sign in' should return to the portal login");
    }

    @Test(description = "A complete application is accepted", enabled = false)
    public void applicationCanBeSubmitted() {
        // Disabled by default: every run adds a pending application for an admin to triage.
        VendorApplyPage apply = openPortal().openVendorApplication();

        long unique = System.currentTimeMillis();
        apply.fill("Automation Store " + unique,
                        "trimiotest+vendor" + unique + "@gmail.com",
                        "Submitted by the Trimio automation suite.")
                .submit();

        Assert.assertTrue(apply.showsReceived(),
                "A complete application should confirm with '" + VendorApplyPage.RECEIVED + "'");
    }
}
