package org.example.tests.web;

import org.example.base.WebBaseTest;
import org.example.data.TestAccounts;
import org.example.pages.web.VendorPortalPage;
import org.example.pages.web.WebShellPage;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The vendor portal — store context, catalogue, orders and payouts.
 *
 * <p>A vendor is a third party operating inside Trimio's marketplace, which makes isolation the
 * first-class assertion: their sidebar must contain <em>only</em> their four store destinations, and
 * no admin one. {@link #vendorSidebarShowsStoreDestinations()} checks both directions — what is there
 * and what must not be.
 *
 * <p>Catalogue writes are exercised where they are cheap and reversible-ish (validation gates,
 * search) and disabled where they publish something to the storefront.
 */
public class VendorPortalTest extends WebBaseTest {

    private static final String VENDOR = "vendor";

    private VendorPortalPage openVendorPortal() {
        WebShellPage shell = signInAs(VENDOR);
        if (!shell.isVendorShell()) {
            throw new SkipException("The configured 'vendor' account did not reach the vendor "
                    + "portal — check its user_type_id is 9 and that it has a store row.");
        }
        return shell.vendor();
    }

    @Test(description = "The vendor sidebar shows only the store destinations")
    public void vendorSidebarShowsStoreDestinations() {
        WebShellPage shell = signInAs(VENDOR);

        Assert.assertTrue(shell.hasAllDestinations(WebShellPage.VENDOR_DESTINATIONS),
                "A vendor should have Overview, Catalog, Orders and Payouts");
        Assert.assertTrue(shell.hasNoneOfDestinations(
                        WebShellPage.ALL_USERS, WebShellPage.QUALITY, WebShellPage.ENFORCEMENTS,
                        WebShellPage.PRICING, WebShellPage.APPLICATIONS),
                "A vendor must not see any admin destination — they are a third party inside the "
                        + "marketplace, not staff");
    }

    @Test(description = "The store header shows identity, product count and fee")
    public void storeHeaderShowsContext() {
        VendorPortalPage vendor = openVendorPortal();

        Assert.assertTrue(vendor.showsStoreContext(),
                "The header should state the product count and the platform fee — the fee is the "
                        + "commission deducted from every payout, so it belongs in permanent view");
    }

    @Test(description = "The payout CTA reflects the onboarding state")
    public void payoutCtaReflectsState() {
        VendorPortalPage vendor = openVendorPortal();

        String state = vendor.payoutState();
        Assert.assertFalse(state.isBlank(),
                "The header should show a payout state pill (Payouts on / Onboarding / No payouts)");

        if (VendorPortalPage.PAYOUTS_ON.equals(state)) {
            Assert.assertFalse(vendor.showsPayoutCta(),
                    "With payouts live there should be no setup CTA");
        } else {
            Assert.assertTrue(vendor.showsPayoutCta(),
                    "Without payouts the vendor must be offered a way to set them up — otherwise "
                            + "they can sell but never be paid");
        }
    }

    @Test(description = "A payout onboarding link can be requested")
    public void payoutSetupLinkIsIssued() {
        VendorPortalPage vendor = openVendorPortal();

        if (!vendor.showsPayoutCta()) {
            throw new SkipException("The configured vendor already has payouts enabled — use one "
                    + "without a completed Stripe account to exercise onboarding.");
        }
        Assert.assertTrue(vendor.requestPayoutLink(),
                "Requesting payout setup should return a Stripe onboarding link");
    }

    @Test(description = "The store name is required when editing the profile")
    public void storeProfileRequiresName() {
        VendorPortalPage vendor = openVendorPortal();

        Assert.assertTrue(vendor.saveStoreWithEmptyName(),
                "Saving with an empty store name should be refused with '"
                        + VendorPortalPage.STORE_NAME_ERROR + "'");
    }

    @Test(description = "Creating a product requires a name and slug")
    public void productRequiresNameAndSlug() {
        VendorPortalPage vendor = openVendorPortal().openCatalog();

        Assert.assertTrue(vendor.createProductWithoutRequiredFields(),
                "A product with no name or slug must not be created");
    }

    @Test(description = "Product search filters the catalogue")
    public void productSearchFilters() {
        VendorPortalPage vendor = openVendorPortal().openCatalog();

        vendor.searchProducts("zzzznomatch");

        Assert.assertFalse(vendor.hasProduct("zzzznomatch"),
                "A query matching nothing should leave no product rows");
    }

    @Test(description = "Orders are listed with their payout amount")
    public void ordersAreListed() {
        VendorPortalPage vendor = openVendorPortal().openOrders();

        Assert.assertTrue(vendor.hasNoOrders() || vendor.hasAnyOrder(),
                "Orders should be listed, or the empty state shown");
        if (vendor.hasAnyOrder()) {
            Assert.assertTrue(vendor.showsOrderPayouts(),
                    "Each order should disclose the payout it will produce");
        }
    }

    @Test(description = "Shipping requires a carrier and tracking number")
    public void shippingRequiresCarrierAndTracking() {
        VendorPortalPage vendor = openVendorPortal().openOrders();

        if (!vendor.hasAnyOrder()) {
            throw new SkipException("The vendor has no orders to ship — place a store order to "
                    + "populate the queue.");
        }

        Assert.assertTrue(vendor.shipWithoutCarrierAndTracking(),
                "Shipping without a carrier and tracking number must be refused — marking shipped "
                        + "releases the payout, so it cannot happen without proof of despatch");
    }

    @Test(description = "The store profile can be edited", enabled = false)
    public void storeProfileCanBeEdited() {
        // Disabled by default: renames a live storefront that clients and professionals can see.
        VendorPortalPage vendor = openVendorPortal();

        Assert.assertTrue(vendor.updateStore("Automation Store", "support@example.com", ""),
                "A valid profile save should confirm with '" + VendorPortalPage.STORE_UPDATED + "'");
    }

    @Test(description = "A vendor can create a product", enabled = false)
    public void vendorCanCreateProduct() {
        // Disabled by default: publishes a catalogue entry.
        VendorPortalPage vendor = openVendorPortal().openCatalog();

        String name = "Automation Product " + System.currentTimeMillis();
        vendor.createProduct(name, TestAccounts.uniqueSlug("automation-product"), "created by tests");

        Assert.assertTrue(vendor.hasProduct(name), "The product should be listed after creation");
    }

    @Test(description = "A variant can be added to a product", enabled = false)
    public void variantCanBeAdded() {
        // Disabled by default: a priced variant makes the product sellable.
        VendorPortalPage vendor = openVendorPortal().openCatalog();

        String name = "Automation Product " + System.currentTimeMillis();
        vendor.createProduct(name, TestAccounts.uniqueSlug("automation-product"), "created by tests")
                .openProduct(name)
                .addVariant("Default", "19.99");

        Assert.assertTrue(vendor.hasVariant("Default"), "The variant should be listed");
        Assert.assertFalse(vendor.showsNeedsVariantWarning(),
                "With a variant the product should no longer be flagged as unsellable");
    }

    @Test(description = "Status controls storefront visibility", enabled = false)
    public void statusControlsVisibility() {
        // Disabled by default: activating a product exposes it to real buyers.
        VendorPortalPage vendor = openVendorPortal().openCatalog();

        String name = "Automation Product " + System.currentTimeMillis();
        vendor.createProduct(name, TestAccounts.uniqueSlug("automation-product"), "created by tests")
                .openProduct(name)
                .addVariant("Default", "19.99")
                .setStatus(VendorPortalPage.STATUS_ACTIVE);

        Assert.assertTrue(vendor.showsStatus(VendorPortalPage.STATUS_ACTIVE),
                "The product should report itself as Active");
    }

    @Test(description = "The overview links through to Orders")
    public void overviewLinksToOrders() {
        VendorPortalPage vendor = openVendorPortal().openOverview();

        Assert.assertTrue(vendor.overviewLoaded(), "The overview should render its metrics");
        vendor.openOrders();
        Assert.assertTrue(vendor.shell().topBarShows(WebShellPage.ORDERS),
                "Following the to-ship metric should land on the Orders destination");
    }
}
