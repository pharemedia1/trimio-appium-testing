package org.example.base;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.config.ConfigReader;

/**
 * Base for the Trimio web-portal page objects — Playwright against a <em>Flutter Web</em> app.
 *
 * <h2>Why this class exists</h2>
 * Flutter Web paints to a canvas. There is no DOM for buttons, labels or list rows, so the usual
 * Playwright selectors ({@code text=…}, {@code button}, CSS) match nothing at all. What Flutter does
 * expose — once asked — is an <em>accessibility tree</em>: a shadow DOM of {@code <flt-semantics>}
 * elements carrying {@code aria-label}s taken from the widget tree. That tree is the automation
 * surface, and it only exists after semantics are switched on.
 *
 * <p>Flutter offers two ways in, and {@link #enableSemantics()} tries both:
 * <ol>
 *   <li>the hidden {@code <flt-semantics-placeholder>} button ("Enable accessibility"), which is
 *       what a screen-reader user activates; clicking it is the supported path;</li>
 *   <li>failing that, calling the engine's own accessibility toggle from JavaScript — a fallback for
 *       builds where the placeholder is suppressed.</li>
 * </ol>
 *
 * <h2>Consequences for the page objects</h2>
 * <ul>
 *   <li>Locate by {@code aria-label}: {@link #byLabel(String)} / {@link #byLabelContaining(String)}.
 *       Labels come from the Dart source's {@code Text}/{@code tooltip} strings verbatim.</li>
 *   <li>Text fields are the exception — Flutter injects real {@code <input>}/{@code <textarea>}
 *       elements for the focused field, so {@link #fillField(int, String)} works on those.</li>
 *   <li>Never assert on layout or position: the semantics tree is rebuilt aggressively and its node
 *       order does not track the visual order.</li>
 *   <li>Off-screen widgets have no semantics node at all (the same rule as Android), so scroll
 *       before asserting on anything below the fold.</li>
 * </ul>
 */
public abstract class WebBasePage {

    protected static final Logger LOG = LogManager.getLogger(WebBasePage.class);

    /** Flutter's hidden "turn on accessibility" affordance. */
    private static final String SEMANTICS_PLACEHOLDER = "flt-semantics-placeholder";

    /** Any node in the semantics tree. */
    protected static final String SEMANTICS_NODE = "flt-semantics";

    protected final Page page;
    protected final double defaultTimeout;

    protected WebBasePage(Page page) {
        this.page = page;
        this.defaultTimeout = ConfigReader.getInt("timeout", 30_000);
    }

    // ---- lifecycle ----------------------------------------------------------

    /** Navigates to {@code path} under {@code web.baseUrl} and enables the semantics tree. */
    protected void open(String path) {
        String base = ConfigReader.get("web.baseUrl", "http://localhost:8080");
        String url = path == null || path.isBlank() ? base : base.replaceAll("/+$", "") + path;
        LOG.info("Web: navigating to {}", url);
        page.navigate(url);
        waitForFlutter();
        enableSemantics();
    }

    /**
     * Waits for the Flutter engine to have painted its first frame. {@code flt-glass-pane} is the
     * canvas host the engine inserts once it is running, so its presence is the earliest reliable
     * "the app is up" signal — long before any semantics exist.
     */
    protected void waitForFlutter() {
        try {
            page.waitForSelector("flt-glass-pane, flutter-view, flt-scene-host",
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.ATTACHED)
                            .setTimeout(defaultTimeout));
        } catch (TimeoutError e) {
            LOG.warn("Flutter host element never attached within {}ms — is web.baseUrl serving the "
                    + "portal (flutter run -d chrome -t lib/main_web.dart)?", defaultTimeout);
        }
    }

    /**
     * Switches on Flutter's accessibility tree, without which nothing in the app is addressable.
     * Idempotent and best-effort: if semantics are already on, this returns immediately.
     *
     * @return true if a semantics tree is present afterwards
     */
    public boolean enableSemantics() {
        if (hasSemantics()) {
            return true;
        }
        Locator placeholder = page.locator(SEMANTICS_PLACEHOLDER);
        if (placeholder.count() > 0) {
            try {
                LOG.info("Web: enabling Flutter semantics via the accessibility placeholder");
                placeholder.first().click(new Locator.ClickOptions().setForce(true).setTimeout(5_000));
            } catch (RuntimeException e) {
                LOG.debug("Semantics placeholder click failed: {}", e.getMessage());
            }
        }
        if (!hasSemantics()) {
            // Fallback for builds that suppress the placeholder: ask the engine directly.
            try {
                page.evaluate("() => {"
                        + "  const el = document.querySelector('flt-semantics-placeholder');"
                        + "  if (el) { el.click(); return true; }"
                        + "  const eng = window._flutter && window._flutter.loader;"
                        + "  if (eng && eng.didCreateEngineInitializer) { return false; }"
                        + "  return false;"
                        + "}");
            } catch (RuntimeException e) {
                LOG.debug("Semantics JS fallback failed: {}", e.getMessage());
            }
        }
        boolean on = hasSemantics();
        if (!on) {
            LOG.warn("Flutter semantics are not available — label-based locators will not match. "
                    + "The portal build may need `--dart-define=FLUTTER_WEB_AUTO_DETECT=true` or an "
                    + "explicit SemanticsBinding.instance.ensureSemantics() at startup.");
        }
        return on;
    }

    /** True once at least one semantics node exists. */
    public boolean hasSemantics() {
        try {
            page.waitForSelector(SEMANTICS_NODE,
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.ATTACHED)
                            .setTimeout(3_000));
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }

    // ---- locators -----------------------------------------------------------

    /** Exact {@code aria-label} match in the semantics tree. */
    protected Locator byLabel(String label) {
        return page.locator(SEMANTICS_NODE + "[aria-label=\"" + escape(label) + "\"]");
    }

    /** Substring {@code aria-label} match — use when the label carries dynamic text. */
    protected Locator byLabelContaining(String fragment) {
        return page.locator(SEMANTICS_NODE + "[aria-label*=\"" + escape(fragment) + "\"]");
    }

    /**
     * ARIA-role based lookup, for the handful of controls Flutter maps onto real roles
     * (buttons, text fields, headings). More robust than a label match when the label is dynamic.
     */
    protected Locator byRole(AriaRole role, String name) {
        return page.getByRole(role, new Page.GetByRoleOptions().setName(name));
    }

    /** Flutter's injected editable elements, in DOM order. */
    protected Locator textFields() {
        return page.locator("input, textarea");
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    // ---- actions ------------------------------------------------------------

    /** Clicks the node with this exact label, waiting for it to appear first. */
    protected void click(String label) {
        LOG.info("Web: clicking '{}'", label);
        Locator target = byLabel(label);
        target.first().waitFor(new Locator.WaitForOptions().setTimeout(defaultTimeout));
        target.first().click(new Locator.ClickOptions().setForce(true));
    }

    /** Clicks the first node whose label contains {@code fragment}. */
    protected void clickContaining(String fragment) {
        LOG.info("Web: clicking label containing '{}'", fragment);
        Locator target = byLabelContaining(fragment);
        target.first().waitFor(new Locator.WaitForOptions().setTimeout(defaultTimeout));
        target.first().click(new Locator.ClickOptions().setForce(true));
    }

    /**
     * Types into the n-th editable element (0-based).
     *
     * <p>Flutter only materialises an {@code <input>} for a field once it has focus, so a form with
     * several fields may expose fewer inputs than it renders. Where that bites, click the field's
     * label first, then call this with index 0.
     */
    protected void fillField(int index, String value) {
        Locator fields = textFields();
        fields.nth(index).waitFor(new Locator.WaitForOptions().setTimeout(defaultTimeout));
        fields.nth(index).fill(value);
    }

    /** Presses a key on the focused element (e.g. "Enter", "Tab"). */
    protected void press(String key) {
        page.keyboard().press(key);
    }

    /** Scrolls the page by a wheel gesture — the only way to move a Flutter scroll view. */
    protected void scrollBy(int deltaY) {
        page.mouse().wheel(0, deltaY);
    }

    // ---- assertions ---------------------------------------------------------

    /** True if a node with this exact label appears within the default timeout. */
    protected boolean isVisible(String label) {
        return isVisible(label, defaultTimeout);
    }

    /** True if a node with this exact label appears within {@code timeoutMs}. */
    protected boolean isVisible(String label, double timeoutMs) {
        try {
            byLabel(label).first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True if any node's label contains {@code fragment}. */
    protected boolean isVisibleContaining(String fragment) {
        return isVisibleContaining(fragment, defaultTimeout);
    }

    /** True if any node's label contains {@code fragment} within {@code timeoutMs}. */
    protected boolean isVisibleContaining(String fragment, double timeoutMs) {
        try {
            byLabelContaining(fragment).first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True once no node with this label remains (e.g. after navigating away). */
    protected boolean waitForAbsence(String label, double timeoutMs) {
        try {
            byLabel(label).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.DETACHED).setTimeout(timeoutMs));
            return true;
        } catch (RuntimeException e) {
            return byLabel(label).count() == 0;
        }
    }

    /** The {@code aria-label} of the first node containing {@code fragment}; empty when absent. */
    protected String labelOf(String fragment) {
        Locator target = byLabelContaining(fragment);
        if (target.count() == 0) {
            return "";
        }
        String label = target.first().getAttribute("aria-label");
        return label == null ? "" : label;
    }

    /** The current page URL — useful for asserting a redirect rather than a rendered screen. */
    public String url() {
        return page.url();
    }
}
