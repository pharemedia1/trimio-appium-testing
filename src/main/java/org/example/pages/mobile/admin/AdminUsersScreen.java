package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.WebElement;

import java.time.Duration;

/**
 * All Users and the professional-status queues — {@code screens/Admin/all_users/*}.
 *
 * <p>Covers the users hub, the pending/approved/rejected/incomplete professional segments, the
 * per-professional detail (documents, automated licence checks) and the licence-verification queue,
 * which share the same approve/reject vocabulary.
 *
 * <p>The automated check is advisory, not authoritative: an admin sees "Automated check" /
 * "DMV attribute match" (or "Automated check not run") alongside the raw provider response and still
 * makes the call. Tests should therefore assert the result is <em>displayed</em>, never that a
 * particular verdict was reached.
 */
public class AdminUsersScreen extends MobileBasePage {

    // ---- segments -----------------------------------------------------------
    /**
     * All Users is a HUB, not the list.
     *
     * <p>Verified on-device: it offers two cards — "Clients · Registered customers" and
     * "Professionals · Approvals &amp; status" — and the approval segments live one level inside
     * the second. Every page-object method here that reached for "Pending" straight from the hub
     * was reaching for something that is not on that screen.
     */
    public static final String CARD_PROFESSIONALS = "Professionals";
    public static final String CARD_CLIENTS = "Clients";
    public static final String BY_APPROVAL_STATUS = "By approval status";

    public static final String SEGMENT_PENDING = "Pending";
    public static final String SEGMENT_APPROVED = "Approved";
    public static final String SEGMENT_REJECTED = "Rejected";
    public static final String SEGMENT_INCOMPLETE = "Incomplete";

    // ---- documents / licences ----------------------------------------------
    public static final String APPROVE = "Approve";
    public static final String REJECT = "Reject";
    public static final String REJECT_DOCUMENT = "Reject document";
    public static final String REJECT_SUBMISSION = "Reject submission";
    public static final String VIEW_DOCUMENT = "View document";
    public static final String VIEW_BACK_OF_DOCUMENT = "View back of document";
    public static final String RERUN_AUTO_CHECK = "Re-run auto-check";
    public static final String AUTOMATED_CHECK = "Automated check";
    public static final String AUTO_CHECK_NOT_RUN = "Automated check not run";
    public static final String DMV_MATCH = "DMV attribute match";
    public static final String RAW_RESPONSE = "Raw provider response";
    public static final String REASON_SHARED = "Reason (optional) — shared with the professional";
    public static final String REASON_SHOWN = "Reason (optional) — shown to the professional";
    public static final String CANCEL = "Cancel";
    public static final String SHOWING_PREFIX = "Showing ";

    public AdminUsersScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresentAfterScroll("clients") || isPresentAfterScroll("pros")
                || isPresent(descContains("Users"), Duration.ofSeconds(20));
    }

    /** True when both client and professional counts are rendered. */
    public boolean showsUserCounts() {
        return isPresentAfterScroll("clients") && isPresentAfterScroll("pros");
    }

    /** Opens the Professionals card — the segments live inside it, not on the All Users hub. */
    public AdminUsersScreen openProfessionals() {
        LOG.info("AdminUsers: opening the Professionals card");
        scrollAndTap(CARD_PROFESSIONALS);
        return this;
    }

    /** True once the approval-status segments are on screen. */
    public boolean isProfessionalsListLoaded() {
        return isPresentAfterScroll(BY_APPROVAL_STATUS);
    }

    /**
     * Opens a professional-status segment.
     *
     * <p>The segment tile merges its count into the label ("55\nPending"), so this matches on
     * contains rather than an exact id.
     */
    public AdminUsersScreen openSegment(String segment) {
        LOG.info("AdminUsers: opening the '{}' segment", segment);
        scrollAndTap(segment);
        return this;
    }

    /** True if the open segment lists at least one professional. */
    public boolean segmentHasEntries() {
        return isPresentAfterScroll("No.") || isPresentAfterScroll("@");
    }

    /** Opens the first professional in the list. */
    public AdminUsersScreen openFirstProfessional() {
        scrollAndTap("@");
        return this;
    }

    /**
     * Opens a NAMED professional, by the email shown in the row.
     *
     * <p>The approval test must act on the subject it created, not on whoever happens to sit at
     * the top of a queue that other people's data shares.
     */
    public AdminUsersScreen openProfessional(String email) {
        LOG.info("AdminUsers: opening '{}'", email);
        // Two separate hazards, so this both settles AND re-checks:
        //   1. tapping a segment does not render its list synchronously, so a scroll issued
        //      immediately searches an empty list and finds nothing;
        //   2. scrollIntoView reporting success does NOT mean the row is still addressable a
        //      moment later — it can settle back out of view, and Flutter drops the semantics of
        //      anything off-screen, so a plain tap() then waits 30s for a node that is gone.
        // Hence: look, scroll if absent, look again, and only tap while it is genuinely there.
        By row = descContains(email);
        for (int attempt = 0; attempt < 6; attempt++) {
            if (isPresent(row, Duration.ofSeconds(3))) {
                tap(row);
                return this;
            }
            scrollToDesc(email);
        }
        throw new IllegalStateException("'" + email + "' never stayed on screen long enough to "
                + "open — it may be under a different approval status, or below the scroll "
                + "depth the search allows");
    }

    /** True if this professional's row/detail is reachable in the open segment. */
    public boolean listsProfessional(String email) {
        return isPresentAfterScroll(email);
    }

    // ---- documents ----------------------------------------------------------

    /** True when the automated licence-check result (or its absence) is displayed. */
    public boolean showsAutomatedCheckResult() {
        return isPresentAfterScroll(AUTOMATED_CHECK)
                || isPresentAfterScroll(AUTO_CHECK_NOT_RUN)
                || isPresentAfterScroll(DMV_MATCH);
    }

    /** True when the open document offers a decision — both Approve and Reject. */
    public boolean canDecideOnDocument() {
        return isPresentAfterScroll(APPROVE) && isPresentAfterScroll(REJECT);
    }

    /**
     * Approves the open document/licence.
     *
     * <p>Taps via {@code mobile: clickGesture} on the ELEMENT rather than
     * {@code element.click()} or a coordinate. Measured on this screen: the button is built
     * correctly and live — a trace inside the widget showed a valid id and {@code busy=false},
     * so {@code onPressed} was not null — yet neither a Selenium click nor a tap at the centre
     * of the reported bounds ever reached the handler. Appium computes this gesture from the
     * element's position at the moment of the tap, which is what the other two were getting
     * wrong after the list had scrolled.
     */
    public AdminUsersScreen approveDocument() {
        if (!isPresentAfterScroll(APPROVE)) {
            throw new IllegalStateException("No '" + APPROVE + "' control on screen — a document "
                    + "must be submitted before there is anything to approve");
        }
        WebElement button = find(descContains(APPROVE));
        LOG.info("AdminUsers: approving the open document");
        driver.executeScript("mobile: clickGesture",
                java.util.Map.of("elementId", ((RemoteWebElement) button).getId()));
        return this;
    }

    /** Rejects the open document/licence with a reason shared with the professional. */
    public AdminUsersScreen rejectDocument(String reason) {
        scrollAndTap(REJECT);
        type(editText(0), reason);
        hideKeyboard();
        tap(accId(REJECT));
        return this;
    }

    /** Re-runs the automated provider check. */
    public AdminUsersScreen rerunAutoCheck() {
        scrollAndTap(RERUN_AUTO_CHECK);
        return this;
    }

    /** True when the reviews list paginates ("Showing n of total"). */
    public boolean showsReviewPagination() {
        return isPresentAfterScroll(SHOWING_PREFIX);
    }
}
