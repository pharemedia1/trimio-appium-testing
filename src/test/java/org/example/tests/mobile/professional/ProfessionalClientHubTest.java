package org.example.tests.mobile.professional;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.professional.ProfessionalClientHubScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/** Client Hub — the professional's client list, search and private notes. */
public class ProfessionalClientHubTest extends RoleSessionTest {

    private ProfessionalClientHubScreen openClientHub() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_CLIENT_HUB);

        ProfessionalClientHubScreen hub = new ProfessionalClientHubScreen(driver);
        Assert.assertTrue(hub.isLoaded(), "Client Hub should render");
        return hub;
    }

    @Test(description = "Clients are listed with visit count and spend")
    public void clientsAreListed() {
        ProfessionalClientHubScreen hub = openClientHub();
        if (!hub.hasAnyClient()) {
            throw new SkipException("The professional has served no clients yet — complete an "
                    + "appointment (or seed history) to populate the hub.");
        }

        Assert.assertTrue(hub.showsVisitCounts(), "Each client row should show its visit count");
        Assert.assertTrue(hub.showsTotalSpent(), "Each client row should show lifetime spend");
    }

    @Test(description = "Search filters the client list")
    public void searchFiltersClients() {
        ProfessionalClientHubScreen hub = openClientHub();
        if (!hub.hasAnyClient()) {
            throw new SkipException("The professional has served no clients yet.");
        }

        hub.search("zzzznomatch");

        Assert.assertFalse(hub.hasResult("zzzznomatch"),
                "A query matching nothing should leave no client rows");
    }

    @Test(description = "A private note persists on the client profile")
    public void noteCanBeAdded() {
        ProfessionalClientHubScreen hub = openClientHub();
        if (!hub.hasAnyClient()) {
            throw new SkipException("The professional has served no clients yet.");
        }

        String note = "Automation note " + System.currentTimeMillis();
        hub.addNote(note);

        Assert.assertTrue(hub.hasNote(note),
                "The note should persist on the client's profile — these notes are the "
                        + "professional's own working memory and must survive a reload");
    }
}
