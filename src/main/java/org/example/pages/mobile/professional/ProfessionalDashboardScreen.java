package org.example.pages.mobile.professional;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.example.pages.mobile.common.BottomNavBar;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;

/**
 * The professional dashboard — {@code screens/professional/dashboard_screen.dart}.
 *
 * <p>The pro's home: the on-duty toggle, incoming offers with their payout breakdown, today's
 * earnings and an "appointment in progress" banner.
 *
 * <p>The payout copy is the contract that matters here — an offer states what the professional will
 * actually take home ("you earn $x.xx") and why ("Service pay + mileage · convenience fee goes to
 * Trimio"). If that number drifts from what the balance later shows, the professional finds out
 * after doing the work, so the two are worth cross-checking.
 *
 * <p>Note: the shell reroutes to {@code ProfessionalNotCreatedHomePage} when the profile is missing
 * or {@code approval_status == 'pending'} — {@link #isProfileIncomplete()} detects that branch so a
 * test can skip with a clear reason instead of timing out on a dashboard that will never render.
 */
public class ProfessionalDashboardScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String YOU_EARN = "you earn";
    public static final String PAYOUT_NOTE = "Service pay + mileage · convenience fee goes to Trimio";
    public static final String EARNED = "Earned";
    public static final String DECLINE = "Decline";
    public static final String IN_PROGRESS = "You have an appointment in progress...";
    public static final String NEAREST_FIRST = "Nearest first";
    public static final String MIN_REVIEWS_NOTICE = "Minimum 3 reviews required to calculate rating.";
    /**
     * The duty switch's OWN label, used to scroll it into view.
     *
     * <p>Verified on-device: the control is an {@code android.widget.Switch} whose content-desc
     * reads {@code "Offline, Status\nOffline"}. It does NOT contain the word "duty" — scrolling
     * for that scrolls right past the switch and off the dashboard, which is precisely how an
     * earlier version of this page object ended up dereferencing a null element.
     */
    public static final String DUTY_STATUS_HINT = "Status";

    public ProfessionalDashboardScreen(AndroidDriver driver) {
        super(driver);
    }

    public BottomNavBar nav() {
        return new BottomNavBar(driver);
    }

    /** True once the professional shell has painted (its tab bar is the landmark). */
    public boolean isLoaded() {
        return nav().isProfessionalShell();
    }

    /**
     * True when the app rerouted to the "profile not created / pending approval" screen instead of
     * the dashboard. Tests should skip rather than fail on this — it is an account-state problem,
     * not a defect in the screen under test.
     */
    public boolean isProfileIncomplete() {
        return !nav().hasTab(BottomNavBar.PRO_BOOKINGS)
                && isPresent(descContains("profile"), Duration.ofSeconds(10));
    }

    // ---- offers -------------------------------------------------------------

    /** True if an offer card is on screen. */
    public boolean hasOffer() {
        return isPresentAfterScroll(YOU_EARN);
    }

    /** True when the offer explains the payout composition. */
    public boolean showsPayoutBreakdown() {
        return isPresentAfterScroll(YOU_EARN) && isPresentAfterScroll("earn");
    }

    /** True when the offer carries the mileage/convenience-fee explanation. */
    public boolean showsPayoutNote() {
        return isPresentAfterScroll("convenience fee goes to Trimio");
    }

    /** True when a bonus is attached to the offer ("+$x bonus"). */
    public boolean showsBonus() {
        return isPresentAfterScroll("bonus");
    }

    /** Declines the visible offer. */
    public ProfessionalDashboardScreen declineOffer() {
        scrollAndTap(DECLINE);
        return this;
    }

    /** Accepts the visible offer. */
    public ProfessionalDashboardScreen acceptOffer() {
        scrollAndTap("Accept");
        return this;
    }

    /** Applies the "Nearest first" sort to the offer list. */
    public ProfessionalDashboardScreen sortNearestFirst() {
        scrollAndTap(NEAREST_FIRST);
        return this;
    }

    // ---- state --------------------------------------------------------------

    /**
     * The duty control.
     *
     * <p>Matched by class AND checkable: the tree carries two {@code Switch} nodes but only one
     * is checkable, and it is that one which carries the {@code checked} state.
     */
    private static final By dutySwitch = AppiumBy.androidUIAutomator(
            "new UiSelector().className(\"android.widget.Switch\").checkable(true)");

    /** Toggles the on-duty switch. */
    public ProfessionalDashboardScreen toggleOnDuty() {
        bringDutySwitchIntoView();
        tap(dutySwitch);
        return this;
    }

    /** Puts the switch on screen; Flutter drops the semantics of anything scrolled out of it. */
    private boolean bringDutySwitchIntoView() {
        return isPresent(dutySwitch, SHORT_TIMEOUT)
                || scrollToDesc(DUTY_STATUS_HINT)
                || isPresent(dutySwitch, SHORT_TIMEOUT);
    }

    /**
     * True when the duty switch reads as on.
     *
     * <p>Reads the platform's {@code checked} attribute rather than the label, so it cannot be
     * fooled by the copy changing. Absent switch means not on duty rather than an exception —
     * a professional held off the dashboard has no switch to read.
     */
    public boolean isOnDuty() {
        if (!bringDutySwitchIntoView()) {
            LOG.warn("Dashboard: no duty switch on screen");
            return false;
        }
        WebElement control = find(dutySwitch);
        return control != null && Boolean.parseBoolean(control.getAttribute("checked"));
    }

    /**
     * Drives the duty switch to {@code wanted} and reports whether it got there.
     *
     * <p>Duty is NOT the switch. {@code ClearanceBadge.isOnDuty} is
     * {@code switchedOn && clearance.isApproved && ready}, and the server refuses duty regardless
     * of the switch — so a professional who is unlicensed for the state or short of the readiness
     * checklist will see the toggle spring back. Returning the observed state rather than
     * asserting here lets the caller tell "the toggle does not work" from "this pro may not hold
     * duty", which are very different findings.
     */
    public boolean setOnDuty(boolean wanted) {
        if (isOnDuty() == wanted) {
            return true;
        }
        LOG.info("Dashboard: switching duty {}", wanted ? "ON" : "OFF");
        toggleOnDuty();
        // The switch round-trips to the server, which may refuse it.
        for (int i = 0; i < 10 && isOnDuty() != wanted; i++) {
            sleepBriefly();
        }
        return isOnDuty() == wanted;
    }

    /** True when an appointment is currently running. */
    public boolean showsAppointmentInProgress() {
        return isPresentAfterScroll("appointment in progress");
    }

    /** True when today's earnings figure is rendered. */
    public boolean showsEarned() {
        return isPresentAfterScroll(EARNED);
    }
}
