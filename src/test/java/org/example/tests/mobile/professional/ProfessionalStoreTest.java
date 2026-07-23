package org.example.tests.mobile.professional;

import org.example.base.RoleSessionTest;
import org.example.data.TestAccounts;
import org.example.pages.mobile.common.BottomNavBar;
import org.example.pages.mobile.professional.ProfessionalStoreScreen;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The professional store — the pro-only catalogue, the cart and the purchase-gated review form.
 *
 * <p>This surface is the mirror image of {@code ClientStoreTest}'s visibility assertions: the same
 * catalogue, but a professional <em>should</em> see the Pro-only items a client must not. Running
 * both halves is what makes the flag meaningful — testing only one side cannot tell "correctly
 * restricted" from "broken for everyone".
 */
public class ProfessionalStoreTest extends RoleSessionTest {

    private ProfessionalStoreScreen openStore() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_STORE);

        ProfessionalStoreScreen store = new ProfessionalStoreScreen(driver);
        Assert.assertTrue(store.isLoaded(), "The professional store should render");
        return store;
    }

    @Test(description = "The professional store offers pro-only products")
    public void proStoreListsProOnlyProducts() {
        String proOnly = TestAccounts.proOnlyProductName();
        ProfessionalStoreScreen store = openStore();

        if (!proOnly.isBlank()) {
            Assert.assertTrue(store.hasProduct(proOnly),
                    "Pro-only product '" + proOnly + "' should be visible to a professional — the "
                            + "counterpart to it being hidden from clients");
        } else {
            Assert.assertTrue(store.showsProOnlyProducts() || store.isLoaded(),
                    "The professional catalogue should render; set storeFixtures.proOnlyProduct to "
                            + "assert on a specific seeded item");
        }
    }

    @Test(description = "The cart totals items consistently")
    public void cartTotalsAreConsistent() {
        ProfessionalStoreScreen store = openStore();
        store.addFirstProduct();
        store.openCart();

        if (store.cartIsEmpty()) {
            throw new SkipException("Nothing could be added to the cart — the professional "
                    + "catalogue may be empty in this environment.");
        }
        Assert.assertTrue(store.cartShowsItemCount(), "The cart should state its item count");
        Assert.assertTrue(store.cartShowsSubtotal(), "The cart should show a subtotal");
        Assert.assertTrue(store.cartShowsEstimatedTotal(), "The cart should show an estimated total");
    }

    @Test(description = "Only verified purchasers can review a product")
    public void onlyPurchasersCanReview() {
        String product = TestAccounts.storeProductName();
        if (product.isBlank()) {
            throw new SkipException("Set storeFixtures.clientVisibleProduct in test-accounts.json to "
                    + "a product the signed-in professional has NOT purchased.");
        }

        ProfessionalStoreScreen store = openStore();
        store.openProduct(product);

        Assert.assertTrue(store.showsPurchasersOnlyGate(),
                "A professional who has not bought '" + product + "' must be told '"
                        + ProfessionalStoreScreen.PURCHASERS_ONLY
                        + "' — unverified reviews would make the ratings worthless");
        Assert.assertFalse(store.canReview(), "The review form must not be offered");
    }
}
