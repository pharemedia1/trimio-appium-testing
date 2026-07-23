# Trimio — Application Analysis & Test Strategy

**Scope:** client, professional, admin (mobile) · admin + vendor web portal · the Store (marketplace) across all roles.
**Sources analysed:** `~/StudioProjects/trimio/frontend` (251 Dart files) and `~/StudioProjects/trimio/backend` (~50 route modules, 38 controllers).
**Date:** 2026-07-22

---

## 1. Product surfaces

Trimio is an at-home grooming marketplace: clients book licensed professionals who travel to them, plus a
retail Store that sells take-home products and pro-only supplies through third-party vendors.

There are **two shipping surfaces**, not one:

| Surface | Entry point | Roles served | Rendering |
|---|---|---|---|
| **Mobile app** (Android/iOS, Flutter) | `lib/main.dart` | client, professional, admin, support, vendor | Native Flutter |
| **Web portal** | `lib/main_web.dart` → `lib/web/web_shell.dart` | **admin, support, vendor only** | Flutter Web (canvas) |

The web portal deliberately re-uses the mobile screens inside a desktop shell (sidebar + top bar) rather
than being a separate codebase. Clients and professionals are **blocked** from it.

### 1.1 Role → landing screen (from `screens/auth/loginPage.dart:238-305`)

| `user_type_id` | Role | Mobile landing | Web landing |
|---|---|---|---|
| 1 | client | `BottomnavigationBar` (5 tabs) | **blocked** |
| 2 | professional | `BarberBottomnavigationBar` (5 tabs) | **blocked** |
| 3 | admin | `AdminBottomNavigationBar` (4 tabs) | `WebShell(role:'admin')` |
| 4 | support | `SupportHomePage` | allowed (no dedicated shell yet) |
| 9 | vendor | `VendorHomePage` | `WebShell(role:'vendor')` |

### 1.2 Navigation maps

**Client bottom nav** (`NavigationBar/bottomNavigationBar.dart`) — `Home · Book · Appointments · Shop · Profile`.
The Shop tab carries a live cart-count badge fed by `BottomnavigationBar.setCartBadge`.

**Professional bottom nav** (`NavigationBar/professional_bottom_navigation_bar.dart`) —
`DashBoard · My Bookings · Client Hub · Store · Account`. On entry it fetches the user's profile and, when the
profile is empty or `approval_status == 'pending'`, pushes `ProfessionalNotCreatedHomePage` — an important
branch for test setup.

**Admin bottom nav** (`screens/Admin/NavigationBar/admin_bottom_navigation_bar.dart`) —
`Dashboard · Pros · Quality · Reports`, with the full management grid on the Dashboard tile grid
(All Users, Services, Quality Control, Reports, Price Overrides, Enforcements, Training, Store, Licenses, States).

**Web sidebar** (`web/web_shell.dart:_adminDests`) — grouped destinations:
`Overview → Dashboard` · `Manage → All Users, Services, Quality Control, Reports, Price Overrides, Enforcements, Training`
· `Marketplace → Store, Applications` · `Countries & States`.
Vendor sidebar (`_vendorDests`): `My Store → Overview, Catalog, Orders, Payouts`.

---

## 2. Feature inventory by role

### 2.1 Authentication (shared, `screens/auth/`, `screens/onBoarding/`)
Onboarding carousel (3 slides, `Skip`/`Next`/`Get started`/`Sign in`) → role page (`I'm a Client` /
`I'm a Professional`; `I'm an Admin` only in the reset flow) → registration / login.
Also: OTP verification (`otp_page.dart`), forgot password, reset password, forced password reset
(`force_password_reset_page.dart`), account verification (email + phone OTP), biometric "Faster sign-in"
opt-in, social sign-in, and a `Want to sell on Trimio? Apply to sell` entry point to the vendor application.

### 2.2 Client
| Area | Key screens |
|---|---|
| Booking (scheduled) | `booking_flow_screen.dart` — 4 steps: *What do you need? · Who & where · When · Add extras*, then Review & pay |
| Group booking | same flow with `isGroup` — *Who's coming · Services · Where · When* |
| Style-Me-Now (on-demand) | `style_me_now_flow_screen.dart` (2 steps: *Who & what · Where & pay*) + `style_me_now_matching_screen.dart` (live matching, "Your pro is on the way", cancel request) |
| Recurrence | `client_recurrence_appointment_page.dart`, `recurrence_appointment_page.dart` |
| Appointments | list/detail/update, cancel (single vs recurring series), rebook, countdown, post-service prompt, tipping |
| Membership | Choose plan · checkout (Stripe) · manage plan · upgrade · pause · cancel (with refund calc) · billing history |
| Store | `client_shop_screen.dart` (bundles + products) · cart · checkout · orders + order detail |
| Reviews | `client_review_flow.dart` / `review_flow_page.dart` — per-service ratings, sliders, public review (min 20 chars), private feedback, photos |
| Profile | account page, profile creation, family members, addresses, biometric toggle |
| Reports | create/list/view support reports |
| Other | scan-to-book (QR), map navigation, notifications, payment methods |

### 2.3 Professional
Dashboard (offers, accept/decline, earnings, "you earn" payout math, appointment-in-progress banner),
Bookings dashboard (search, cancel, report no-show — "Charges the §5.5 no-show fee"), Client Hub
(client list, per-client profile + notes), Store (pro-only catalog, cart, product reviews — "Only verified
purchasers can leave a review"), Schedule/availability, Performance & KPIs, Trends dashboard, Balance +
withdraw ("Withdraw all"), Bank details / Stripe payout onboarding, Profile forms (personal, sensitive data
with license number `FXXX-XXX-XX-XXX-X`, portfolio, about), State-board licenses (`Submit for review`),
Training tutorials, Google Calendar link, review flow for the client (incl. safety categories), share/referral sheet,
sanitation attestation checklist on appointment details.

### 2.4 Admin
All Users (clients/professionals, status pages: pending/approved/rejected/incomplete), professional detail
with document approve/reject and automated DMV/licence checks, License verification queue, Quality control
(warnings/suspensions, streak review, professional reviews), Reports/support tickets, Price overrides
(fixed price or multiplier, scoped by service/pro level with effective dates), Enforcements (suspend, extend,
reinstate, remove), Training materials CRUD, Countries & States (activation, law-watch alerts, sources),
Services catalog, Notifications centre, and the **Store admin**: vendors CRUD + logins/invites, vendor
applications (approve & invite / reject), products + variants + images, fulfillment (mark shipped with
carrier + tracking), payouts/transfers.

### 2.5 Vendor
Apply to sell (`vendor_apply_page.dart`, public), then the portal: Overview, Catalog (products, variants,
images, draft/active/archived), Orders (ship with carrier/tracking, order detail with payout after commission),
Payouts + Stripe onboarding (`Set up payouts` / `Continue payout setup` / `Refresh`), store profile edit,
notifications, CSV export.

### 2.6 Store data flow (backend)
`/store/client/*` (products, categories, bundles, cart, checkout, orders) ·
`/store/vendor/*` (overview, me, products, variants, images, orders, ship, payouts, stripe) ·
`/store/admin/*` (vendors, applications, products, variants, images, orders, vendor groups/payouts) ·
`/store/apply` (public application).

---

## 3. Critical rule: the web portal is staff-only

Enforced **twice**, and both halves need coverage:

1. **Client side** — `utils/web_portal_access.dart`: allowed roles `{admin, support, vendor}` /
   type-ids `{3,4,9}`. On denial it signs the Firebase session out, clears `userId/userType/userPhone/
   userEmail/vendor_id` from prefs, and shows an `AlertDialog` titled **"Use the Trimio app"** with
   role-specific copy.
2. **Server side** — `backend/services/webPortalAccess.js`, applied at `/auth/login`, self-registration,
   social registration, and OTP completion. A request is treated as a browser when it carries an `Origin`
   or `Sec-Fetch-Site` header. Refusal is **HTTP 403** with
   `error_code: WEB_PORTAL_NOT_AVAILABLE`. Toggles: `WEB_PORTAL_BLOCK_ENABLED`, `WEB_PORTAL_ROLE_IDS`.

Test implications: a client/pro login in the browser must be refused **and** must leave no usable session;
an admin/vendor login in the browser must succeed; the same client credentials must still work from the app.

---

## 4. Automation approach

### 4.1 Mobile — Appium + UiAutomator2 (existing framework)
Flutter exports its widget tree to the Android accessibility bridge, so `Text`, button labels and tooltips
surface as **content-desc**. Only two explicit `Semantics(label:…)` wrappers exist in the whole app
(`login_button`, `Status`), so page objects locate by:

- `accId("Create account")` — exact content-desc for buttons and standalone labels;
- `descContains("…")` — when a `Semantics` merges with a child (e.g. onboarding slide title + description,
  role cards) or when the label is dynamic (`Order #123`);
- `editText(n)` — text fields carry no accessibility id, so they are addressed by `EditText` index;
- `UiScrollable` scroll-into-view for content below the fold — **required** for the long client/pro
  dashboards, because Flutter drops the semantics of off-screen widgets;
- `hideKeyboard()` before tapping a control that the soft keyboard pushed off-screen.

### 4.2 Web — Playwright Java against Flutter Web
Flutter Web renders to canvas: there is no DOM for widgets until the **semantics tree** is built. The
framework therefore:

1. navigates to the portal, then
2. enables accessibility — either by clicking Flutter's hidden "Enable accessibility" placeholder or by
   dispatching the semantics-enable event — see `WebBasePage.enableSemantics()`;
3. locates elements as `flt-semantics[aria-label="…"]`, `flt-semantics >> text=…`, or the real
   `input`/`textarea` elements Flutter injects for text fields (`flt-text-editing`).

This is inherently more brittle than DOM-native testing, so the web layer keeps assertions at the level of
*"the destination rendered / the action produced its toast"* and pushes deep data assertions to the
API/DB helpers.

### 4.3 Layers
| Layer | Tool | Purpose |
|---|---|---|
| UI mobile | Appium + TestNG | user journeys per role |
| UI web | Playwright Java + TestNG | portal navigation, store admin/vendor, access control |
| Data setup/verify | `DbHelper` (JDBC → Postgres `trimio`) | OTP codes, account provisioning, order/state verification |
| Reporting | ExtentReports + Log4j2 + failure screenshots | one report for both surfaces |

---

## 5. Risk assessment — where the defects will be

| # | Risk | Why | Priority |
|---|---|---|---|
| R1 | Web portal access control | Two independent enforcement points; a regression silently exposes the portal to clients | **P1** |
| R2 | Money paths (booking price, membership, tips, store checkout, payouts) | Stripe + admin overrides + membership credits + peak/distance fees compound | **P1** |
| R3 | Booking availability & matching | Multi-step flow with server-side availability, group fitting and RSN offer scoring | **P1** |
| R4 | Store fulfillment/payout split | Vendor commission math and ship→payout release | **P1** |
| R5 | Professional approval gating | `approval_status == 'pending'` reroutes the whole pro app | **P2** |
| R6 | Licence/state compliance | Law-watch alerts and per-state rules gate bookability | **P2** |
| R7 | Recurring series & cancellation semantics | "Only this visit" vs "All future visits" | **P2** |
| R8 | Review flow validation | Many required sliders + 20-char minimum + photo upload | **P2** |
| R9 | Session/role persistence | `SharedPreferences` role drives every landing decision | **P2** |
| R10 | Flutter-web semantics availability | Selectors vanish if accessibility isn't enabled | **P3** (test-infra) |

---

## 6. Test-suite structure delivered

```
docs/Trimio-Test-Cases.xlsx        ← the full manual/automation test-case suite (multi-sheet)
docs/Trimio-Test-Analysis.md       ← this document
scripts/generate_test_cases.py     ← regenerates the workbook from source-controlled data
src/main/java/org/example/pages/mobile/{client,professional,admin,vendor}/…
src/main/java/org/example/pages/web/…
src/test/java/org/example/tests/mobile/{client,professional,admin}/…
src/test/java/org/example/tests/web/…
src/test/resources/suites/{mobile-testng,web-testng,full-regression}.xml
```

---

## 7. Known constraints & assumptions

1. **Selectors are derived from source, not from a running device** (per the agreed scope). They follow the
   patterns already verified for the auth suite, but the first execution against a device/browser should be
   treated as a selector-calibration run.
2. **The web portal needs CORS or `--disable-web-security`** in local dev (`main_web.dart` header comment).
   `web.baseUrl` defaults to `http://localhost:8080`; point it at whatever `flutter run -d chrome` prints.
3. **Positive paths that need real accounts self-skip** (`SkipException`) until credentials are supplied via
   `test-accounts.json` or `-D` flags — the same convention the auth suite already uses.
4. **The admin mobile console and the web console share screens**, so a fix in one usually fixes both; the
   suites are split anyway because the shells, navigation and viewport differ.
5. `screens/auth/Tesitng.dart`, `screens/reviews/revie_flow_test.dart` and the legacy
   `NavigationBar/admin_bottom_navigation_bar.dart` look like dead/scratch code — excluded from coverage.
