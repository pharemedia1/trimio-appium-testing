package org.example.tests.mobile.admin;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.admin.AdminConsoleScreen;
import org.example.pages.mobile.admin.AdminEnforcementScreen;
import org.example.pages.mobile.admin.AdminPricingScreen;
import org.example.pages.mobile.admin.AdminQueuesScreen;
import org.example.pages.mobile.admin.AdminTrainingScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The remaining admin queues: Reports, Services, Countries &amp; States, Enforcements, Price
 * Overrides and Training.
 *
 * <p>Grouped in one class because they share a shape — open the tile, assert the queue rendered, and
 * assert the one rule that queue enforces. Splitting them into six classes of two methods each would
 * add navigation cost without adding coverage.
 *
 * <p>The mutating paths are deliberately shallow. A price override changes what every matching
 * client is charged; approving a state makes Trimio operable in a jurisdiction; extending a
 * suspension keeps a real person locked out. Those are exercised up to their validation gate and no
 * further — the full round-trips are manual cases on the Admin sheet.
 */
public class AdminQueuesTest extends RoleSessionTest {

    // ---- reports ------------------------------------------------------------

    @Test(description = "The reports queue lists open support tickets")
    public void openReportsAreListed() {
        AdminConsoleScreen console = loginAsAdmin();
        console.openTile(AdminConsoleScreen.TILE_REPORTS);

        AdminQueuesScreen queues = new AdminQueuesScreen(driver);
        Assert.assertTrue(queues.reportsLoaded(),
                "The reports queue should render its list or its empty state");
    }

    // ---- services -----------------------------------------------------------

    @Test(description = "The services catalogue renders")
    public void servicesAreListed() {
        AdminConsoleScreen console = loginAsAdmin();
        console.openTile(AdminConsoleScreen.TILE_SERVICES);

        AdminQueuesScreen queues = new AdminQueuesScreen(driver);
        Assert.assertTrue(queues.servicesLoaded(), "The services catalogue should render");
    }

    // ---- states -------------------------------------------------------------

    @Test(description = "States are listed with their active count")
    public void statesAreListed() {
        AdminConsoleScreen console = loginAsAdmin();
        console.openTile(AdminConsoleScreen.TILE_STATES);

        AdminQueuesScreen queues = new AdminQueuesScreen(driver);
        Assert.assertTrue(queues.statesLoaded(), "The states screen should render");
        Assert.assertTrue(queues.showsActiveStateCount() || queues.showsNoStatesForCountry(),
                "The screen should summarise how many states are active, or say there are none");
    }

    // ---- enforcements -------------------------------------------------------

    @Test(description = "Enforcement records show why each hold exists")
    public void enforcementsAreListed() {
        AdminConsoleScreen console = loginAsAdmin();
        AdminEnforcementScreen enforcement = console.openEnforcements();

        if (!enforcement.hasAnyEnforcement()) {
            throw new SkipException("No active enforcements in this environment — suspend a test "
                    + "account to populate the register.");
        }
        Assert.assertTrue(enforcement.showsReasons(),
                "Every hold should carry its reason — an unexplained suspension cannot be reviewed "
                        + "or defended");
    }

    @Test(description = "Extending a suspension states its 1–30 day bound")
    public void extensionStatesItsBounds() {
        AdminConsoleScreen console = loginAsAdmin();
        AdminEnforcementScreen enforcement = console.openEnforcements();

        if (!enforcement.hasAnyEnforcement()) {
            throw new SkipException("No active enforcements in this environment.");
        }
        enforcement.tapExtend();

        Assert.assertTrue(enforcement.showsDurationBounds(),
                "The extension dialog should state the permitted 1–30 day range");
    }

    // ---- price overrides ----------------------------------------------------

    @Test(description = "The override editor refuses to save without a service and reason")
    public void overrideRequiresServiceAndReason() {
        AdminConsoleScreen console = loginAsAdmin();
        AdminPricingScreen pricing = console.openPricing();
        Assert.assertTrue(pricing.isLoaded(), "Price Overrides should render");

        pricing.createNew();
        if (!pricing.editorIsOpen()) {
            throw new SkipException("The override editor did not open.");
        }
        pricing.save();

        Assert.assertTrue(pricing.saveWasBlocked(),
                "Saving an override with no service or reason must be refused — an unexplained "
                        + "override silently changes what every matching client pays");
    }

    @Test(description = "An override can be created for a service", enabled = false)
    public void overrideCanBeCreated() {
        // Deliberately disabled: a saved override immediately changes live client pricing, so it is
        // run only against a disposable environment. Enable with -Dgroups or by flipping this flag
        // when pointing at a seeded staging database.
        AdminConsoleScreen console = loginAsAdmin();
        AdminPricingScreen pricing = console.openPricing();

        pricing.createNew()
                .setFixedPrice("45")
                .setReason("automation-" + System.currentTimeMillis())
                .save();

        Assert.assertFalse(pricing.saveWasBlocked(), "A complete override should save");
    }

    // ---- training -----------------------------------------------------------

    @Test(description = "Training material creation validates its required fields")
    public void trainingRequiresTitleAndUrl() {
        AdminConsoleScreen console = loginAsAdmin();
        AdminTrainingScreen training = console.openTraining();
        Assert.assertTrue(training.isLoaded(), "Training materials should render");

        training.createNew();
        training.save();

        Assert.assertTrue(training.showsRequiredFieldsError(),
                "Saving without a title and file URL should show '"
                        + AdminTrainingScreen.REQUIRED_ERROR + "'");
    }

    @Test(description = "A training material can be created", enabled = false)
    public void materialCanBeCreated() {
        // Disabled by default: creates a material visible to every professional. Enable against a
        // disposable environment.
        AdminConsoleScreen console = loginAsAdmin();
        AdminTrainingScreen training = console.openTraining();

        String title = "Automation material " + System.currentTimeMillis();
        training.createNew()
                .fillMaterial(title, "created by the automation suite", "https://example.com/a.mp4")
                .save();

        Assert.assertTrue(training.hasMaterial(title), "The material should be listed after saving");
    }
}
