package org.example.tests.mobile.admin;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.admin.AdminConsoleScreen;
import org.example.pages.mobile.admin.AdminEnforcementScreen;
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

    // ---- price overrides: REMOVED FROM THE PRODUCT ---------------------------
    //
    // overrideRequiresServiceAndReason and overrideCanBeCreated are GONE, along with
    // AdminPricingScreen, because the feature they covered no longer exists. 'Price Overrides'
    // was removed from the admin console on 2026-08-07 (admin_home_page.dart names it
    // "Breaker #1, R2.3") and the backend service, repository, controllers and routes went with
    // it — the endpoint inventory carries no /admin/price-overrides route at all.
    //
    // The reason matters and is worth keeping: it let an admin set custom pricing rules on a
    // professional's services, i.e. the platform setting the price, which is prong A of the ABC
    // worker-classification test. This is a deliberate legal position, not a feature that might
    // come back, so the tests are deleted rather than disabled.

    // ---- training -----------------------------------------------------------

    @Test(description = "Training material creation validates its required fields")
    public void trainingRequiresTitleAndUrl() {
        AdminConsoleScreen console = loginAsAdmin();
        AdminTrainingScreen training = console.openTraining();
        Assert.assertTrue(training.isLoaded(), "Training materials should render");

        training.createNew();
        training.save();

        // KNOWN PRODUCT DEFECT, and this test is what reports it.
        //
        // admin_training_materials_page.dart DOES validate — an empty title or URL returns early
        // with a SnackBar reading exactly REQUIRED_ERROR. The admin never sees it. The dialog is
        // opened with showDialog(context: context, builder: (ctx) => …) and the handler then calls
        // ScaffoldMessenger.of(CONTEXT) — the page's messenger, not the dialog's — while the
        // dialog is still up (it `return`s without popping). A SnackBar on the page's Scaffold
        // renders beneath a pushed route and its modal barrier, so it is invisible on screen and
        // absent from the accessibility tree above the modal.
        //
        // The effect for a real admin: the form appears to do nothing. They press Create, nothing
        // moves, and there is no explanation anywhere.
        //
        // Same defect class as PaymentHandlerService in the booking flow, where a refused charge
        // is reported on the dialog's context and the only evidence is `adb logcat`. Worth fixing
        // the same way: pop the dialog first, or attach the message to the dialog itself.
        Assert.assertTrue(training.showsRequiredFieldsError(),
                "Saving a training material with no title and no file URL produced no visible "
                        + "error. The validation runs and raises a SnackBar with exactly '"
                        + AdminTrainingScreen.REQUIRED_ERROR + "', but it is raised on the PAGE's "
                        + "ScaffoldMessenger while the dialog is still open, so it renders behind "
                        + "the modal barrier — invisible to the admin and absent from the "
                        + "accessibility tree. See admin_training_materials_page.dart.");
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
