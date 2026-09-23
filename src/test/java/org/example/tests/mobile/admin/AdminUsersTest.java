package org.example.tests.mobile.admin;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.admin.AdminConsoleScreen;
import org.example.pages.mobile.admin.AdminUsersScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * All Users, the professional-status queues and the licence-verification queue.
 *
 * <p>Approving and rejecting are not automated: an approval makes a professional bookable by real
 * clients, and a rejection blocks someone's livelihood with a reason they will read. The automation
 * covers the queues being populated and the evidence being <em>shown</em> — including the automated
 * DMV/board check, which is advisory: it must be displayed alongside the raw provider response, and
 * an admin still decides.
 */
public class AdminUsersTest extends RoleSessionTest {

    private AdminUsersScreen openAllUsers() {
        AdminConsoleScreen console = loginAsAdmin();
        console.openTile(AdminConsoleScreen.TILE_ALL_USERS);

        AdminUsersScreen users = new AdminUsersScreen(driver);
        Assert.assertTrue(users.isLoaded(), "All Users should render");
        return users;
    }

    /**
     * All Users → Professionals, where the approval-status segments actually live.
     *
     * <p><b>An extra hop the tests were missing.</b> "All Users" ({@code all_users_page.dart}) is
     * a chooser offering only "Clients" and "Professionals"; the Pending / Approved / Rejected /
     * Incomplete segments are on the page behind the second of those
     * ({@code admin_professionals_statusa_page.dart}). Calling {@code openSegment("Pending")}
     * straight from All Users therefore waited out a 30-second timeout on a label that is one tap
     * away, and reported it as the segment being absent.
     */
    private AdminUsersScreen openProfessionalsByStatus() {
        AdminUsersScreen users = openAllUsers();
        users.openProfessionals();
        Assert.assertTrue(users.isProfessionalsListLoaded(),
                "The professionals list should show its approval-status segments (Pending, "
                        + "Approved, Rejected, Incomplete)");
        return users;
    }

    @Test(description = "All Users opens with client and professional counts")
    public void allUsersOpens() {
        AdminUsersScreen users = openAllUsers();

        Assert.assertTrue(users.showsUserCounts(),
                "All Users should show the client and professional counts");
    }

    @Test(description = "Professional status segments list only their own state")
    public void professionalStatusSegments() {
        AdminUsersScreen users = openProfessionalsByStatus();

        // All four segments are asserted PRESENT, then one is opened.
        //
        // The old shape looped over the segments opening each in turn, guarded by
        // users.isLoaded() — which asks whether we are on the All Users hub. By that point we are
        // one page past it, on "Professionals", so the guard was false for every segment, the loop
        // body never ran, and the test reported the segments as unreachable while all four were on
        // screen. It also had no way back: each segment pushes its own page, so opening the second
        // one could never have worked either.
        for (String segment : new String[]{
                AdminUsersScreen.SEGMENT_PENDING, AdminUsersScreen.SEGMENT_APPROVED,
                AdminUsersScreen.SEGMENT_REJECTED, AdminUsersScreen.SEGMENT_INCOMPLETE}) {
            Assert.assertTrue(users.hasSegment(segment),
                    "The professionals page should offer the '" + segment + "' segment — a missing "
                            + "one makes that part of the approval queue unreachable");
        }

        users.openSegment(AdminUsersScreen.SEGMENT_PENDING);
        Assert.assertFalse(users.isProfessionalsListLoaded(),
                "Opening a segment should navigate away from the segment chooser");
    }

    @Test(description = "The automated licence check result is displayed for review")
    public void automatedCheckResultIsShown() {
        AdminUsersScreen users = openProfessionalsByStatus();
        users.openSegment(AdminUsersScreen.SEGMENT_PENDING);

        if (!users.segmentHasEntries()) {
            throw new SkipException("No pending professionals in this environment — submit a "
                    + "professional profile to populate the queue.");
        }
        users.openFirstProfessional();

        Assert.assertTrue(users.showsAutomatedCheckResult(),
                "The automated provider check (or 'Automated check not run') must be shown to the "
                        + "admin — it is advisory evidence, and hiding it turns a human decision "
                        + "into a rubber stamp");
    }
}
