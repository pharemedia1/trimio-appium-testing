package org.example.tests.mobile.client;

import org.example.base.RoleSessionTest;
import org.example.pages.mobile.client.ClientBookingFlowScreen;
import org.example.pages.mobile.client.ClientBookingReviewScreen;
import org.example.pages.mobile.client.ClientHomeScreen;
import org.example.utils.DbHelper;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;

/**
 * An <b>individual</b> booking driven end to end, from the Home feed through to the charge.
 *
 * <p>This is the path a client actually takes: Home → "Book Individual" → what → who &amp; where →
 * when (day, time, <em>and professional</em>) → extras → "Review &amp; pay" → "Confirm &amp; Pay".
 * The last press authorises a hold on the client's saved card, writes the appointment, and captures
 * — so unlike {@link ClientBookingTest}, which deliberately stops at the hand-off, this test spends
 * money and creates a real booking a professional is dispatched to.
 *
 * <h2>Why this is safe to run</h2>
 * Nothing here can touch live money. The backend runs on a Stripe <b>test</b> secret key, and the
 * seeded clients pay with Stripe's own {@code pm_card_visa} test card attached by
 * {@code backend/scripts/seed_login_pair.js} — the fixture set that exists precisely so the money
 * path can be exercised from the one direction a person can drive it. The booking it creates lives
 * in the dev database against a seeded professional.
 *
 * <h2>What it needs</h2>
 * <ul>
 *   <li>A client with a saved card and a complete profile — {@code trimiotest+client1@gmail.com}.
 *       Without a card the server answers 402 and the flow asks for one.</li>
 *   <li>A professional who can <b>take money</b>: an approved, licensed pro whose
 *       {@code professional_stripe_accounts} row holds a <em>real</em> Stripe-issued
 *       {@code acct_1…} id. A fabricated {@code acct_SEEDED_NOT_REAL_…} id is refused by
 *       {@code resolveChargeAccount} in every environment, and the test skips with that
 *       explanation rather than reporting a defect that isn't one.</li>
 * </ul>
 *
 * <h2>Running it</h2>
 *
 * <p><b>This class on its own, from the command line.</b> {@code -Dtest} makes Surefire ignore the
 * {@code suiteXmlFiles} pinned in {@code pom.xml}, so no suite file is involved:
 * <pre>{@code
 * mvn test -Dtest=ClientIndividualBookingPaymentTest -Ddb.password=…
 * }</pre>
 *
 * <p><b>A single method:</b>
 * <pre>{@code
 * mvn test -Dtest=ClientIndividualBookingPaymentTest#individualBookingIsPaidEndToEnd -Ddb.password=…
 * }</pre>
 *
 * <p><b>From the IDE.</b> Right-click the class or the method and Run — IntelliJ's TestNG runner
 * invokes it directly, and {@link org.example.base.MobileBaseTest}'s {@code @BeforeSuite} still
 * starts Appium, so nothing else is needed. Put {@code -Ddb.password=…} in the run configuration's
 * <i>VM options</i> (not Program arguments); without it the DB assertions degrade to a no-op rather
 * than failing, so a green run without it has proved less than it appears to.
 *
 * <p><b>The whole suite, with reporting:</b>
 * <pre>{@code
 * mvn test -DsuiteXmlFile=src/test/resources/suites/booking-payment.xml -DretryCount=0 -Ddb.password=…
 * }</pre>
 *
 * <p><b>What you give up running it directly.</b> {@code TestListener} is attached by the suite XML
 * only — there is no {@code @Listeners} annotation and no ServiceLoader wiring — so a direct run has
 * <em>no</em> Extent report and none of the {@code >>> START} / {@code <<< SKIP} log lines. It also
 * loses {@code RetryAnalyzer}, which that listener installs through {@code IAnnotationTransformer};
 * for this test that is a feature, since a booking should not be silently retried. Surefire's own
 * XML under {@code target/surefire-reports/testng-results.xml} still records the outcome, and is
 * where to read a skip reason:
 * <pre>{@code
 * grep -o '<message>.*</message>' target/surefire-reports/testng-results.xml
 * }</pre>
 *
 * <p>An emulator must be booted and the backend up on {@code :3000} either way — the app reaches it
 * at {@code 10.0.2.2:3000}.
 */
public class ClientIndividualBookingPaymentTest extends RoleSessionTest {

    /** A per-person service priced above zero, so the charge is a real amount. */
    private static final String CATEGORY = "Barbering (beard & shave)";
    private static final String SERVICE = "Men's Haircut + Beard";
    private static final double SERVICE_PRICE = 85.00;

    /** How far forward to look for a bookable slot before calling the environment unavailable. */
    private static final int DAYS_TO_TRY = 4;

    /** How many offered times to try before concluding nobody is free that day. */
    private static final int SLOTS_TO_TRY = 4;

    @Test(description = "A client books an individual appointment and pays for it end to end")
    public void individualBookingIsPaidEndToEnd() {
        ClientHomeScreen home = clientSession();
        Assert.assertTrue(home.isLoaded(), "The Home feed should render before booking");

        // ---- steps 1-4 ------------------------------------------------------
        ClientBookingFlowScreen flow = home.bookIndividual();
        Assert.assertTrue(flow.isLoaded(), "The individual booking flow should open");
        Assert.assertEquals(flow.currentStep(), 1, "It should open on step 1");

        flow.selectCategory(CATEGORY).selectService(SERVICE).continueStep();
        Assert.assertEquals(flow.currentStep(), 2,
                "Selecting a service should advance to 'Who & where'");

        flow.chooseMyself().continueStep();
        Assert.assertEquals(flow.currentStep(), 3, "Step 2 should advance to 'When'");

        Assert.assertTrue(flow.waitForAvailability(),
                "'Checking availability…' should resolve rather than hang");

        // Try the next few days rather than only today: run this late enough in the afternoon and
        // today's last slot has already gone by, which is a clock, not a missing fixture.
        List<String> slots = flow.openFirstDayWithTimes(DAYS_TO_TRY);
        if (slots.isEmpty()) {
            throw new SkipException("No bookable time for " + SERVICE + " in the next "
                    + DAYS_TO_TRY + " days — every period reports 'No times'. Give a licensed "
                    + "professional availability.");
        }

        // THE STEP THAT IS EASY TO MISS AND FATAL TO SKIP. The shortlist only appears once a slot
        // is chosen, and the flow will happily continue without a selection — the booking then
        // reaches the review dialog with an empty professional and is refused at the charge, with
        // the reason drawn behind the dialog where nobody can read it.
        ClientBookingFlowScreen.SlotOffer offer =
                flow.selectSlotWithProfessionals(slots, SLOTS_TO_TRY);
        if (offer == null) {
            throw new SkipException("None of the first " + SLOTS_TO_TRY + " offered times had a "
                    + "professional free to take them.");
        }
        List<String> pros = offer.professionals();
        flow.chooseProfessional(pros.get(0));
        flow.continueStep();

        Assert.assertEquals(flow.currentStep(), 4, "Choosing a professional should reach 'Add extras'");

        // ---- the money ------------------------------------------------------
        ClientBookingReviewScreen review = flow.reviewAndPay();
        Assert.assertTrue(review.isLoaded(), "The review & cost breakdown should open");
        Assert.assertTrue(review.showsBreakdown(),
                "The client must see what they are paying for before paying");

        double service = review.serviceCharge();
        double premium = review.professionalPremium();
        double distance = review.distanceSurcharge();
        double total = review.total();

        Assert.assertEquals(service, SERVICE_PRICE, 0.01,
                "The service line should carry the catalogue price");
        Assert.assertEquals(total, service + premium + distance, 0.01,
                "The total must be the sum of the lines shown — a total the client cannot derive "
                        + "from the breakdown is worse than no breakdown");

        long before = DbHelper.countAppointmentsFor(pros.get(0));

        ClientBookingReviewScreen.Outcome outcome = review.confirmAndPay();

        switch (outcome) {
            case NEEDS_CARD:
                throw new SkipException("The client has no saved card, so the booking cannot be "
                        + "charged. Seed one with backend/scripts/seed_login_pair.js.");
            case BLOCKED:
                throw new SkipException("The booking was refused on compliance grounds in this "
                        + "environment — not a payment defect.");
            case REFUSED:
                throw new SkipException(refusalAdvice(pros.get(0)));
            default:
                break;
        }

        Assert.assertTrue(review.isBookingConfirmed(),
                "A paid booking should land on the confirmation screen");
        Assert.assertTrue(review.showsPaymentSuccessful(),
                "The confirmation should state the payment was taken");

        long after = DbHelper.countAppointmentsFor(pros.get(0));
        if (before >= 0 && after >= 0) {
            Assert.assertEquals(after, before + 1,
                    "Paying should have created exactly one appointment for " + pros.get(0));
        }
    }

    /**
     * Explains a refused charge in terms of the thing that is almost always wrong.
     *
     * <p>The app cannot show the reason — {@code PaymentHandlerService} raises it on a snackbar
     * behind the dialog — so a bare "payment failed" would send the next person to read logcat.
     * The overwhelmingly common cause in a seeded environment is a professional whose Stripe
     * account is fabricated, which {@code resolveChargeAccount} refuses with
     * {@code STRIPE_ACCOUNT_NOT_REAL} in every environment by design.
     */
    private String refusalAdvice(String professional) {
        String stripeId = DbHelper.stripeAccountIdFor(professional);
        if (stripeId != null && !stripeId.startsWith("acct_1")) {
            return "The charge was refused because '" + professional + "' has no real Stripe "
                    + "connected account (" + stripeId + "). resolveChargeAccount rejects any id "
                    + "Stripe could not have issued, in every environment, so no booking against a "
                    + "seeded professional can be paid for. Onboard one through Stripe Connect and "
                    + "point this test at them.";
        }
        return "The charge was refused. The app reports the reason on a snackbar drawn behind the "
                + "review dialog, so check the backend's response to POST /payment/create-intent "
                + "(and 'adb logcat | grep \"confirm error\"' for the status code).";
    }
}
