package org.example.tests.mobile.professional;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.professional.ProfessionalDashboardScreen;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Going on duty — the switch that decides whether a professional can be dispatched at all.
 *
 * <p><b>Duty is not the switch.</b> {@code ClearanceBadge.isOnDuty} is
 * {@code switchedOn && clearance.isApproved && ready}: the licence clearance for the state being
 * worked, and the readiness checklist in {@code services/proReadiness.js} — identity documents,
 * an approved profile, priced services, a Stripe account that can accept charges, availability,
 * payout, portfolio and bio. Every one of them blocks, by decision, so that a professional
 * finishes their profile before going online rather than after. The server refuses duty
 * regardless of the switch, so flipping it is only the last of several conditions.
 *
 * <p>That is why the fixture matters: {@code roleAccounts.professional} is professional_id 266,
 * the only Texas professional that both passes {@code readiness()} and can sign in. Point this at
 * an ordinary seeded pro and the toggle springs back — correctly.
 */
public class ProfessionalDutyTest extends RoleSessionTest {

    /** Going on duty is what makes a professional dispatchable. */
    @Test(description = "A ready professional can go on duty")
    public void professionalCanGoOnDuty() {
        ProfessionalDashboardScreen dashboard = loginAsProfessional();

        Assert.assertTrue(dashboard.setOnDuty(true),
                "A professional who passes clearance and readiness should be able to go on duty — "
                        + "the switch springing back means the server refused it");
        Assert.assertTrue(dashboard.isOnDuty(), "The duty switch should read as on");
    }

    /**
     * And can come off it again.
     *
     * <p>The direction that matters for a person's evening: a professional who cannot go offline
     * keeps receiving offers they have no intention of accepting, and declining is what their
     * acceptance rate is scored on.
     */
    @Test(dependsOnMethods = "professionalCanGoOnDuty",
            description = "A professional can go back off duty")
    public void professionalCanGoOffDuty() {
        ProfessionalDashboardScreen dashboard = loginAsProfessional();
        Assert.assertTrue(dashboard.setOnDuty(true), "Precondition: should be able to go on duty");

        Assert.assertTrue(dashboard.setOnDuty(false),
                "Switching back to offline should stick");
        Assert.assertFalse(dashboard.isOnDuty(), "The duty switch should read as off");
    }
}
