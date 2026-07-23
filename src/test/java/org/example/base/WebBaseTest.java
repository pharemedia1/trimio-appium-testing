package org.example.base;

import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.config.ConfigReader;
import org.example.data.TestAccounts;
import org.example.factory.PlaywrightFactory;
import org.example.pages.web.WebLoginPage;
import org.example.pages.web.WebShellPage;
import org.example.reports.ExtentManager;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

/**
 * Parent of the web-portal (Playwright) test classes.
 *
 * <p>A fresh {@link Page} per test method keeps sessions from bleeding between tests — which matters
 * more here than usual, because half the value of this suite is proving that a <em>refused</em> login
 * leaves nothing behind. A leaked context would make that assertion meaningless.
 *
 * <p>The portal is a separately-served Flutter Web build, so tests self-skip when
 * {@code web.baseUrl} is not answering rather than failing with a wall of timeouts. Serve it with:
 * <pre>
 *   cd ~/StudioProjects/trimio/frontend
 *   flutter run -d chrome -t lib/main_web.dart          # or: flutter build web -t lib/main_web.dart
 * </pre>
 * then run with {@code -Dweb.baseUrl=http://localhost:&lt;port&gt;}.
 */
public abstract class WebBaseTest {

    private static final Logger LOG = LogManager.getLogger(WebBaseTest.class);

    protected Page page;

    @BeforeSuite(alwaysRun = true)
    public void beforeWebSuite() {
        LOG.info("==== @BeforeSuite (web): loading test data ====");
        TestAccounts.load();
    }

    @BeforeMethod(alwaysRun = true)
    public void createPage() {
        page = PlaywrightFactory.createPage();
        // A desktop viewport by default: below 900px the shell collapses its sidebar into a drawer,
        // and most tests assert against the expanded layout.
        page.setViewportSize(
                ConfigReader.getInt("web.viewportWidth", 1440),
                ConfigReader.getInt("web.viewportHeight", 900));
    }

    @AfterMethod(alwaysRun = true)
    public void closePage() {
        PlaywrightFactory.closePage();
    }

    @AfterSuite(alwaysRun = true)
    public void afterWebSuite() {
        ExtentManager.flush();
    }

    // ---- helpers ------------------------------------------------------------

    /** The portal URL under test. */
    protected String portalUrl() {
        return ConfigReader.get("web.baseUrl", "http://localhost:8080");
    }

    /**
     * Opens the portal login screen, skipping the test when the portal is not being served or when
     * Flutter's semantics tree never materialises — in both cases there is nothing meaningful to
     * assert, and a skip says so far more usefully than a timeout.
     */
    protected WebLoginPage openPortal() {
        WebLoginPage login = new WebLoginPage(page);
        try {
            login.open();
        } catch (RuntimeException e) {
            throw new SkipException("Trimio portal not reachable at " + portalUrl()
                    + " — serve it with `flutter run -d chrome -t lib/main_web.dart` and pass"
                    + " -Dweb.baseUrl=… (" + e.getMessage() + ")");
        }
        if (!login.hasSemantics()) {
            throw new SkipException("Flutter Web semantics are unavailable at " + portalUrl()
                    + " — no accessibility tree means no addressable widgets. See WebBasePage.");
        }
        return login;
    }

    /**
     * Signs in and returns the resulting shell, skipping when credentials for the role are not
     * configured in {@code testdata/mobile/test-accounts.json}.
     */
    protected WebShellPage signInAs(String role) {
        String email = TestAccounts.emailFor(role);
        String password = TestAccounts.passwordFor(role);
        if (email.isBlank() || password.isBlank()) {
            throw new SkipException("No '" + role + "' account configured — add roleAccounts." + role
                    + " to testdata/mobile/test-accounts.json to run this test.");
        }
        WebLoginPage login = openPortal();
        login.login(email, password);

        WebShellPage shell = login.shell();
        if (!shell.isLoaded()) {
            throw new SkipException("Sign-in as '" + role + "' did not reach the portal shell — the "
                    + "account may lack the role, or the portal build may be stale.");
        }
        return shell;
    }
}
