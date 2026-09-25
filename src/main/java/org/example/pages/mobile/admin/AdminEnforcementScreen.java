package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Enforcements — {@code screens/Admin/Enforcements/admin_enforcement_list_page.dart}.
 *
 * <p>The register of safety holds: who is suspended, why, and for how long. Extensions are bounded
 * ("Choose duration"), and reinstating restores a user's access immediately — the action
 * a wrongly-suspended professional is waiting on, so it is worth asserting that it actually clears
 * both the record and the console counter.
 */
public class AdminEnforcementScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String EXTEND = "Extend";
    public static final String REINSTATE = "Reinstate";
    public static final String REMOVE = "Remove";
    public static final String CONFIRM = "Confirm";
    public static final String CANCEL = "Cancel";
    public static final String CHOOSE_DURATION = "Choose duration";
    public static final String SELECT_EXTENSION_DAYS = "Select extension days";
    /**
     * A hold's row, anchored on the word every record ends with: "Until 2026-10-07T12:07:22.081Z".
     *
     * <p>Was {@code "Reason:"}, which the register never prints. A row reads
     * "Leila Rivera | Active | SUSPEND | (bullet) Test fixture: repeated no-shows | risk=0.0 (ok) |
     * Until ..." -- the reason follows a U+2022 bullet with no label, and a {@code UiSelector}
     * could not have matched that bullet either. So with three holds on screen and the console
     * counting "Enforcements | 3 active", the register reported itself empty.
     */
    public static final String RECORD_ANCHOR = "Until ";
    /** The action a hold names, and the segment the reason follows. */
    public static final String ACTION_SUSPEND = "SUSPEND";

    public AdminEnforcementScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresentAfterScroll(RECORD_ANCHOR) || isPresentAfterScroll(REINSTATE)
                || isPresent(descContains("Enforcement"), Duration.ofSeconds(20));
    }

    /**
     * True if at least one enforcement record is listed.
     *
     * <p>Waits for the register to render before answering. {@link #records()} is an immediate
     * read, so asking it the moment the screen opens answers "empty" for a list that is merely
     * still loading. That is not hypothetical: 90 minutes into a run, on a device slow enough
     * that the frame had not arrived within the tap, a register holding three holds reported
     * itself empty and the test skipped saying there were "no active enforcements in this
     * environment" — while the console tile behind it read "Enforcements | 3 active".
     *
     * <p>A genuinely empty register still answers false; it just costs the wait first, which is
     * the right trade for a question whose wrong answer is silently skipped coverage.
     */
    public boolean hasAnyEnforcement() {
        isPresent(descContains(RECORD_ANCHOR), Duration.ofSeconds(20));
        return !records().isEmpty() || isPresentAfterScroll(REINSTATE);
    }

    /**
     * True when EVERY listed hold explains itself.
     *
     * <p>Checked structurally rather than by matching a label, because there is no label: the row
     * merges name, status, action, reason, risk and expiry into one node. So the test reads each
     * record and asserts there is something between the action and the risk -- which is what the
     * assertion actually means, and which keeps working when the copy changes.
     *
     * <p>Deliberately fails on an empty register rather than passing vacuously: "every hold states
     * a reason" is not satisfied by having no holds.
     */
    public boolean showsReasons() {
        java.util.List<String> rows = records();
        if (rows.isEmpty()) {
            return false;
        }
        for (String row : rows) {
            if (reasonIn(row).isEmpty()) {
                LOG.warn("Enforcement record carries no reason: {}", row.replace("\n", " | "));
                return false;
            }
        }
        return true;
    }

    /** The content-desc of every enforcement row on screen. */
    private java.util.List<String> records() {
        java.util.List<String> rows = new java.util.ArrayList<>();
        for (org.openqa.selenium.WebElement e : findAll(descContains(RECORD_ANCHOR))) {
            String desc = e.getAttribute("content-desc");
            if (desc != null && desc.contains(ACTION_SUSPEND)) {
                rows.add(desc);
            }
        }
        return rows;
    }

    /**
     * The reason out of one row: the segment after the action and before the risk.
     *
     * <p>The leading bullet is stripped by position rather than matched, since it is non-ASCII.
     */
    private static String reasonIn(String row) {
        String[] parts = row.split("\n");
        for (int i = 0; i < parts.length; i++) {
            if (!ACTION_SUSPEND.equalsIgnoreCase(parts[i].trim())) {
                continue;
            }
            for (int j = i + 1; j < parts.length; j++) {
                String candidate = parts[j].trim();
                if (candidate.startsWith("risk=") || candidate.startsWith("Until ")) {
                    break;
                }
                // Drop a leading bullet/punctuation, keep the sentence.
                String text = candidate.replaceAll("^[^\\p{Alnum}]+", "").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    // ---- actions ------------------------------------------------------------

    /**
     * Opens the first hold's detail page.
     *
     * <p>The register is a list of rows and nothing else -- no per-row actions. Confirm, Extend,
     * Remove and Reinstate all live on "Enforcement Detail", one tap in, which is also the only
     * place the "Reason:" label appears. Tapping Extend straight from the list therefore waited
     * 30 seconds for a button that was never on that screen.
     */
    public AdminEnforcementScreen openFirstRecord() {
        java.util.List<org.openqa.selenium.WebElement> rows = findAll(descContains(RECORD_ANCHOR));
        if (!rows.isEmpty()) {
            rows.get(0).click();
        }
        return this;
    }

    /** True once a hold's detail page is showing. */
    public boolean isRecordOpen() {
        return isPresent(descContains(EXTEND), Duration.ofSeconds(10));
    }

    /** Opens the extend dialog, stepping into the record first when the list is showing. */
    public AdminEnforcementScreen tapExtend() {
        if (!isRecordOpen()) {
            openFirstRecord();
        }
        scrollAndTap(EXTEND);
        return this;
    }

    /** True when the extension dialog states its 1–30 day bound. */
    public boolean showsDurationBounds() {
        return isPresent(descContains("Choose duration"), Duration.ofSeconds(10))
                || isPresent(descContains(SELECT_EXTENSION_DAYS), Duration.ofSeconds(5));
    }

    /** Extends by a number of days and confirms. */
    public AdminEnforcementScreen extendBy(int days) {
        scrollAndTap("Extend " + days + " days");
        confirm();
        return this;
    }

    /** Reinstates the first suspended user. */
    public AdminEnforcementScreen reinstateFirst() {
        scrollAndTap(REINSTATE);
        confirm();
        return this;
    }

    /** Removes the first enforcement record. */
    public AdminEnforcementScreen removeFirst() {
        scrollAndTap(REMOVE);
        confirm();
        return this;
    }

    private void confirm() {
        if (isPresent(accId(CONFIRM), SHORT_TIMEOUT)) {
            tap(accId(CONFIRM));
        }
    }
}
