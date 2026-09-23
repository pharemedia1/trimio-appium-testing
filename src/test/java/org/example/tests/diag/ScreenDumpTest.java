package org.example.tests.diag;

import io.appium.java_client.AppiumBy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.base.RoleSessionTest;
import org.example.pages.mobile.common.BottomNavBar;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import java.util.List;

/**
 * A diagnostic that prints what is actually on a screen. Not an assertion — it never fails.
 *
 * <p><b>Why this exists.</b> Three separate defects in this suite were stale locator constants
 * guessed from Dart source: the booking confirmation said "You're booked!" where the page object
 * looked for "Booking Confirmed", the extras step's SUBTOTAL had no amount merged into its node,
 * and a row of admin anchors carried characters {@code UiSelector} cannot match. Each cost a full
 * run, and each was settled in seconds once the screen was dumped. Guessing is the expensive
 * option; this is the cheap one.
 *
 * <p>Excluded from every suite XML — run it deliberately:
 * {@code mvn -o test -Dtest=ScreenDumpTest#membership -DfailIfNoSpecifiedTests=false}
 */
public class ScreenDumpTest extends RoleSessionTest {

    private static final Logger DUMP = LogManager.getLogger(ScreenDumpTest.class);

    /** Prints every non-blank content-desc, in tree order, with its y-coordinate. */
    private void dump(String label) {
        DUMP.info("===== SCREEN DUMP: {} =====", label);
        List<WebElement> nodes = driver.findElements(
                AppiumBy.androidUIAutomator("new UiSelector().descriptionMatches(\".+\")"));
        int i = 0;
        for (WebElement n : nodes) {
            String desc = n.getAttribute("content-desc");
            if (desc == null || desc.isBlank()) {
                continue;
            }
            DUMP.info("  [{}] y={} {}", i++, n.getLocation().getY(), desc.replace("\n", " | "));
        }
        DUMP.info("===== END DUMP: {} ({} nodes) =====", label, i);
    }

    @Test(description = "DIAG: what the client's membership area actually renders")
    public void membership() {
        loginAsClient();
        new BottomNavBar(driver).open(BottomNavBar.CLIENT_PROFILE);
        dump("client profile tab");

        driver.findElement(AppiumBy.androidUIAutomator(
                "new UiScrollable(new UiSelector().scrollable(true))"
                        + ".scrollIntoView(new UiSelector().description(\"Manage\"))")).click();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        dump("after tapping Manage");
    }

    @Test(description = "DIAG: what the admin enforcement register actually renders")
    public void adminEnforcements() {
        org.example.pages.mobile.admin.AdminConsoleScreen console = loginAsAdmin();
        dump("admin console");
        console.openEnforcements();
        pause();
        dump("admin enforcements");
        // The list showed no Extend/Reinstate controls -- find out whether they live behind a row.
        try {
            driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiSelector().descriptionContains(\"Until \")")).click();
            pause();
            dump("enforcement record opened");
        } catch (RuntimeException e) {
            DUMP.info("could not open an enforcement record: {}", e.getMessage());
        }
    }

    @Test(description = "DIAG: what the client's first appointment detail renders")
    public void appointmentDetail() {
        loginAsClient();
        new BottomNavBar(driver).open(BottomNavBar.CLIENT_APPOINTMENTS);
        pause();
        dump("appointments list");
        org.example.pages.mobile.client.ClientAppointmentsScreen appts =
                new org.example.pages.mobile.client.ClientAppointmentsScreen(driver);
        appts.openFirst();
        pause();
        dump("appointment detail");
    }

    /** Dumps the raw XML tree, which shows nodes that carry no content-desc (e.g. bare icons). */
    private void dumpXml(String label) {
        DUMP.info("===== XML DUMP: {} =====", label);
        String src = driver.getPageSource();
        for (String line : src.split(">")) {
            String t = line.trim();
            if (t.contains("ImageView") || t.contains("Button") || t.contains("star")
                    || t.contains("clickable=\"true\"")) {
                DUMP.info("  {}", t.length() > 220 ? t.substring(0, 220) : t);
            }
        }
        DUMP.info("===== END XML: {} =====", label);
    }

    private void pause() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test(description = "DIAG: the admin Quality page and its Suspended list")
    public void adminQuality() {
        org.example.pages.mobile.admin.AdminConsoleScreen console = loginAsAdmin();
        console.openQuality();
        pause();
        dump("admin quality");
        try {
            driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiSelector().descriptionContains(\"Suspended\")")).click();
            pause();
            dump("quality suspended list");
            driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiSelector().descriptionContains(\"Pro #\")")).click();
            pause();
            dump("quality professional opened");
        } catch (RuntimeException e) {
            DUMP.info("could not open the Suspended list: {}", e.getMessage());
        }
    }

    @Test(description = "DIAG: the professional's tabs")
    public void professionalTabs() {
        loginAsProfessional();
        BottomNavBar nav = new BottomNavBar(driver);
        dump("pro dashboard");
        for (String tab : new String[]{BottomNavBar.PRO_BOOKINGS, BottomNavBar.PRO_CLIENT_HUB,
                                       BottomNavBar.PRO_STORE, BottomNavBar.PRO_ACCOUNT}) {
            try {
                nav.open(tab);
                pause();
                dump("pro tab: " + tab);
            } catch (RuntimeException e) {
                DUMP.info("could not open pro tab {}: {}", tab, e.getMessage());
            }
        }
    }

    @Test(description = "DIAG: the suspend-reason dialog on the Quality page")
    public void suspendDialog() {
        org.example.pages.mobile.admin.AdminQualityScreen q = loginAsAdmin().openQuality();
        q.openStatusList(org.example.pages.mobile.admin.AdminQualityScreen.CARD_WARNING);
        pause();
        q.openFirstProfessional();
        pause();
        dump("warning professional detail");
        q.openActions();
        pause();
        dump("actions opened");
        q.tapSuspend();
        pause();
        dump("suspend dialog");
        q.submitWithoutReason();
        pause();
        dump("after submitting with no reason");
    }

    @Test(description = "DIAG: how the review flow is reached from a past appointment")
    public void reviewFlow() {
        loginAsClient();
        new BottomNavBar(driver).open(BottomNavBar.CLIENT_APPOINTMENTS);
        pause();
        dump("appointments list");
        tapDesc("Open history");
        pause();
        dump("appointment history");
        // A past visit is not "Confirmed" -- its card carries a price.
        tapDesc("Rate your visit");
        pause();
        dump("after Rate your visit");
        org.example.pages.mobile.client.ClientReviewScreen r =
                new org.example.pages.mobile.client.ClientReviewScreen(driver);
        r.rateOverall(5);
        r.answerBookAgain(true);
        pause();
        dump("after rating 5 stars");
        for (int step = 2; step <= 4; step++) {
            tapDesc("Continue");
            pause();
            dump("review step " + step);
            r.rateAllAspects(5);
            pause();
            dump("review step " + step + " rated");
        }
    }

    @Test(description = "DIAG: the professional account tab, scrolled, looking for earnings")
    public void proAccountScrolled() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_ACCOUNT);
        pause();
        for (int i = 1; i <= 4; i++) {
            try {
                driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().scrollable(true)).scrollForward()"));
            } catch (RuntimeException e) {
                break;
            }
            pause();
            dump("pro account scroll " + i);
        }
    }

    @Test(description = "DIAG: the professional client hub")
    public void clientHub() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_CLIENT_HUB);
        pause();
        dump("client hub");
        tapDesc("Client");
        pause();
        dump("client hub row opened");
    }

    @Test(description = "DIAG: the admin users / pending professionals queue")
    public void adminUsers() {
        org.example.pages.mobile.admin.AdminConsoleScreen console = loginAsAdmin();
        console.openTile(org.example.pages.mobile.admin.AdminConsoleScreen.TILE_ALL_USERS);
        pause();
        dump("admin all users");
        org.example.pages.mobile.admin.AdminUsersScreen users =
                new org.example.pages.mobile.admin.AdminUsersScreen(driver);
        users.openProfessionals();
        pause();
        dump("admin professionals by status");
    }

    /** Taps the first node whose content-desc contains {@code text}; logs instead of failing. */
    private void tapDesc(String text) {
        try {
            driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true))"
                            + ".scrollIntoView(new UiSelector().descriptionContains(\"" + text + "\"))"))
                    .click();
        } catch (RuntimeException e) {
            try {
                driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiSelector().descriptionContains(\"" + text + "\")")).click();
            } catch (RuntimeException e2) {
                DUMP.info("could not tap '{}': {}", text, e2.getMessage());
            }
        }
    }

    @Test(description = "DIAG: the professional's booking detail and its actions")
    public void proBookingDetail() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_BOOKINGS);
        pause();
        org.example.pages.mobile.professional.ProfessionalBookingsScreen b =
                new org.example.pages.mobile.professional.ProfessionalBookingsScreen(driver);
        b.openFirstBooking();
        pause();
        dump("pro booking detail");
        // Flutter drops off-screen semantics, so anything below the fold is invisible until
        // scrolled to -- the same trap that hid the professional shortlist in the booking flow.
        for (int i = 1; i <= 3; i++) {
            try {
                driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().scrollable(true)).scrollForward()"));
            } catch (RuntimeException e) {
                DUMP.info("no further scroll: {}", e.getMessage());
                break;
            }
            pause();
            dump("pro booking detail scrolled " + i);
        }
    }

    @Test(description = "DIAG: does switching category actually filter the service list?")
    public void categoryFilter() {
        org.example.pages.mobile.client.ClientHomeScreen home = loginAsProvisionedClient();
        org.example.pages.mobile.client.ClientBookingFlowScreen flow = home.bookIndividual();
        pause();
        dump("booking step 1 (default category)");
        flow.selectCategory("Hair");
        pause();
        dump("category Hair");
        flow.selectCategory("Nail services");
        pause();
        dump("category Nail services");
    }

    @Test(description = "DIAG: the admin PENDING professionals list")
    public void adminPendingList() {
        org.example.pages.mobile.admin.AdminConsoleScreen console = loginAsAdmin();
        console.openTile(org.example.pages.mobile.admin.AdminConsoleScreen.TILE_ALL_USERS);
        pause();
        org.example.pages.mobile.admin.AdminUsersScreen users =
                new org.example.pages.mobile.admin.AdminUsersScreen(driver);
        users.openProfessionals();
        pause();
        users.openSegment(org.example.pages.mobile.admin.AdminUsersScreen.SEGMENT_PENDING);
        pause();
        dump("admin pending segment");
        scrollAndDump("admin pending segment", 2);
    }

    @Test(description = "DIAG: the client hub list rows and a client's profile")
    public void clientHubRows() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_CLIENT_HUB);
        pause();
        scrollAndDump("client hub", 3);
    }

    @Test(description = "DIAG: the professional account tab, scrolled, looking for earnings")
    public void proAccountEarnings() {
        loginAsProfessional();
        new BottomNavBar(driver).open(BottomNavBar.PRO_ACCOUNT);
        pause();
        scrollAndDump("pro account", 4);
    }

    /** Dumps the screen, then scrolls and dumps again, {@code times} times. */
    private void scrollAndDump(String label, int times) {
        for (int i = 1; i <= times; i++) {
            try {
                driver.findElement(AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().scrollable(true)).scrollForward()"));
            } catch (RuntimeException e) {
                DUMP.info("{}: no further scroll ({})", label, e.getMessage());
                return;
            }
            pause();
            dump(label + " scroll " + i);
        }
    }

    @Test(description = "DIAG: the Book tab's discovery cards and what a tap does")
    public void bookTabDiscovery() {
        org.example.pages.mobile.client.ClientHomeScreen home = loginAsProvisionedClient();
        home.nav().open(BottomNavBar.CLIENT_BOOK);
        pause();
        dump("book tab");
        dumpXml("book tab raw");
        home.bookFromDiscoveryCategory();
        pause();
        dump("after tapping a category card");
    }

    @Test(description = "DIAG: the booking date strip -- which days are offered, and what an empty one shows")
    public void dateStrip() {
        org.example.pages.mobile.client.ClientHomeScreen home = loginAsProvisionedClient();
        org.example.pages.mobile.client.ClientBookingFlowScreen flow = home.bookIndividual();
        flow.selectCategory("Hair");
        flow.selectService("Haircut");
        flow.continueStep();
        flow.chooseMyself();
        flow.continueStep();
        flow.waitForAvailability();
        pause();
        DUMP.info("currentStep={}", flow.currentStep());
        dump("step 3 default day");

        java.util.List<String> days = flow.offeredDays();
        DUMP.info("===== OFFERED DAYS ({}) =====", days.size());
        for (String d : days) {
            DUMP.info("  day chip: {}", d.replace("\n", " | "));
        }

        // Walk every offered day and record whether it reports no open times.
        for (int i = 1; i < days.size(); i++) {
            String chip = days.get(i);
            String[] parts = chip.split("\n");
            if (parts.length < 2) {
                continue;
            }
            tapDesc(parts[1]);
            flow.waitForAvailability();
            pause();
            DUMP.info("day '{}' -> showsNoOpenTimes={} periodsWithTimes={}",
                    chip.replace("\n", " "), flow.showsNoOpenTimes(),
                    flow.openFirstPeriodWithTimes().size());
        }
        dump("after walking every offered day");
    }
}
