# Trimio Test Automation

Test automation for **Trimio** across both of its shipping surfaces:

| Surface | Roles | Tool |
|---|---|---|
| **Mobile app** (Flutter, Android) | client · professional · admin · vendor · support | Appium + UiAutomator2 |
| **Web portal** (Flutter Web — staff only) | admin · vendor | Playwright for Java |
| **Backend** (Node/Express, `:3000`) | — | JDK `HttpClient` (`utils/ApiClient`) |

> Despite the repository name, the app under test is a **Flutter mobile app**, not a DOM web app.
> Playwright is used only for the browser portal, which is itself Flutter Web and is driven through
> its accessibility (semantics) tree — see `base/WebBasePage` for why.

**Stack:** Java 17 · Appium java-client 9.4 · Playwright for Java 1.60 · TestNG 7.11 ·
ExtentReports 5 · Log4j2 · Maven

**Methodology:** Page Object Model + data-driven (external JSON) + listener-driven reporting.

---

## Documentation

| Document | What it is |
|---|---|
| [`docs/Trimio-Test-Analysis.md`](docs/Trimio-Test-Analysis.md) | Application analysis: surfaces, roles, feature inventory, risk assessment, automation strategy |
| [`docs/Trimio-Test-Cases.xlsx`](docs/Trimio-Test-Cases.xlsx) | The master test-case suite — 366 cases across 7 module sheets, with priority, type and the automated test that covers each |
| `scripts/testcases/*.py` | The **source** of the workbook. Edit these, never the .xlsx |
| `scripts/generate_test_cases.py` | Regenerates the workbook: `python3 scripts/generate_test_cases.py` (needs `openpyxl`) |

---

## Project layout

```
src/main/java/org/example/
  base/MobileBasePage         Appium actions + Flutter-on-Android selector helpers (a11y id,
                              EditText index, descContains, UiScrollable scroll-into-view)
  base/WebBasePage            Playwright against Flutter Web: enables the semantics tree and
                              locates by aria-label
  factory/AppiumDriverFactory embedded Appium server + AndroidDriver lifecycle
  factory/PlaywrightFactory   ThreadLocal browser/context/page lifecycle
  config/ConfigReader         config.properties + -D / env overrides
  utils/ApiClient             backend calls where a rule is enforced server-side
  utils/DbHelper              JDBC into Postgres `trimio` (OTP codes, verification)

  pages/mobile/               auth screens (onboarding, login, registration, OTP, reset)
  pages/mobile/common/        BottomNavBar — the shell shared by all three signed-in roles
  pages/mobile/client/        home · booking · style-me-now · appointments · shop · cart ·
                              orders · membership · profile · review
  pages/mobile/professional/  dashboard · bookings · client hub · store · earnings
  pages/mobile/admin/         console · users · quality · pricing · enforcement · training ·
                              store · queues (reports/services/states)
  pages/web/                  login · shell · admin dashboard · admin store · vendor portal ·
                              vendor application

src/test/java/org/example/
  base/MobileBaseTest         Appium TestNG lifecycle (server, driver per test)
  base/RoleSessionTest        signs in as a role and returns that role's landing screen
  base/RegisteredAccountTest  registers a real account once per class (OTP/reset flows)
  base/WebBaseTest            Playwright page per test + portal sign-in helpers
  tests/mobile/               auth suites (onboarding, registration, login, forgot, OTP, reset)
  tests/mobile/{client,professional,admin}/   signed-in journeys
  tests/web/                  portal access control, admin portal, admin store, vendor portal
  listeners/                  ExtentReports, screenshot-on-fail, retry

src/test/resources/
  testdata/mobile/*.json      scenarios + accounts (no credentials in code)
  suites/                     see below
```

### Suites

| Suite | Covers | Needs |
|---|---|---|
| `suites/mobile-testng.xml` | auth: onboarding, registration, login, forgot/OTP/reset | emulator + backend |
| `suites/mobile-regression-testng.xml` | signed-in client / professional / admin journeys | + seeded role accounts |
| `suites/web-testng.xml` | web portal: access control, admin shell, marketplace, vendor | + the portal served |
| `suites/api-testng.xml` | API contract: membership, object-level authorization (IDOR), social sign-up | backend + Firebase web key |
| `suites/security-testng.xml` | authentication, the role matrix, the super-admin seat, injection, enumeration, brute force, CORS, the staff-only portal | backend + Firebase web key |
| `suites/paired-testng.xml` | on-demand dispatch → offer → acceptance | **two** emulators |
| `suites/booking-payment.xml` | the individual booking money path | emulator + a client with a card |
| `suites/full-regression.xml` | everything that does not write or trip the rate limiter | all of the above |

The last four layers need **no device at all** (`api`, `security`, and the performance classes) or
**two** (`paired`). See `docs/Trimio-Requirements-And-Coverage.md` for what each one proves and
what it deliberately leaves out.

---

## Setup (one time)

```bash
mvn clean test-compile

# Playwright browser binaries (only needed for the web suite)
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
              -Dexec.args="install --with-deps chromium" \
              -Dexec.classpathScope=test
```

Appium is started automatically by `AppiumDriverFactory` (it looks for
`~/.npm-global/lib/node_modules/appium/index.js`; override with `-Dappium.jsPath`).

### Environment

```bash
# 1. Emulator (Apple Silicon: the AVD is arm64 — build the APK with --target-platform android-arm64)
emulator -avd Pixel_7_API_35

# 2. Backend + database
cd ~/StudioProjects/trimio/backend && npm start        # :3000, Postgres `trimio` on :5432

# 3. Web portal (only for the web suite)
cd ~/StudioProjects/trimio/frontend
flutter run -d chrome -t lib/main_web.dart              # note the port it prints
```

### Test accounts

Fill `roleAccounts` in `src/test/resources/testdata/mobile/test-accounts.json` — one seeded
account per role (`user_type_id` 1 client, 2 professional, 3 admin, 4 support, 9 vendor).
**Anything without credentials skips with an explanatory message rather than failing**, so a run
against a partially-provisioned environment still reports honestly.

The `professional` account needs `approval_status = 'approved'`; otherwise the app reroutes to
`ProfessionalNotCreatedHomePage` and the dashboard is unreachable.

---

## Run

```bash
# Auth suite (no seeded accounts needed)
mvn test -DsuiteXmlFile=src/test/resources/suites/mobile-testng.xml -Ddb.password=… -DretryCount=0

# Signed-in mobile journeys
mvn test -DsuiteXmlFile=src/test/resources/suites/mobile-regression-testng.xml -DretryCount=0

# Web portal
mvn test -DsuiteXmlFile=src/test/resources/suites/web-testng.xml \
         -Dweb.baseUrl=http://localhost:<port> -Dheadless=false -DretryCount=0

# Everything that does not write or trip the rate limiter
mvn test -DsuiteXmlFile=src/test/resources/suites/full-regression.xml \
         -Ddb.password=… -Dweb.baseUrl=http://localhost:<port> -DretryCount=0

# Security — no device needed, ~540 real HTTP calls in about 12 seconds
mvn test -DsuiteXmlFile=src/test/resources/suites/security-testng.xml \
         -Dfirebase.webApiKey=<web key from firebase_options.dart> -Ddb.password=… -DretryCount=0

# Performance — run on an otherwise idle machine; an emulator in the background invalidates it
mvn test -Dtest=ApiLatencyTest,ApiConcurrencyTest -DfailIfNoTests=false \
         -Dfirebase.webApiKey=… -Dperf.concurrency=10 -Dperf.requestsPerWorker=10

# On-demand across TWO emulators. WRITES: accepting an offer creates a real appointment.
mvn test -DsuiteXmlFile=src/test/resources/suites/paired-testng.xml \
         -Ddevices.client=emulator-5554 -Ddevices.professional=emulator-5556 -DretryCount=0

# Where the coverage gaps are (reads the harvested route inventory, asserts nothing)
python3 scripts/coverage_matrix.py --gaps
```

### Two ways these suites break each other

Both were found by doing them, and neither is obvious from a failing test:

- **The security suite trips a rate limiter the mobile suites depend on.** `/auth/*` and
  `/password/*` share a per-source limiter (429 after a handful of attempts) plus a per-account
  lockout (423, **one hour**). A brute-force probe locked the seeded client and made the mobile
  password-reset tests fail for a reason that had nothing to do with them. **Do not run
  `security-testng.xml` concurrently with the mobile suites against the same backend.**
- **A `DEV_AUTOLOGIN` build makes the whole auth suite unrunnable.** A debug APK carrying
  `--dart-define=DEV_AUTOLOGIN_EMAIL/PASSWORD` signs itself in during splash and never renders
  onboarding, and `adb shell pm clear` does not help because the credentials are compiled in.
  Every onboarding, registration and login test then fails against a screen that never existed.
  Rebuild without the defines (`flutter build apk --debug --target-platform android-arm64`) to
  test auth.

Any key in `config.properties` is overridable with `-Dkey=value` or an `UPPER_SNAKE` env var —
`web.baseUrl`, `api.baseUrl`, `browser`, `headless`, `retryCount`, `appium.deviceName`, …

`-DretryCount=0` is recommended for verification runs: the default retry hides real failures
behind a re-run.

---

## How the selectors work

**Mobile.** Flutter exports its widget tree to the Android accessibility bridge, so `Text`,
button labels and tooltips arrive as **content-desc**. The whole app contains only two explicit
`Semantics(label:)` wrappers (`login_button`, `Status`), so page objects locate by:

- `accId("Create account")` — exact content-desc;
- `descContains(…)` — when a `Semantics` merges with its child, or the label is dynamic
  (`Order #123`);
- `editText(n)` — text fields carry no accessibility id;
- `scrollToDesc(…)` — **required** below the fold: Flutter drops the semantics of off-screen
  widgets, so an unscrolled control genuinely does not exist in the tree;
- `hideKeyboard()` before tapping a control the soft keyboard pushed off-screen.

**Web.** Flutter Web paints to a canvas — there is no DOM for widgets until the semantics tree is
enabled. `WebBasePage.enableSemantics()` switches it on (clicking Flutter's hidden
"Enable accessibility" placeholder, with a JS fallback), after which elements are
`flt-semantics[aria-label="…"]`. Text fields are the exception: Flutter injects real `<input>`
elements for the focused field. If the tree is unavailable the whole web suite **skips** rather
than timing out.

---

## What is deliberately not automated

Some tests are written but marked `enabled = false`, and some cases in the workbook are marked
Manual. The line is drawn at side effects that outlive the test run:

- **money movement** — booking/membership/store payment, tips, `Withdraw all`, releasing a vendor
  payout by marking an order shipped;
- **identity issuance** — approving a vendor application creates a store and emails credentials to
  a third party;
- **irreversible state for real people** — suspending an account, approving/rejecting a licence,
  cancelling a professional's paid work, publishing a public review.

Where a flow is guarded, the automation drives it up to the guard and asserts the guard is there —
then stops. Enable the disabled tests only against a disposable environment.

---

## Outputs

- **Report:** `reports/Trimio-Automation-Report.html` (failure screenshots embedded)
- **Logs:** `logs/automation.log`

---

## Known constraints

1. **Selectors are derived from the Flutter source, not from a running device.** They follow the
   patterns already verified for the auth suite; treat the first execution of the new suites as a
   selector-calibration run.
2. The web portal needs CORS (or Chrome launched with web security disabled) to reach the API in
   local dev — see the header comment in `lib/main_web.dart`.
3. Charts render on canvas and expose no semantics, so their **values** are not assertable from the
   UI; revenue/KPI reconciliation stays an API- or DB-level check.
