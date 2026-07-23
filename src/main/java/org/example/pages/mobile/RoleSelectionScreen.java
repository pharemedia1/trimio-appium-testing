package org.example.pages.mobile;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

/**
 * Role chooser reached from "Register" (WantUserRolePage) — REDESIGNED.
 * Each role opens the same registration form with a different {@code isUser} value.
 *
 * <p>Cards (content-desc): "I'm a Client" / "I'm a Professional". "I'm an Admin" now appears
 * ONLY in the forgot-password flow; the "Support Team" card was removed in the redesign.
 */
public class RoleSelectionScreen extends MobileBasePage {

    // Each role card's label Text MERGES with its subtitle into one content-desc
    // (e.g. "I'm a Client\nBook beauty & grooming services at home"), so match by
    // substring rather than exact id.
    private final By client = descContains("I'm a Client");
    private final By professional = descContains("I'm a Professional");
    private final By admin = descContains("I'm an Admin");

    public RoleSelectionScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(client);
    }

    public RegistrationScreen chooseClient() {
        LOG.info("Role page: choosing Client");
        tap(client);
        return new RegistrationScreen(driver);
    }

    public RegistrationScreen chooseProfessional() {
        LOG.info("Role page: choosing Professional");
        tap(professional);
        return new RegistrationScreen(driver);
    }

    // ---- forgot-password flow (same page, different destination) ------------
    // When reached via Login → "Forgot password?", picking a role opens the
    // forgot-password form instead of registration. "I'm an Admin" only exists here.

    public ForgotPasswordScreen chooseAdminForReset() {
        LOG.info("Role page: choosing Admin (reset flow)");
        tap(admin);
        return new ForgotPasswordScreen(driver);
    }

    public ForgotPasswordScreen chooseClientForReset() {
        LOG.info("Role page: choosing Client (reset flow)");
        tap(client);
        return new ForgotPasswordScreen(driver);
    }

    public ForgotPasswordScreen chooseProfessionalForReset() {
        tap(professional);
        return new ForgotPasswordScreen(driver);
    }
}
