package org.example.pages.mobile.client;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * The client Profile tab — {@code screens/client/profile/accountPage.dart} plus the profile editor
 * and family-members screens.
 *
 * <p>Beyond the obvious account fields this screen owns two things worth guarding: the biometric
 * ("fingerprint") login toggle, whose failure mode is a device without enrolled biometrics, and the
 * unsaved-changes guard on the editor ("Discard changes?"), which is the only thing standing between
 * a mistyped address and a pro driving to the wrong house.
 */
public class ClientProfileScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String EDIT_DETAILS = "Edit your details";
    public static final String CURRENT_PLAN = "Current plan:";
    public static final String BIOMETRICS_ENABLED = "Fingerprint login enabled";
    public static final String BIOMETRICS_DISABLED = "Fingerprint login disabled";
    public static final String BIOMETRICS_UNAVAILABLE =
            "Biometric authentication is not available on this device";
    public static final String DISCARD_CHANGES = "Discard changes?";
    public static final String KEEP_EDITING = "Keep editing";
    public static final String ADD_FAMILY_MEMBER = "Add someone";
    public static final String REMOVE_PERSON = "Remove this person?";

    public ClientProfileScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(EDIT_DETAILS), Duration.ofSeconds(25));
    }

    /** True when the membership plan line is rendered on the profile. */
    public boolean showsCurrentPlan() {
        return isPresentAfterScroll(CURRENT_PLAN);
    }

    /** Opens the profile editor. */
    public ClientProfileScreen editDetails() {
        scrollAndTap(EDIT_DETAILS);
        return this;
    }

    /** Types a new value into the n-th editor field. */
    public ClientProfileScreen setField(int index, String value) {
        type(editText(index), value);
        hideKeyboard();
        return this;
    }

    /** Saves the editor. */
    public ClientProfileScreen save() {
        scrollAndTap("Save");
        return this;
    }

    /** True when the unsaved-changes guard fired. */
    public boolean showsDiscardPrompt() {
        return isPresent(descContains(DISCARD_CHANGES), Duration.ofSeconds(10));
    }

    /** Returns to the editor from the discard prompt. */
    public ClientProfileScreen keepEditing() {
        tap(descContains(KEEP_EDITING));
        return this;
    }

    // ---- biometrics ---------------------------------------------------------

    /** Toggles the first switch on the profile (the fingerprint-login toggle). */
    public ClientProfileScreen toggleBiometrics() {
        tap(checkable(0));
        return this;
    }

    public boolean showsBiometricsEnabled() {
        return isPresent(descContains(BIOMETRICS_ENABLED), Duration.ofSeconds(10));
    }

    public boolean showsBiometricsDisabled() {
        return isPresent(descContains(BIOMETRICS_DISABLED), Duration.ofSeconds(10));
    }

    public boolean showsBiometricsUnavailable() {
        return isPresent(descContains(BIOMETRICS_UNAVAILABLE), Duration.ofSeconds(10));
    }

    // ---- family members -----------------------------------------------------

    /** Opens the add-family-member form. */
    public ClientProfileScreen addFamilyMember() {
        scrollAndTap(ADD_FAMILY_MEMBER);
        return this;
    }

    public boolean hasFamilyMember(String name) {
        return isPresentAfterScroll(name);
    }

    /** Removes a family member and confirms the prompt. */
    public ClientProfileScreen removeFamilyMember() {
        scrollAndTap("Remove");
        tap(descContains("Remove"));
        return this;
    }

    public boolean showsRemovePrompt() {
        return isPresent(descContains(REMOVE_PERSON), Duration.ofSeconds(10));
    }

    // ---- session (AUTH-039) -------------------------------------------------
    public static final String LOGOUT = "Logout";
    public static final String LOGOUT_CONFIRM = "Logout Confirmation";

    /** True if a logout control is reachable on the account page. */
    public boolean hasLogoutControl() {
        return isPresentAfterScroll(LOGOUT);
    }

    /** Logs out and confirms the "Logout Confirmation" dialog. */
    public ClientProfileScreen logout() {
        scrollAndTap(LOGOUT);
        if (isPresent(descContains(LOGOUT_CONFIRM), SHORT_TIMEOUT)) {
            tap(descContains(LOGOUT));
        }
        return this;
    }

}
