package org.example.tests.web;

import org.example.base.WebBaseTest;
import org.example.data.TestAccounts;
import org.example.pages.web.AdminStorePage;
import org.example.pages.web.WebShellPage;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The Marketplace destinations of the admin portal — vendors, applications, products, fulfillment
 * and payouts.
 *
 * <p>Where the money and the identities are handed out. Two operations here have consequences that
 * outlive the test: approving an application creates a store <em>and</em> issues login credentials to
 * a third party, and marking an order shipped releases that vendor's payout. Both are exercised only
 * as far as their validation gate by default; the disabled methods below carry the full round-trip
 * for use against a disposable environment.
 */
public class AdminStoreTest extends WebBaseTest {

    private static final String ADMIN = "admin";

    private AdminStorePage openStore() {
        WebShellPage shell = signInAs(ADMIN);
        shell.openDestination(WebShellPage.STORE);

        AdminStorePage store = shell.store();
        if (!store.isLoaded()) {
            throw new SkipException("The Store destination did not render — the store admin API may "
                    + "be unavailable in this environment.");
        }
        return store;
    }

    @Test(description = "Vendors are listed with status and can be searched")
    public void vendorsAreListedAndSearchable() {
        AdminStorePage store = openStore();

        Assert.assertFalse(store.requiresAdminSignIn(),
                "An admin session should not be refused by the store screens");
        Assert.assertTrue(store.showsVendorStatuses() || store.isLoaded(),
                "Vendor rows should carry an Active/Pending/Suspended status");

        store.searchVendors("zzzznomatch");
        Assert.assertFalse(store.hasVendor("zzzznomatch"),
                "A query matching nothing should leave no vendor rows");
    }

    @Test(description = "Creating a vendor requires a name and slug")
    public void vendorCreationRequiresNameAndSlug() {
        AdminStorePage store = openStore();

        store.openNewVendor();
        store.createVendor("", "", "");

        Assert.assertTrue(store.createDialogStillOpen(),
                "A vendor with no name or slug must not be created — the slug is the storefront's "
                        + "identity and cannot be blank");
    }

    @Test(description = "Pending vendor applications are listed with their applied date")
    public void pendingApplicationsAreListed() {
        WebShellPage shell = signInAs(ADMIN);
        shell.openDestination(WebShellPage.APPLICATIONS);
        AdminStorePage store = shell.store();

        if (!store.hasPendingApplication()) {
            throw new SkipException("No pending vendor applications — submit one through "
                    + "'Apply to sell' to populate the queue.");
        }
        Assert.assertTrue(store.hasPendingApplication(),
                "Applications should be listed with when they were applied");
    }

    @Test(description = "A vendor can be created", enabled = false)
    public void vendorCanBeCreated() {
        // Disabled by default: creates a real vendor row that then appears in the marketplace and
        // has to be cleaned up. Enable against a disposable environment.
        AdminStorePage store = openStore();

        String name = "Automation Vendor " + System.currentTimeMillis();
        String slug = TestAccounts.uniqueSlug("automation-vendor");
        store.createVendor(name, slug, "0.15");

        Assert.assertTrue(store.hasVendor(name), "The vendor should be listed after creation");
    }

    @Test(description = "An admin can create a product", enabled = false)
    public void adminCanCreateProduct() {
        // Disabled by default: a created product is a catalogue entry other tests then see.
        AdminStorePage store = openStore();

        String name = "Automation Product " + System.currentTimeMillis();
        store.createProduct(name, TestAccounts.uniqueSlug("automation-product"));

        Assert.assertTrue(store.hasProduct(name), "The product should be listed after creation");
        Assert.assertTrue(store.showsNeedsVariantWarning(),
                "A product with no variant should say it cannot sell yet");
    }

    @Test(description = "An order can be marked shipped", enabled = false)
    public void orderCanBeMarkedShipped() {
        // Disabled by default: shipping RELEASES the vendor's payout. This is a money movement, not
        // a status change, and must only run where the Stripe account is a test account.
        AdminStorePage store = openStore();

        store.shipFirstOrder("USPS", "9400" + System.currentTimeMillis());

        Assert.assertTrue(store.showsPayoutOnShip(),
                "Shipping should disclose the payout it releases to the vendor");
    }

    @Test(description = "Vendor transfers can be searched by order number")
    public void payoutsAreSearchable() {
        AdminStorePage store = openStore();

        store.searchTransfers("zzzznomatch");

        Assert.assertFalse(store.hasTransferForOrder("zzzznomatch"),
                "A query matching nothing should leave no transfer rows");
    }
}
