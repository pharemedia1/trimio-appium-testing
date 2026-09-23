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
    /** Shown only when the client HAS a membership. */
    public static final String CURRENT_PLAN = "Current plan:";
    /**
     * Shown instead when they do not — the profile offers the plans rather than naming one.
     *
     * <p>Verified on-device 2026-08-31 for a client with no membership: the section reads
     * "Choose Your Membership … Basic $12.99/mo … Standard $89/mo … Executive $139/mo" with a
     * "Select Plan" button, and no "Current plan:" anywhere. The old assertion assumed the plan
     * line rendered either way and said so in a comment; it does not.
     */
    public static final String CHOOSE_MEMBERSHIP = "Choose Your Membership";
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

    /** True when the membership plan line is rendered — i.e. this client HAS a membership. */
    public boolean showsCurrentPlan() {
        return isPresentAfterScroll(CURRENT_PLAN);
    }

    /** True when the profile offers plans to choose from — i.e. this client has NO membership. */
    public boolean showsMembershipChooser() {
        return isPresentAfterScroll(CHOOSE_MEMBERSHIP);
    }

    /**
     * True when the membership section rendered in either of its two legitimate forms.
     *
     * <p>The section is always present; which half shows depends on whether the client subscribes.
     * Asserting on one half alone makes the test a statement about the fixture rather than about
     * the screen — and asserting "the plan line OR the screen loaded" asserts nothing at all, while
     * quietly breaking itself: the first check scrolls, which pushes "Edit your details" out of
     * view, and Flutter drops the semantics of what is off-screen, so the fallback then fails too.
     */
    public boolean showsMembershipSection() {
        return showsCurrentPlan() || showsMembershipChooser();
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
    /**
     * The account page's logout row — {@code "Log out"}, with a space
     * ({@code accountPage.dart} {@code _logoutButton()}).
     *
     * <p>Not the same string as the dialog's button below, and the difference silently disabled
     * AUTH-039: this used to look for "Logout", which exists ONLY inside the confirmation dialog
     * — i.e. only after the dialog this control is meant to open. So {@code hasLogoutControl()}
     * was never true, and {@code logoutClearsSession} skipped on its own guard instead of ever
     * asserting that a logged-out session stays logged out across a cold start.
     */
    public static final String LOGOUT_TRIGGER = "Log out";
    public static final String LOGOUT_CONFIRM = "Logout Confirmation";
    /** The dialog's red confirm button. Exact: "Logout Confirmation" *contains* this text. */
    public static final String LOGOUT_CONFIRM_BUTTON = "Logout";

    /** True if a logout control is reachable on the account page. */
    public boolean hasLogoutControl() {
        return isPresentAfterScroll(LOGOUT_TRIGGER);
    }

    /** Logs out and confirms the "Logout Confirmation" dialog. */
    public ClientProfileScreen logout() {
        scrollAndTap(LOGOUT_TRIGGER);
        if (isPresent(descContains(LOGOUT_CONFIRM), SHORT_TIMEOUT)) {
            // accId, not descContains: the dialog TITLE ("Logout Confirmation") also contains
            // "Logout" and precedes the button in the tree, so a contains-match taps the title
            // and the dialog never closes.
            tap(accId(LOGOUT_CONFIRM_BUTTON));
        }
        return this;
    }

}
