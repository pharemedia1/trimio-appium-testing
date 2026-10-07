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
    public static final String PAYOUT_NOTE = "convenience fee goes to Trimio";
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

    /**
     * The payout figure as the card formats it — {@code toStringAsFixed(2)}, so always two
     * decimals. Deliberately not a bare "$": the card also lists per-service prices, and the
     * point of this pattern is to find a disclosed payout rather than any money on screen.
     */
    private static final java.util.regex.Pattern MONEY =
            java.util.regex.Pattern.compile("\\$\\d+\\.\\d{2}");

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
     * True when the pro is locked into an active job instead of the dashboard.
     *
     * <p>Accepting an on-demand offer puts the professional on a full-screen navigation view —
     * "Navigation Map", "Call Client", "Message Client", "Completed" — and that view has no bottom
     * nav, so {@link #isLoaded()} is false and every dashboard test looks like a sign-in failure.
     * Minimising it does not help: it collapses to an "On the way to your client" banner that is
     * still full-screen. The only exits are finishing the job or cancelling it, both of which
     * write and move money, so no test may take them on its own initiative.
     *
     * <p>It resolves itself. {@code jobs/appointmentStatusJob.js} runs every minute and
     * {@code update_appointment_statuses()} completes an appointment once its scheduled end time
     * passes, which releases the device. So the right response is to say so and skip, not to
     * repair anything.
     */
    public boolean hasActiveJob() {
        return isPresent(descContains("Navigation Map"), Duration.ofSeconds(5))
                || isPresent(descContains("On the way to your client"), Duration.ofSeconds(3))
                || isPresent(descContains(IN_PROGRESS), Duration.ofSeconds(3));
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

    /**
     * True if an offer card is on screen.
     *
     * <p>Keyed on the Decline button, which the card renders unconditionally. It used to key on
     * {@link #YOU_EARN}, and that label is <b>conditional in the app</b>:
     * {@code dashboard_screen.dart} renders {@code '+$N bonus'} <em>instead of</em> "you earn"
     * whenever a bonus is attached to the offer. So an offer carrying a bonus — a real, ordinary
     * offer — reported as no offer at all, and the caller skipped saying the dashboard was empty
     * while an offer sat on it.
     */
    public boolean hasOffer() {
        return isPresentAfterScroll(DECLINE);
    }

    /**
     * True when the offer discloses what the professional takes home.
     *
     * <p>What that means in the app: {@code if (payout != null)} renders the figure
     * {@code '$x.xx'} and then <em>either</em> a {@code '+$N bonus'} chip <em>or</em> the words
     * "you earn". When {@code payout} is null the whole block is absent — figure and both labels —
     * and that is the defect worth catching, because the professional is then asked to accept a
     * job without being told the pay.
     *
     * <p>So the figure is the assertion and the label is secondary. The previous version asserted
     * {@code isPresentAfterScroll(YOU_EARN) && isPresentAfterScroll("earn")}, which is the same
     * condition twice — "earn" is a substring of "you earn" — so it never checked an amount at
     * all, and it returned false for every bonus-bearing offer.
     */
    public boolean showsPayoutBreakdown() {
        if (payoutAmount() == null) {
            return false;
        }
        return isPresentAfterScroll(YOU_EARN) || isPresentAfterScroll("bonus");
    }

    /**
     * The payout figure on the offer card ({@code "$42.50"}), or null when none is shown.
     *
     * <p>Matched in Java rather than through a {@code UiSelector} regex: the card frequently
     * merges into a single accessibility node whose content-desc is newline-joined, and
     * {@code descriptionContains} cannot match across a newline.
     */
    public String payoutAmount() {
        scrollToDesc(DECLINE);
        for (WebElement e : findAll(descContains("$"))) {
            String desc = e.getAttribute("content-desc");
            if (desc == null || desc.isBlank()) {
                desc = e.getText();
            }
            if (desc == null) {
                continue;
            }
            java.util.regex.Matcher m = MONEY.matcher(desc);
            if (m.find()) {
                return m.group();
            }
        }
        return null;
    }

    /** True when the offer carries the mileage/convenience-fee explanation. */
    public boolean showsPayoutNote() {
        return isPresentAfterScroll(PAYOUT_NOTE);
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
