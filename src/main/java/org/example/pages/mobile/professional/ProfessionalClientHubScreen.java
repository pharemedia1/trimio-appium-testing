package org.example.pages.mobile.professional;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Client Hub — {@code screens/professional/clientHub/professional_client_hub.dart} and the per-client
 * profile it opens.
 *
 * <p>The professional's CRM: every client they have served, with visit count and lifetime spend, plus
 * private notes ("Preferences, allergies, reminders…"). Those notes are the professional's own —
 * they must never surface to the client, which is the assertion worth having alongside the
 * persistence check.
 */
public class ProfessionalClientHubScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String TITLE = "Clients";
    public static final String CLIENT_LIST = "Client List";
    public static final String SEARCH_HINT = "Search by client name";
    public static final String NOTES = "Notes";
    public static final String ADD_NOTE = "+ Add";
    public static final String NOTE_HINT = "Preferences, allergies, reminders…";
    public static final String APPOINTMENT_HISTORY = "Appointment history";
    public static final String BROUGHT_IN_CLIENT = "Your brought-in client";

    /** Empty state, verified on-device. */
    public static final String EMPTY = "No clients yet.";

    public ProfessionalClientHubScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(descContains(TITLE), Duration.ofSeconds(25))
                || isPresent(descContains(CLIENT_LIST), Duration.ofSeconds(10));
    }

    /**
     * True if at least one client row is listed.
     *
     * <p>Asks the negative question on purpose. The stats strip above the list contains the caption
     * "visited 2+ times" and a "$" figure whether or not any client exists, so matching on those
     * words reports clients on an empty hub — verified on-device.
     */
    public boolean hasAnyClient() {
        return !isPresent(descContains(EMPTY), SHORT_TIMEOUT);
    }

    /** True when a client row shows its visit count (only meaningful once clients exist). */
    public boolean showsVisitCounts() {
        return hasAnyClient() && isPresentAfterScroll("visit");
    }

    /** True when a client row shows lifetime spend (only meaningful once clients exist). */
    public boolean showsTotalSpent() {
        return hasAnyClient() && isPresentAfterScroll("$");
    }

    /** Filters the client list by name. */
    public ProfessionalClientHubScreen search(String name) {
        LOG.info("ClientHub: searching '{}'", name);
        type(editText(0), name);
        hideKeyboard();
        return this;
    }

    public boolean hasResult(String name) {
        return isPresentAfterScroll(name);
    }

    /** Opens a client's profile by name. */
    public ProfessionalClientHubScreen openClient(String name) {
        scrollAndTap(name);
        return this;
    }

    // ---- notes --------------------------------------------------------------

    /** Adds a private note to the open client profile. */
    public ProfessionalClientHubScreen addNote(String note) {
        scrollAndTap(ADD_NOTE);
        type(editText(0), note);
        hideKeyboard();
        scrollAndTap("Save");
        return this;
    }

    /** True if the note is shown on the client profile. */
    public boolean hasNote(String note) {
        return isPresentAfterScroll(note);
    }

    /** True when the client's appointment history section is rendered. */
    public boolean showsAppointmentHistory() {
        return isPresentAfterScroll(APPOINTMENT_HISTORY);
    }

    /** True when the client was acquired through this professional's referral link. */
    public boolean showsBroughtInBadge() {
        return isPresentAfterScroll(BROUGHT_IN_CLIENT);
    }
}
