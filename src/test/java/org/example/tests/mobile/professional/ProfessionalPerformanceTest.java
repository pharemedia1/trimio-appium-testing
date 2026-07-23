package org.example.tests.mobile.professional;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.professional.ProfessionalEarningsScreen;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Performance, KPIs and the trends dashboard.
 *
 * <p>These screens are read-only analytics, so the assertion is availability rather than arithmetic:
 * they must render, and when they cannot they must offer "Retry" instead of an empty page. Charts
 * drawn on a Flutter canvas expose no semantics, so their <em>values</em> are not automatable here —
 * reconciling revenue against appointments stays a manual/API check (see the Professional sheet).
 */
public class ProfessionalPerformanceTest extends RoleSessionTest {

    @Test(description = "The performance surface loads rather than erroring out")
    public void performanceLoads() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_DASHBOARD);

        ProfessionalEarningsScreen performance = new ProfessionalEarningsScreen(driver);

        Assert.assertTrue(performance.performanceLoaded(),
                "The performance surface should render its data rather than falling back to 'Retry'");
    }
}
