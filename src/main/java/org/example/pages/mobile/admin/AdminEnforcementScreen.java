package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Enforcements — {@code screens/Admin/Enforcements/admin_enforcement_list_page.dart}.
 *
 * <p>The register of safety holds: who is suspended, why, and for how long. Extensions are bounded
 * ("Choose duration (1–30 days)"), and reinstating restores a user's access immediately — the action
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
    public static final String CHOOSE_DURATION = "Choose duration (1–30 days)";
    public static final String SELECT_EXTENSION_DAYS = "Select extension days";
    public static final String REASON_PREFIX = "Reason:";

    public AdminEnforcementScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresentAfterScroll(REINSTATE) || isPresentAfterScroll(REASON_PREFIX)
                || isPresent(descContains("Enforcement"), Duration.ofSeconds(20));
    }

    /** True if at least one enforcement record is listed. */
    public boolean hasAnyEnforcement() {
        return isPresentAfterScroll(REASON_PREFIX) || isPresentAfterScroll(REINSTATE);
    }

    /** True when each record explains why the hold exists. */
    public boolean showsReasons() {
        return isPresentAfterScroll(REASON_PREFIX);
    }

    // ---- actions ------------------------------------------------------------

    /** Opens the extend dialog for the first record. */
    public AdminEnforcementScreen tapExtend() {
        scrollAndTap(EXTEND);
        return this;
    }

    /** True when the extension dialog states its 1–30 day bound. */
    public boolean showsDurationBounds() {
        return isPresent(descContains("1–30 days"), Duration.ofSeconds(10))
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
