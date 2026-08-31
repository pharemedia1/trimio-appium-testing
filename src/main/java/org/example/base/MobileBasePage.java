package org.example.base;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.constants.FrameworkConstants;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base for all mobile page objects. Holds the {@link AndroidDriver} and a default
 * {@link WebDriverWait}, and exposes small reusable actions so the screen classes stay
 * declarative (locators + intent) rather than repeating wait/typing boilerplate.
 *
 * <p>Selector helpers map directly to how the Trimio Flutter app exposes itself to
 * UiAutomator2: Flutter {@code Semantics(label:'x')} and button/label text both surface as
 * Android <em>accessibility ids</em> (content-desc), while plain text fields are reachable by
 * their {@code EditText} index.
 */
public abstract class MobileBasePage {

    protected static final Logger LOG = LogManager.getLogger(MobileBasePage.class);

    /** Upper bound on UiScrollable swipes — long enough for the admin/pro dashboards, short
     *  enough that a genuinely missing element fails in seconds rather than minutes. */
    private static final int MAX_SCROLL_SWIPES = 12;

    /** Wait used for "is it already here?" probes and negative checks. */
    protected static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);

    protected final AndroidDriver driver;
    protected final WebDriverWait wait;

    protected MobileBasePage(AndroidDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofMillis(FrameworkConstants.DEFAULT_TIMEOUT_MS));
    }

    // ---- locator helpers ----------------------------------------------------

    /** Accessibility id (content-desc) — Flutter Semantics label, button text, or label text. */
    protected static By accId(String id) {
        return AppiumBy.accessibilityId(id);
    }

    /** The n-th editable text field on screen (0-based) — Flutter TextFields carry no a11y id. */
    protected static By editText(int index) {
        return AppiumBy.androidUIAutomator(
                "new UiSelector().className(\"android.widget.EditText\").instance(" + index + ")");
    }

    /**
     * Content-desc <em>contains</em> {@code text}. Needed when a Flutter {@code Semantics(label:…)}
     * wraps a child Text, because the two get merged into one content-desc
     * (e.g. "login_error_message\nInvalid email or password."), so an exact id won't match.
     */
    protected static By descContains(String text) {
        return AppiumBy.androidUIAutomator(
                "new UiSelector().descriptionContains(\"" + text + "\")");
    }

    /**
     * A <b>Button</b> whose content-desc contains {@code text}.
     *
     * <p>Needed wherever a dialog repeats its choice inside its own message. The rebook prompt's
     * body reads "…or choose a New professional?", so a bare {@link #descContains(String)} for
     * "New professional" resolves to the message paragraph — which is a real, clickable node, so
     * the tap "succeeds" against a 744×105 block of text and the dialog simply stays put. Verified
     * on-device: the button is 353×126 and 116px lower. Constraining to the Button class picks the
     * control rather than the prose describing it.
     */
    protected static By buttonDescContains(String text) {
        return AppiumBy.androidUIAutomator(
                "new UiSelector().className(\"android.widget.Button\").descriptionContains(\""
                        + text + "\")");
    }

    /**
     * Matches either the content-desc <em>or</em> the rendered text containing {@code value}.
     *
     * <p>Most Trimio screens carry no explicit {@code Semantics} (only two exist in the whole app),
     * so a widget surfaces as a node whose content-desc is its text — but a few list/table cells come
     * through as {@code TextView}s with a {@code text} attribute instead. This selector covers both
     * without the caller having to know which.
     */
    protected static By descOrText(String value) {
        String escaped = value.replace("\"", "\\\"");
        return AppiumBy.androidUIAutomator(
                "new UiSelector().descriptionContains(\"" + escaped + "\")");
    }

    /** The n-th checkable widget (Checkbox/Switch) on screen — Flutter toggles carry no a11y id. */
    protected static By checkable(int index) {
        return AppiumBy.androidUIAutomator(
                "new UiSelector().checkable(true).instance(" + index + ")");
    }

    // ---- scrolling ----------------------------------------------------------

    /**
     * Scrolls the first scrollable container until a node whose content-desc contains {@code text}
     * is on screen, and returns whether it was found.
     *
     * <p>This is not a nicety on the long Trimio dashboards: Flutter drops the semantics of widgets
     * that are off-screen, so a control below the fold is genuinely <em>absent</em> from the
     * accessibility tree until it is scrolled into view. Anything below the first viewport must be
     * reached this way rather than with a plain wait.
     */
    protected boolean scrollToDesc(String text) {
        String escaped = text.replace("\"", "\\\"");
        try {
            driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true).instance(0))"
                            + ".setMaxSearchSwipes(" + MAX_SCROLL_SWIPES + ")"
                            + ".scrollIntoView(new UiSelector().descriptionContains(\"" + escaped + "\"))"));
        } catch (RuntimeException e) {
            LOG.debug("scrollToDesc('{}') did not find the element: {}", text, e.getMessage());
            return false;
        }
        // CONFIRM IT, do not take scrollIntoView's word for it. That call resolves to the
        // SCROLLABLE CONTAINER, not the target, so it can succeed having never found what it was
        // asked for — and this method then reported "found" for text that is not on screen.
        // Every isPresentAfterScroll caller inherited that false positive: an assertion could pass
        // against a screen which simply happens to be scrollable. Measured live against the admin
        // Pending queue, which lists professionals by NAME: a search for an email returned true
        // while the page source contained no such string anywhere.
        boolean reallyThere = isPresent(descContains(text), SHORT_TIMEOUT);
        if (!reallyThere) {
            LOG.debug("scrollToDesc('{}') scrolled but the text is still absent", text);
        }
        return reallyThere;
    }

    /**
     * Flings the first scrollable to its very end.
     *
     * <p>Needed wherever a control only unlocks once the user has genuinely reached the bottom —
     * a read-before-agree document, a long consent form. Scrolling "until the button appears"
     * cannot express that: a DISABLED Flutter button is still in the accessibility tree, so it is
     * found immediately and the scroll never happens.
     */
    protected void flingToEnd() {
        try {
            driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true).instance(0))"
                            + ".flingToEnd(" + MAX_SCROLL_SWIPES + ")"));
        } catch (RuntimeException e) {
            LOG.debug("flingToEnd found nothing scrollable: {}", e.getMessage());
        }
    }

    /** True if the element exists AND the platform reports it as enabled (not greyed out). */
    protected boolean isEnabled(By by, Duration timeout) {
        if (!isPresent(by, timeout)) return false;
        return Boolean.parseBoolean(find(by).getAttribute("enabled"));
    }

    /** A short pause, for the handful of places where a value round-trips to the server. */
    protected void sleepBriefly() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Scrolls {@code text} into view and taps it. Fails the wait if it never appears. */
    protected void scrollAndTap(String text) {
        scrollToDesc(text);
        tap(descContains(text));
    }

    /** True if {@code text} is on screen already, or can be reached by scrolling. */
    protected boolean isPresentAfterScroll(String text) {
        return isPresent(descContains(text), SHORT_TIMEOUT) || scrollToDesc(text);
    }

    // ---- actions ------------------------------------------------------------

    /** Waits for the element to exist, then taps it (center tap works even under the keyboard). */
    protected void tap(By by) {
        wait.until(ExpectedConditions.presenceOfElementLocated(by)).click();
    }

    /**
     * Taps a point <em>inside</em> an element, given as fractions of its width and height
     * (0.0 = left/top, 1.0 = right/bottom).
     *
     * <p>Needed wherever Flutter merges a whole card into one accessibility node. The client
     * storefront is the clearest case: a product row exports a single node labelled
     * {@code "Argan Repair Shampoo\nHair Care\n$18\nAdd"} — the "Add" button has no node of its own,
     * so there is nothing to locate and click. The only way in is to hit its position within the
     * card. Verified on-device: a centre tap and a bottom-right tap both do nothing, while the
     * right-edge/lower-third point actually adds the item.
     *
     * <p>This is coordinate-based and therefore the most layout-sensitive thing in the framework —
     * use it only when an element genuinely has no node, and keep the fractions next to the
     * screen that calibrated them.
     */
    protected void tapWithin(By by, double xFraction, double yFraction) {
        tapWithin(wait.until(ExpectedConditions.presenceOfElementLocated(by)), xFraction, yFraction);
    }

    /** As {@link #tapWithin(By, double, double)}, for an element the caller already resolved. */
    protected void tapWithin(WebElement element, double xFraction, double yFraction) {
        Rectangle bounds = element.getRect();
        int x = bounds.getX() + (int) (bounds.getWidth() * xFraction);
        int y = bounds.getY() + (int) (bounds.getHeight() * yFraction);
        LOG.info("Tapping inside element at ({}, {}) [{}%, {}% of {}x{}]",
                x, y, (int) (xFraction * 100), (int) (yFraction * 100),
                bounds.getWidth(), bounds.getHeight());

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, Duration.ofMillis(80)))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(tap));
    }

    /** Focuses the field and types {@code value}. */
    protected void type(By by, String value) {
        WebElement field = wait.until(ExpectedConditions.presenceOfElementLocated(by));
        field.click();
        field.clear();
        field.sendKeys(value);
    }

    /**
     * Dismisses the soft keyboard if it is up, <b>without leaving the current screen</b>.
     *
     * <h3>Why this does not call {@code driver.hideKeyboard()}</h3>
     * On Android, Appium implements {@code hideKeyboard()} by pressing <b>BACK</b>. In a Flutter
     * app BACK is a navigation event, so on the registration form it does not close the keyboard —
     * it pops the whole screen. Measured on-device (Android 15 / UiAutomator2), immediately after
     * a {@code hideKeyboard()} call:
     * <pre>
     *   before:  3 EditText · 1 CheckBox · 1 "Create account" · pageSource 17009 chars
     *   after:   0 EditText · 0 CheckBox · 0 "Create account" · pageSource  9547 chars
     * </pre>
     * Every subsequent lookup then times out against a screen that is no longer there — which reads
     * like a broken selector but is really a lost screen. This is the same BACK trap noted for
     * manual key presses, reached through an API whose name promises something else.
     *
     * <p>{@code mobile: performEditorAction} with "done" sends the IME action instead, which closes
     * the keyboard and leaves the form fully intact (verified: all counts unchanged afterwards).
     *
     * <p>Best-effort: if the keyboard cannot be dismissed, the caller proceeds anyway — the taller
     * forms remain addressable with the keyboard up, so this is an optimisation, not a precondition.
     * It deliberately has <em>no</em> fallback to {@code driver.hideKeyboard()}.
     */
    protected void hideKeyboard() {
        try {
            if (!driver.isKeyboardShown()) {
                return;
            }
        } catch (Exception e) {
            return;  // Driver can't answer the question — don't guess, and don't press anything.
        }
        try {
            driver.executeScript("mobile: performEditorAction", Map.of("action", "done"));
        } catch (Exception e) {
            LOG.debug("Could not dismiss the keyboard via performEditorAction ({}); continuing with "
                    + "it open rather than risking a BACK press.", e.getMessage());
        }
    }

    /**
     * Answers Android's runtime location prompt, if it is up.
     *
     * <p>The booking flow asks for location the first time it opens, and the prompt is a
     * <b>system</b> dialog from the permission controller, not part of the app. While it is
     * showing, Flutter's semantics tree is empty — every app locator times out against a screen
     * that is really there but covered, which reads exactly like a broken selector. Verified
     * on-device: the tree goes from the full Home feed to zero nodes and back.
     *
     * <p>{@code autoGrantPermissions} does not cover it. That capability grants manifest
     * permissions at <em>install</em> time, and the suite installs once and then clears app data
     * per session — which revokes the grant and brings the prompt back on the next run.
     *
     * <p>Best-effort and quick: no prompt is the normal case once granted.
     *
     * @return true if a prompt was found and allowed
     */
    protected boolean allowLocationIfAsked() {
        return allowLocationIfAsked(Duration.ofSeconds(12));
    }

    /**
     * @param timeout how long to wait for the prompt to appear. The permission controller is a
     *     separate process and the dialog is raised only once Flutter asks, so it can lag the tap
     *     that triggered it by several seconds — a short wait returns "no prompt" while one is
     *     still on its way, and the caller then times out against a covered screen.
     */
    protected boolean allowLocationIfAsked(Duration timeout) {
        By allow = By.xpath("//*[contains(@text,'While using the app') or contains(@text,'Only this time')]");
        if (!isPresent(allow, timeout)) {
            return false;
        }
        LOG.info("Answering the system location prompt");
        tap(allow);
        sleepBriefly();
        return true;
    }

    protected String getText(By by) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(by)).getText();
    }

    /** True if the element appears within the default timeout. */
    protected boolean isPresent(By by) {
        try {
            return wait.until(ExpectedConditions.presenceOfElementLocated(by)) != null;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** True if the element appears within a custom timeout (use short waits for negative checks). */
    protected boolean isPresent(By by, Duration timeout) {
        try {
            new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.presenceOfElementLocated(by));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** True if no element matches right now (no waiting) — handy after an action removes a node. */
    protected boolean isAbsent(By by) {
        List<WebElement> found = driver.findElements(by);
        return found.isEmpty();
    }

    /** Waits until the element is gone (e.g. a screen we navigated away from). */
    protected boolean waitForAbsence(By by, Duration timeout) {
        try {
            return new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.invisibilityOfElementLocated(by));
        } catch (TimeoutException e) {
            return false;
        }
    }

    protected WebElement find(By by) {
        try {
            return driver.findElement(by);
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
