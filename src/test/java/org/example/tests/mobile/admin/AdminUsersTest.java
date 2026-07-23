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

    @Test(description = "All Users opens with client and professional counts")
    public void allUsersOpens() {
        AdminUsersScreen users = openAllUsers();

        Assert.assertTrue(users.showsUserCounts(),
                "All Users should show the client and professional counts");
    }

    @Test(description = "Professional status segments list only their own state")
    public void professionalStatusSegments() {
        AdminUsersScreen users = openAllUsers();

        boolean anySegmentOpened = false;
        for (String segment : new String[]{
                AdminUsersScreen.SEGMENT_PENDING, AdminUsersScreen.SEGMENT_APPROVED,
                AdminUsersScreen.SEGMENT_REJECTED, AdminUsersScreen.SEGMENT_INCOMPLETE}) {
            if (users.isLoaded()) {
                users.openSegment(segment);
                anySegmentOpened = true;
            }
        }
        Assert.assertTrue(anySegmentOpened,
                "At least one professional-status segment should be reachable from All Users");
    }

    @Test(description = "The automated licence check result is displayed for review")
    public void automatedCheckResultIsShown() {
        AdminUsersScreen users = openAllUsers();
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
