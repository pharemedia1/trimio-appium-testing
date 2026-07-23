package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;

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

    /** Opens a professional-status segment. */
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

    // ---- documents ----------------------------------------------------------

    /** True when the automated licence-check result (or its absence) is displayed. */
    public boolean showsAutomatedCheckResult() {
        return isPresentAfterScroll(AUTOMATED_CHECK)
                || isPresentAfterScroll(AUTO_CHECK_NOT_RUN)
                || isPresentAfterScroll(DMV_MATCH);
    }

    /** Approves the open document/licence. */
    public AdminUsersScreen approveDocument() {
        scrollAndTap(APPROVE);
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
