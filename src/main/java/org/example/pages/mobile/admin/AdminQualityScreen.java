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
    /** The appbar title — the screen's only reliable landmark. */
    public static final String TITLE = "Quality Control";
    /** Summary cards on the Quality page; each opens a status list. */
    public static final String CARD_WARNING = "Warning";
    public static final String CARD_SUSPENDED = "Suspended";
    public static final String CARD_DEACTIVATED = "Deactivated";
    /**
     * The search field's placeholder.
     *
     * @deprecated as a LOCATOR — it is a Flutter {@code hintText} and never reaches the
     *     accessibility tree. Kept because the copy is worth recording. Locate the field with
     *     {@code editText(0)}.
     */
    @Deprecated
    public static final String SEARCH_HINT = "Search by Email/ID";
    public static final String REFRESH = "Refresh";
    public static final String ACTIONS = "Actions";
    public static final String SUSPEND = "Suspend";
    public static final String DEACTIVATE = "Deactivate";
    public static final String REACTIVATE = "Reactivate";
    public static final String CONFIRM = "Confirm";
    public static final String CANCEL = "Cancel";
    public static final String SUBMIT = "Submit";
    public static final String ARE_YOU_SURE = "Are you sure?";
    /**
     * The refusal the app shows when a suspension is submitted with no reason.
     *
     * <p>Was {@code "Reason (required)"}, which is the dialog field's {@code hintText} -- a Flutter
     * hint never reaches the accessibility tree, so it could not have matched even while the
     * dialog was open. And the dialog does not stay open: submitting empty CLOSES it and raises a
     * SnackBar reading exactly "Reason is required". Verified on-device 2026-09-23, along with the
     * fact that the gate genuinely holds -- professional 1203 stayed on WARNING.
     */
    public static final String REASON_REQUIRED = "Reason is required";
    /** The dialog's title, e.g. "Suspend #1203". The reason field itself is editText(0). */
    public static final String SUSPEND_DIALOG = "Suspend #";
    public static final String REASON_PREFIX = "Reason:";
    /**
     * A professional's row inside a status list, e.g. "Pro #1212 | SUSPENDED | ID 1212 ...".
     *
     * <p>The list is rows only. {@link #ACTIONS} and the buttons under it live on the
     * professional's own page, one tap in, which is why looking for "Actions" on the list found
     * nothing and every caller concluded the list was empty.
     */
    public static final String PROFESSIONAL_ROW = "Pro #";

    public AdminQualityScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        // The APPBAR TITLE, not the search hint.
        //
        // SEARCH_HINT is a Flutter `hintText`, and a hint never becomes a content-desc: the field
        // arrives as a bare EditText with an empty one. The REFRESH fallback does not exist either
        // — admin_quality_page.dart's appBar carries only a conditional "Back" action. So this
        // method could never return true, and two tests failed with "Quality Control should
        // render" against a screen that was rendering.
        return isPresent(descContains(TITLE), Duration.ofSeconds(25))
                || isPresent(editText(0), SHORT_TIMEOUT);
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
    /**
     * Opens a status list from one of the Quality summary cards.
     *
     * <p>The "Actions" panel is <b>two hops</b> from the Quality page, and the page object went
     * looking for it on the first. {@code admin_quality_page.dart} shows summary cards — Warning,
     * Suspended, Deactivated — each of which opens {@code admin_status_list_page.dart}, and only
     * that page renders "Actions" alongside a professional row. Calling {@link #openActions()}
     * straight from Quality waited out a 30s timeout on a panel one screen away.
     *
     * @param statusCard one of {@link #CARD_WARNING}, {@link #CARD_SUSPENDED},
     *                   {@link #CARD_DEACTIVATED}
     */
    public AdminQualityScreen openStatusList(String statusCard) {
        LOG.info("AdminQuality: opening the '{}' status list", statusCard);
        scrollAndTap(statusCard);
        return this;
    }

    /** True once a status list is showing its Actions panel for a professional. */
    /**
     * Opens the first professional in the current status list.
     *
     * @return false when the list has no professional to open
     */
    public boolean openFirstProfessional() {
        java.util.List<org.openqa.selenium.WebElement> rows = findAll(descContains(PROFESSIONAL_ROW));
        if (rows.isEmpty()) {
            return false;
        }
        LOG.info("AdminQuality: opening {}", rows.get(0).getAttribute("content-desc"));
        rows.get(0).click();
        return true;
    }

    /** True once an Actions panel is reachable, stepping into the professional if needed. */
    public boolean statusListHasActions() {
        return isPresentAfterScroll(ACTIONS)
                || (openFirstProfessional() && isPresentAfterScroll(ACTIONS));
    }

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
