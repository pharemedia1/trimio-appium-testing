package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Quality control — {@code screens/Admin/Quality/admin_quality_page.dart} and
 * {@code admin_status_list_page.dart}.
 *
 * <p>Where an admin suspends, deactivates or reinstates a user. Every destructive action here is
 * gated twice: an "Are you sure?" confirmation and a mandatory free-text reason. The reason is not
 * decoration — it is what the suspended user and the enforcement record later show, so a test that
 * only checks the happy path misses the point. {@link #submitWithoutReason()} exercises the gate.
 */
public class AdminQualityScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String SEARCH_HINT = "Search by Email/ID…";
    public static final String REFRESH = "Refresh";
    public static final String ACTIONS = "Actions";
    public static final String SUSPEND = "Suspend";
    public static final String DEACTIVATE = "Deactivate";
    public static final String REACTIVATE = "Reactivate";
    public static final String CONFIRM = "Confirm";
    public static final String CANCEL = "Cancel";
    public static final String SUBMIT = "Submit";
    public static final String ARE_YOU_SURE = "Are you sure?";
    public static final String REASON_REQUIRED = "Reason (required)";
    public static final String REASON_PREFIX = "Reason:";

    public AdminQualityScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(SEARCH_HINT), Duration.ofSeconds(25))
                || isPresent(accId(REFRESH), Duration.ofSeconds(10));
    }

    /** Searches by email or user id. */
    public AdminQualityScreen search(String emailOrId) {
        LOG.info("AdminQuality: searching '{}'", emailOrId);
        type(editText(0), emailOrId);
        hideKeyboard();
        return this;
    }

    /** True if a row matching the query is listed. */
    public boolean hasResult(String text) {
        return isPresentAfterScroll(text);
    }

    /** Reloads the list. */
    public AdminQualityScreen refresh() {
        tap(accId(REFRESH));
        return this;
    }

    // ---- actions ------------------------------------------------------------

    /** Opens the action menu for the first listed user. */
    public AdminQualityScreen openActions() {
        scrollAndTap(ACTIONS);
        return this;
    }

    /** Opens the suspend dialog. */
    public AdminQualityScreen tapSuspend() {
        scrollAndTap(SUSPEND);
        return this;
    }

    /** Attempts to submit the reason dialog with an empty reason (negative path). */
    public AdminQualityScreen submitWithoutReason() {
        tap(accId(SUBMIT));
        return this;
    }

    /** Fills the mandatory reason and submits. */
    public AdminQualityScreen submitWithReason(String reason) {
        type(editText(0), reason);
        hideKeyboard();
        tap(accId(SUBMIT));
        return this;
    }

    /** True when the mandatory-reason field is still on screen (i.e. the submission was refused). */
    public boolean stillAsksForReason() {
        return isPresent(descContains(REASON_REQUIRED), Duration.ofSeconds(8));
    }

    /** Deactivates the selected user, confirming the prompt. */
    public AdminQualityScreen deactivate() {
        scrollAndTap(DEACTIVATE);
        confirm();
        return this;
    }

    /** Reactivates the selected user. */
    public AdminQualityScreen reactivate() {
        scrollAndTap(REACTIVATE);
        confirm();
        return this;
    }

    /** Accepts an "Are you sure?" prompt. */
    public AdminQualityScreen confirm() {
        if (isPresent(accId(CONFIRM), SHORT_TIMEOUT)) {
            tap(accId(CONFIRM));
        }
        return this;
    }

    public boolean showsConfirmationPrompt() {
        return isPresent(descContains(ARE_YOU_SURE), Duration.ofSeconds(10));
    }

    /** True when a suspension reason is displayed on the row. */
    public boolean showsSuspensionReason() {
        return isPresentAfterScroll(REASON_PREFIX);
    }
}
