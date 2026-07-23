package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;

import java.time.Duration;

/**
 * The smaller admin queues that share one shape — a list, a detail and a decision:
 * Reports ({@code screens/Admin/Reports/*}), Services
 * ({@code screens/Admin/services/admin_all_services_page.dart}) and Countries &amp; States
 * ({@code screens/Admin/all_users/admin_states_page.dart} / {@code admin_state_detail_page.dart}).
 *
 * <p>They are grouped in one page object deliberately: each exposes only a handful of controls, and
 * splitting them into three near-empty classes would add navigation cost without adding clarity.
 *
 * <p>States is the one with real teeth — approving a state is what makes Trimio operable there
 * ("Approving makes … "), and the law-watch alerts attached to it are compliance signals an admin is
 * expected to review rather than dismiss blindly.
 */
public class AdminQueuesScreen extends MobileBasePage {

    // ---- reports ------------------------------------------------------------
    public static final String OPEN_SUFFIX = " open";
    public static final String SUPPORT_TICKETS = "support tickets";

    // ---- refunds (support console) ------------------------------------------
    public static final String ALL_REFUND_REQUESTS = "All Refund Requests";
    public static final String REFUND = "REFUND";
    public static final String CANCEL_REFUND = "CANCEL";
    public static final String REFUND_PROCESSED = "Refund processed";
    public static final String REQUEST_CANCELED = "Request canceled";

    // ---- states -------------------------------------------------------------
    public static final String STATES_ACTIVE_SUFFIX = "states active";
    public static final String NO_STATES = "No states for this country.";
    public static final String APPROVE = "Approve";
    public static final String LAW_WATCH = "Law watch";
    public static final String MARK_REVIEWED = "Mark reviewed";
    public static final String DISMISS = "Dismiss";
    public static final String RECHECK = "Re-check";
    public static final String LAST_CHECKED = "Last checked";
    public static final String ADD_SOURCE = "Add source";
    public static final String REMOVE_SOURCE = "Remove source?";
    public static final String REVIEW_TO_APPROVE = "Review to approve";
    public static final String TAP_TO_REVIEW = "Tap to review the law & documents";

    public AdminQueuesScreen(AndroidDriver driver) {
        super(driver);
    }

    // ---- reports ------------------------------------------------------------

    public boolean reportsLoaded() {
        return isPresentAfterScroll(SUPPORT_TICKETS) || isPresentAfterScroll("Report")
                || isPresent(descContains("Reports"), Duration.ofSeconds(20));
    }

    /** True if at least one report row is listed. */
    public boolean hasAnyReport() {
        return isPresentAfterScroll("Report") || isPresentAfterScroll("#");
    }

    /** Opens the first report. */
    public AdminQueuesScreen openFirstReport() {
        scrollAndTap("#");
        return this;
    }

    // ---- refunds ------------------------------------------------------------

    public boolean refundListLoaded() {
        return isPresentAfterScroll(ALL_REFUND_REQUESTS);
    }

    /** Processes the first refund request. */
    public AdminQueuesScreen processFirstRefund() {
        scrollAndTap(REFUND);
        return this;
    }

    public boolean showsRefundProcessed() {
        return isPresent(descContains(REFUND_PROCESSED), Duration.ofSeconds(20));
    }

    // ---- services -----------------------------------------------------------

    public boolean servicesLoaded() {
        return isPresent(descContains("Service"), Duration.ofSeconds(20));
    }

    /** True if a service with this name is in the catalogue. */
    public boolean hasService(String name) {
        return isPresentAfterScroll(name);
    }

    /** True if the catalogue rendered at least one row. */
    public boolean hasAnyService() {
        return isPresentAfterScroll("$") || isPresentAfterScroll("min");
    }

    // ---- states -------------------------------------------------------------

    public boolean statesLoaded() {
        return isPresentAfterScroll(STATES_ACTIVE_SUFFIX) || isPresentAfterScroll(NO_STATES)
                || isPresent(descContains("States"), Duration.ofSeconds(20));
    }

    /** True when the "<active> of <total> states active" summary is rendered. */
    public boolean showsActiveStateCount() {
        return isPresentAfterScroll(STATES_ACTIVE_SUFFIX);
    }

    public boolean showsNoStatesForCountry() {
        return isPresentAfterScroll(NO_STATES);
    }

    /** Opens a state by name. */
    public AdminQueuesScreen openState(String stateName) {
        scrollAndTap(stateName);
        return this;
    }

    /** True when the state has unreviewed law-watch alerts. */
    public boolean showsLawWatchAlerts() {
        return isPresentAfterScroll(LAW_WATCH);
    }

    /** Marks the law-watch alert reviewed. */
    public AdminQueuesScreen markLawWatchReviewed() {
        scrollAndTap(MARK_REVIEWED);
        return this;
    }

    /** Approves the open state, confirming the prompt. */
    public AdminQueuesScreen approveState() {
        scrollAndTap(APPROVE);
        if (isPresent(accId(APPROVE), SHORT_TIMEOUT)) {
            tap(accId(APPROVE));
        }
        return this;
    }

    /** Triggers a fresh compliance check for the state. */
    public AdminQueuesScreen recheckState() {
        scrollAndTap(RECHECK);
        return this;
    }

    public boolean showsLastChecked() {
        return isPresentAfterScroll(LAST_CHECKED);
    }
}
