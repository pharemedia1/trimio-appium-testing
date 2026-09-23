# Trimio — requirements, test architecture and honest coverage

**Date:** 2026-09-21 · **Supersedes the coverage claims in** `Trimio-Test-Analysis.md` (2026-07-22),
which predates the super-admin seat, the read-before-agree registration gate, the shop-visit model
and roughly a hundred routes.

This document exists to answer three questions that are usually answered by assertion rather than
evidence:

1. **What does Trimio actually do?** — harvested from the source, not from memory.
2. **What is automated?** — with the difference between *asserted* and *merely reached* kept visible.
3. **What is not?** — named, counted, and attributed to a reason.

---

## 1. Where the requirements come from

Trimio has no single requirements document, and the ones in `~/StudioProjects/trimio/docs` (44 of
them, from `FIFTY_STATE_MODEL.md` to `SHOP_VISIT_BOOKING_MODEL_V2.md`) describe decisions rather
than behaviour. The authoritative statement of what the product does is therefore the code, and of
that, two artefacts are load-bearing:

| Source | What it settles |
|---|---|
| `backend/server.js` + `backend/routes/*.js` | the complete HTTP contract — 440 endpoints across 57 router files, each with the middleware chain that decides who may call it |
| `frontend/lib/screens/**` (319 Dart files) | what a user can reach, per role, and the copy the tests assert on |

The route surface is harvested mechanically rather than read, because a hand-maintained list drifts
within a release:

```bash
python3 scripts/harvest_api.py src/test/resources/testdata/api/endpoints.json
```

That file is **data the test suite iterates**, not documentation about it — see
`EndpointInventory`. A route added to the backend becomes covered by the security sweeps the moment
the inventory is regenerated, with no test edit.

### 1.1 The surface, by who may call it

| Guard | Middleware | Routes | Meaning |
|---|---|---:|---|
| PUBLIC | none | 54 | reachable by anyone who can reach the host |
| AUTH | `firebaseAuth` | 237 | any signed-in user |
| PRO | `requireProAuth` | 15 | professionals only |
| VENDOR | `requireVendor` | 21 | store vendors only |
| STAFF | `requireStaff` | 7 | admin, super admin **and** support (type ids 3,4,5-8) |
| ADMIN | `adminAuth` | 96 | `user_type_id` 3 or 4 only |
| SUPER_ADMIN | `+ requireSuperAdmin` | 10 | the single transferable seat |

Authentication is genuinely enforced: `AUTH_ENFORCED=true` and `ADMIN_AUTH_ENFORCED=true`, so every
guarded route resolves its caller from a verified Firebase ID token. **There is no test bypass.**
This is why the API suites mint real tokens through Firebase's own REST endpoint
(`FirebaseTokens`) — a suite about authorization has to hold real credentials, and switching the
enforcement off would test a configuration nobody ships.

### 1.2 The five privilege tiers, and why support matters

The tiers are not decoration; each is enforced by different middleware and each has been got wrong
at least once:

```
anonymous  <  client / professional / vendor  <  support  <  admin  <  super admin
```

**`user_type_id` 4 used to mean support and now means super admin.** Any account that held it at
the moment of that change was silently promoted. A test matrix that only ever compares "client vs
admin" cannot see that class of mistake, which is why `roleAccounts.support` is now seeded
(`trimiotest+support1@gmail.com`, type 5) and `RoleAuthorizationMatrixTest.supportIsStaffButNotAdmin`
asserts both halves: support **is** admitted to `/refund/*`, and support **is not** admitted to
`/admin/*`.

The same trap caught the admin seat itself: `roleAccounts.admin` used to point at the super admin,
so the entire admin suite ran as the seat while believing otherwise. `SuperAdminBoundaryTest` skips
loudly if the two accounts are ever the same again.

### 1.3 The two surfaces, and the staff-only rule

Trimio ships a **mobile app** (all roles) and a **Flutter-Web staff portal** (`main_web.dart`,
admin/support/vendor only). Clients and professionals are blocked from the browser **twice** —
client-side in `utils/web_portal_access.dart`, server-side in `services/webPortalAccess.js`, which
decides a request is "from a browser" purely from its `Origin` / `Sec-Fetch-Site` headers and
refuses with `403 WEB_PORTAL_NOT_AVAILABLE`.

Both halves need coverage, and the second half is only testable at the protocol level: from a real
browser you cannot omit `Origin`, and from the app you cannot add it. `TransportAndHeadersTest`
asserts the pair — a browser-shaped client login is refused, **and** the same credentials from a
native-shaped request still work. The second assertion is the important one: a block that catches
the mobile app locks every client out of Trimio, which is worse than the leak it prevents.

---

## 2. Test architecture

Despite the repository being named "Trimio Playwright Automation", the mobile UI is driven with
**Appium + UiAutomator2**, because the app is Flutter and renders to a canvas that no DOM-based
tool can see. Flutter's widget tree reaches Android's accessibility bridge, so `Text`, button
labels and tooltips surface as `content-desc`.

| Layer | Tool | Needs | Classes | `@Test` |
|---|---|---|---:|---:|
| Mobile — auth | Appium/TestNG | emulator, backend | 6 | 37 |
| Mobile — client | Appium/TestNG | + seeded client | 11 | 40 |
| Mobile — professional | Appium/TestNG | + approved pro | 7 | 18 |
| Mobile — admin | Appium/TestNG | + admin account | 5 | 22 |
| Web portal | Playwright Java | served Flutter Web | 5 | 43 |
| **Paired devices** | Appium ×2 | **two emulators** | 1 | 3 |
| API | JDK HttpClient | backend + Firebase key | 3 | 24 |
| **Security** | JDK HttpClient | backend + Firebase key | 7 | 29 |
| **Performance** | JDK HttpClient | backend + Firebase key | 2 | 5 |

The last four layers are new as of 2026-09-21. The API, security and performance suites need no
device at all and run in seconds, so a machine with no Android SDK can still run the whole
authorization sweep.

### 2.1 Suites

| Suite | What it runs | Writes? |
|---|---|---|
| `mobile-testng.xml` | auth flows (fresh install only) | creates accounts |
| `mobile-regression-testng.xml` | signed-in journeys, all roles | no |
| `web-testng.xml` | the staff portal | no |
| `security-testng.xml` | authz, injection, abuse | trips the rate limiter |
| `paired-testng.xml` | on-demand across two emulators | **yes — real appointments** |
| `booking-payment.xml` | the individual booking money path | **yes — real charge** |
| `full-regression.xml` | everything non-writing | — |

### 2.2 The blocker: 10 sign-ins per 15 minutes vs. one sign-in per test

**This is the single thing stopping the mobile suites from running to completion, and it is a
collision between two reasonable designs rather than a bug in either.**

`backend/middleware/authRateLimit.js` caps the credential endpoints — login, OTP mint and verify —
at `AUTH_CREDENTIAL_RATE_MAX` (**default 10**) per `AUTH_CREDENTIAL_RATE_WINDOW_MS` (**default 15
minutes**), per source. That is a good production setting: it is generous for a person who
mistypes a password, and a script hits it in seconds.

This framework creates a **fresh Appium session and signs in again for every single test** — which
is also right, because it is what guarantees no test inherits another's state. There are roughly
120 signed-in tests. At ten sign-ins per quarter hour, the arithmetic does not work.

What it looks like when it happens is the expensive part: the eleventh test onwards is refused
with *"Too many attempts. Please wait a few minutes and try again."*, which the app renders as an
ordinary error snackbar — **visually identical to a wrong password**. One full-regression run
skipped 23 client and professional journeys, every one of them reporting a perfectly healthy
account as possibly suspended. The same credentials answered `200` from curl throughout.

Two halves of the fix are done:

- `LoginScreen.awaitLoginOutcome()` now polls for "left the form" and the rate-limit snackbar
  *together* and reports which happened. It has to be one call: the old shape asked
  `isLoginAccepted()` first, which waits 20 seconds, and the snackbar lives about four — so by the
  time anything looked, the only evidence had been gone for a quarter of a minute.
- `RoleSessionTest.loginAs` and `PairedDeviceTest.signIn` treat a rate-limit refusal as a pause
  rather than a verdict: they wait out a window once (`-Dauth.rateLimitCooldownSeconds`, default
  90) and retry, and if it still fails they say *that*, naming the env var.

The third half is a **decision, not code**, and there are three ways to take it:

| Option | Cost | Notes |
|---|---|---|
| Raise `AUTH_CREDENTIAL_RATE_MAX` on the **test** backend | one env line + a restart | What the variable exists for. The limiter stays on and is still asserted by `RateLimitAndLockoutTest`; the per-account lockout is untouched. Must never ship. |
| Accept the cooldowns | ~3 hours per full run | Mostly spent sleeping. |
| Sign in **once per class** instead of per test | a real refactor of `MobileBaseTest` | Cuts ~120 sign-ins to ~25 and is the right long-term answer. Costs the per-test isolation that the current design buys. |

Until one is taken, run the signed-in suites in batches of ten or fewer.

### 2.3 Two things the suites must not do to each other

Both were learned by doing them:

- **The security suite trips a shared rate limiter.** A brute-force probe against
  `trimiotest+client1` locked the account for an hour and would have skipped every client journey
  in the regression. `RateLimitAndLockoutTest` now aims at an address with no account and clears
  the row in `@AfterClass`; the suite XML runs the abuse tests last. **Do not run the security
  suite concurrently with the mobile suites against the same backend.**
- **A `DEV_AUTOLOGIN` build makes the auth suite unrunnable.** A debug APK carrying
  `--dart-define=DEV_AUTOLOGIN_EMAIL/PASSWORD` signs itself in during splash and never shows
  onboarding, and `pm clear` does not help because the credentials are compiled in. Every
  onboarding, registration and login test then fails against a screen that is never rendered — and
  reads exactly like thirty-five stale tests. Rebuild without the defines to test auth.

---

## 3. Coverage — measured, not claimed

`python3 scripts/coverage_matrix.py` reads the route inventory and every path literal in the Java
sources. It deliberately reports three states rather than a single percentage:

```
Trimio API coverage — 440 mounted endpoints

  asserted by name          46  (10%)   a test names this exact path
  swept for authorization   71  (16%)   reached only by the exhaustive security sweeps
  untouched                323  (73%)
  reached at all           117  (26%)
```

**"Swept" is a much weaker claim than "asserted"** and the two are kept apart on purpose. A swept
route has been proved to refuse the wrong caller; nothing has been said about whether it returns
the right answer to the right one. Collapsing the two is how a coverage number becomes a lie.

### 3.1 Where the gap is

| Method | Reached | Untouched |
|---|---:|---:|
| GET | 107 | 87 |
| POST | 10 | **171** |
| PUT | 0 | **24** |
| PATCH | 0 | **19** |
| DELETE | 0 | **22** |

**The writes are the gap**, and for a structural reason: the automated sweeps only issue GETs,
because a sweep that POSTed would change the shared database the functional suites assert against,
and a `:param` path needs an id a sweep cannot invent. So the 236 write endpoints are covered only
where a UI journey drives them through the app — which the script cannot see, since those tests name
screens, not paths. **The real figure for writes is therefore higher than 4% and lower than
comfortable, and the honest statement is that it is not measured.**

Untouched routes cluster in the modules with the most surface and the least UI coverage:

| Router | Untouched |
|---|---:|
| `professionalProfileRoutes.js` | 43 |
| `adminRoutes.js` | 35 |
| `appointmentRoutes.js` | 21 |
| `storeAdminRoutes.js` | 16 |
| `storeVendorRoutes.js` | 15 |
| `venueRoutes.js` | 14 |

### 3.2 What the security layer does cover, exhaustively

| Test | Scope |
|---|---|
| SEC-001/002 | all 104 sweepable guarded routes × {no credential, forged token} |
| SEC-003 | all 54 public routes reachable without a 5xx |
| SEC-010 | 43 routes × 6 roles — every tier against every route below it |
| SEC-011 | support admitted to staff routes, refused by admin routes |
| SEC-020-024 | all 10 super-admin routes, both directions |
| SEC-030-036 | SQL payloads in query and path, traversal, type confusion, oversized bodies, error disclosure |
| SEC-050-052 | enumeration via reset, registration availability and `checkUserExists` |
| SEC-060-063 | brute force, per-account lockout, rate limiting beyond login |
| SEC-070-073 | hardening headers, CORS, the staff-only portal rule in both directions |
| API-020-022 | IDOR: 10 routes probed with another user's id, plus cross-role |

One run makes ~540 real HTTP calls and takes 12 seconds.

---

## 4. Findings from this round of testing

Recorded here because they are the output of the work, not incidental to it. Both **fail a test
deliberately** rather than being written down and forgotten.

### 4.1 `GET /memberships/invoices` returns 500 for a membership that was never billed

`services/membership.service.js`:

```js
const sub = await getActiveSubscription(userId);
const inv = await stripe.invoices.list({ customer: sub.stripe_customer_id, limit: 12 });
```

`stripe_customer_id` is nullable and is null in practice — seeded user 41504 holds an **active**
`membership_subscriptions` row with no Stripe customer, the shape a comped or manually-granted
membership takes, since nothing charged the member. The call reaches Stripe with `customer: null`
and the route answers `500 INTERNAL_ERROR`. **A member in that state cannot open their billing
history at all.** The correct answer is an empty list.

Note which caller finds it: a client with *no* subscription gets a clean 404, so the obvious test
passes. It takes a caller who has a subscription without a Stripe customer to reach the broken line.

> Covered by `MembershipApiTest.invoiceListingDoesNotFailForAMemberWithoutStripe` — **currently red.**

### 4.2 No request body limit — a 1 MB body answers 500, not 413

An unauthenticated `POST /auth/login` carrying a 1 MB email address produces
`500 INTERNAL_ERROR`. Nothing leaks (the body is a fixed string with no stack trace), but without a
body limit an unauthenticated caller decides how much memory the process allocates. A related gap:
a login body whose fields are objects rather than strings also 500s instead of 400ing.

> Covered by `InjectionAndInputHandlingTest.oversizedBodyIsRefused` — **currently red** — and
> `.wronglyTypedFieldsAreRejected`.

### 4.3 `POST /auth/checkUserExists` is an unauthenticated account oracle

Public, and for any address it is given it reports whether an account exists, **plus its `userId`
and `userTypeId`** — which is to say, which addresses belong to professionals and to admins.

The contrast is what makes this a finding rather than a design choice: `passwordController.sendOtp`
was *deliberately* hardened against exactly this, returning an identical
`200 {"message":"OTP sent successfully."}` for unknown and registered addresses. Two endpoints on
the same surface answer the same question with opposite policies.

Rate limiting bounds how fast the list can be enumerated (429 engages within a handful of requests)
but not whether it can be.

> Covered by `AccountEnumerationTest.checkUserExistsDoesNotDiscloseAccounts`, written to pass under
> either fix — refuse unauthenticated callers, or stop distinguishing the two cases.

### 4.4 CORS reflects an arbitrary `Origin`

The preflight echoes whatever origin it is sent. It is **not** currently exploitable, because
`Access-Control-Allow-Credentials` is absent and Trimio authenticates with a bearer token an
attacker's page cannot read — so a hostile page gets the unauthenticated view and nothing more.
It becomes serious the moment any endpoint accepts cookie auth.

> `TransportAndHeadersTest.corsDoesNotGrantCredentialedCrossOriginAccess` tolerates the reflection
> and fails on the combination.

### 4.5 `/admin/all-professionals` returns 213 KB unpaginated

Fine on seeded data, an outage on production data — and the same code path either way, so no
functional test can tell them apart.

> `ApiLatencyTest.listEndpointsReturnBoundedPayloads` measures it.

### 4.6 Two unlabelled text fields in Style-Me-Now (accessibility)

Both text fields in the on-demand flow are bare `TextField`s carrying only a `hintText`. A Flutter
hint does **not** become a `content-desc`: the node arrives as `android.widget.EditText` with
`NAF="true"` (Not Accessibility Friendly) and an empty content-desc.

- the service search on step 1 (`hintText: 'Search services…'`)
- the manual street-address field on step 2

This is a real accessibility defect — a screen reader cannot announce either field — and it is what
made the flow untestable by name. The page object now locates the search by `EditText` index, which
works but is positional and will break the day a second field is added. **The fix is upstream**: a
`Semantics(label:)` in `style_me_now_flow_screen.dart`.

Found by driving the flow rather than by reading it. Two related things were only visible the same
way, and both had made the existing test skip itself rather than fail:

- **Style-Me-Now has its own Home CTA**, `Style Me Now`. The test reached it through the deprecated
  `startBooking()`, which taps a bare "Book" — the *scheduled* flow. The two-step assertion then
  failed and the class skipped with "the flow was not reached", which reads as a missing
  professional and is not.
- **Step 2 has no address field when GPS supplied a location.** WHERE renders as
  `Current location / Using your GPS location` with a "Change" affordance and no input at all, so a
  caller that insisted on typing waited out a 30s timeout against a screen that was complete.
  `enterAddress` is now a no-op on that branch.

### 4.7 What the journey triage found in the framework itself

Not app defects — these are the reasons the signed-in suites were reporting a healthy app as
broken. Each one failed *late*, as "the screen did not render", which is why they were expensive
to find and worth writing down.

| # | Cause | Effect |
|---|---|---|
| 1 | **The Home feed animates forever.** A marquee banner scrolls continuously, so UiAutomator never sees an idle window and waits its full 10s before *every* command. Confirmed from adb: `uiautomator dump` answers `ERROR: could not get idle state.` | Client journeys took 5-6 minutes each and locators timed out on controls plainly on screen. Fixed with `waitForIdleTimeout=100` as a session setting — the same test then ran in about 2 minutes. |
| 2 | **`tab(label)` was an unscoped `descContains`.** The nav labels are short common words, and on Home "Book" also appears in "Book trusted professionals", "Book Individual", "Plan Group Booking", "Book again" and "Book a service". | `nav().open(CLIENT_BOOK)` never opened the Book tab — it tapped the greeting or a booking CTA. Now matches the description exactly, accepting both the plain and the doubled form the selected tab exports. |
| 3 | **Flutter merges a card's children into one node**, so a card *contains* its own button's label. The Individual Appointment card exports `"Individual Appointment\nBook a one-on-one session…\nHair cut\nBeard trim\nBook Individual"`. | `scrollAndTap("Book Individual")` tapped the card's centre. Because the card is itself clickable the tap "succeeded" and nothing moved. Added `scrollAndTapExact`, used for all three Home CTAs. |
| 4 | **19 locator anchors contained non-ASCII characters** — ellipses, em-dashes, a section sign, a curly apostrophe — across 13 page objects. `UiSelector` cannot match those and does not say so. | Every one was a locator that could never match. All replaced with ASCII-safe substrings, and `LocatorHygieneTest` (FW-001) now fails the build if another is introduced. |
| 5 | **Hints are not content-descs.** Three search fields were located by their `hintText`; a Flutter hint arrives as a bare `EditText` with an *empty* content-desc and `NAF="true"`. | The on-demand flow, the booking flow and the Book tab all had unreachable search boxes. Now located by `EditText` index. This is also a **real accessibility defect** — a screen reader cannot announce those fields either. The fix belongs upstream, in a `Semantics(label:)`. |

### 4.8 Two new mandatory steps in the core booking flow, previously uncovered

Found by walking the flow on-device after it kept skipping. Between "Book Individual" and the
four-step flow there are now **two** dialogs, neither of which the framework knew about:

1. **"Where should your appointment be?"** — the shop-visit model. *Come to me* (a professional
   travels) or *I'll go to a shop* (a chair at a licensed shop). The source calls it "deliberately
   the very first question" because it decides what the client is buying. Now answered by
   `ClientHomeScreen.chooseVenueIfAsked(boolean)`, with the venue exposed as a parameter rather
   than hidden as a default — the two are different products at different prices.
2. **The rebook prompt, rewritten.** Was "Book your professional?" with a "New professional"
   option; it is now "Book &lt;Name&gt; again?" offering "Book &lt;Name&gt;" / "Choose someone
   new". Detected by the caption "Your last professional", which is constant, rather than by the
   title, which carries the fixture's name.

With both answered, `bookingFlowShowsFourSteps` and `step1RequiresAService` pass.

### 4.9 Journey-suite triage: 31 failures to 1

The signed-in suites went from **31 failures / 32 skips** to **1 failure / 23 skips** across 70
tests. Almost none of it was the app being broken; it was the harness describing an app that had
moved. Every fix below was verified on-device before it was made.

| What was wrong | Tests freed |
|---|---|
| `tab(label)` was an unscoped `descContains`, so `nav().open("Book")` tapped the greeting. Now matches a newline-separated **segment** exactly, which also tolerates the selected tab's doubled label and the Shop tab's cart badge (`"4\nShop"`) | 11 store tests plus all tab navigation |
| Booking step 1 needs a **service**, step 2 a **recipient**, step 3 a **slot AND a professional** — `advanceToStep` only tapped Continue | 4 |
| The **venue sheet** ("Where should your appointment be?") and a **reworded rebook prompt** now stand between Home and the flow | the whole booking suite |
| Group step 1's minimum **moved to step 2** (day rates are priced by headcount); `addPerson()` left its chooser sheet open over Continue | 3 |
| Appointment cards no longer show `"Appointment #<id>"`; `openFirst()` opened the always-empty **Today**; cancellation is on the **list**, not the detail, and now goes through a **"Choose an action"** dialog first | 3 |
| `countIn` read only the first matching node, so the "Today" calendar button masked the summary card and a client with 28 visits reported none | 3 |
| Admin: `Price Overrides` was **removed from the product** (prong A of the ABC test); `Refunds` replaced it; the approval segments live one hop past All Users; Quality's landmark was a `hintText`; Training's submit reads "Create", not "Save" | 8 |
| Membership: the chooser says **"Choose Your Membership"**, not "Choose your plan"; no plan advertises "in-home cuts/month" | 3 |
| 19 non-ASCII locator anchors, three hint-based locators, and `waitForIdleTimeout` | suite-wide |

**The 23 remaining skips are honest** — and that claim turned out to be wrong. It is left here
because being wrong about it cost a day, and the correction is §4.11. Only four of the twenty-two
that survived into the mobile regression were environment data gaps. The rest were the harness
looking in the wrong place and blaming the environment for what it could not see.

**There are no remaining failures.** The one defect the triage exposed — §4.10 — has been fixed in
the app.

### 4.11 The skips were not data gaps — 2026-09-23

Every one of the 22 skips in the mobile regression was worked through. The distribution is the
finding:

| Resolution | Count |
|---|---|
| Stale locator, or the control is one tap further in | 16 |
| A genuine fixture gap, seeded | 4 |
| Needed a DEDICATED account, because the assertion is about an ABSENCE | 1 |
| Left skipping deliberately, covered elsewhere | 1 |

**Why the skip messages misled.** Each guard reads `if (!somePageObjectCheck()) throw new
SkipException("...no X in this environment...")`. A stale anchor makes the check false, and the
message then blames the environment — the single most misleading thing it could say, because it
sends the reader to seed data that already exists. The database had 89 appointments for a
professional whose screen "had no bookings", 43 pending professionals in a queue that called
itself empty, and an enforcement register reporting nothing while the console beside it read
"Enforcements | 3 active".

**Two structural causes, over and over.** The control is one tap further in — `Actions` on the
professional's page not the Quality list, Extend/Reinstate on Enforcement Detail not the register,
"Manage plan" past the profile's "Manage", the balance behind the account tab's "Earnings" row,
the review flow behind a past visit's "Rate your visit". Or the copy differs from the Dart source
— "credit/available" not "credits left", "Reason is required" (a SnackBar, after the dialog
closes) not "Reason (required)" (a hintText, which never reaches the a11y tree at all).

**Three failures that were not locators, and are worth knowing:**

1. **A fixture broke unrelated tests.** Seeding the client a membership made booking step 1 render
   a credit banner naming a service — "Your credit covers up to $60 of Men's Haircut." An
   over-broad `descContains` matched it, so the category FILTER looked broken and `selectService`
   tapped the banner instead of a service row. Five tests, none of them about memberships.

2. **The day walks never walked.** `offeredDays()` returned empty on a strip showing six days
   (`DAY_CHIP` expected `"Sun\n27"`; the chip is `"Sunday, September 27\nSun\n27"`), and both
   walks size their loop off it — so `openFirstDayWithTimes`, the helper that exists so tests are
   not pinned to one day, had only ever examined the default day.

3. **"The first appointment" is not the soonest.** `openFirst()` opens whichever section
   `hasAnyAppointment()` remembered, and that tries Future, Past, Today in that order.

**Fixtures are `scripts/seed_test_fixtures.sql`** — idempotent, test-DB only, each section naming
the test it unblocks and why. Seed a WINDOW, not a row: a fixture pinned to one appointment worked
in the morning and failed by afternoon once that appointment was in the past.

**`ScreenDumpTest`** (`tests/diag/`) prints a screen's real content-descs and never asserts. Every
screen it was pointed at was settled in one run. Guessing a locator from `.dart` source cost a
full run every time it was tried.

### 4.10 Training material validation was invisible to the admin — FIXED

`admin_training_materials_page.dart` validates correctly: an empty title or file URL returns early
with a SnackBar reading exactly "Title and File URL are required". **The admin never sees it.** The
dialog is opened with `showDialog(context: context, …)` and the handler calls
`ScaffoldMessenger.of(context)` — the *page's* messenger — while the dialog is still up (it
`return`s without popping). A SnackBar on the page's Scaffold renders beneath a pushed route and
its modal barrier.

For a real admin the form appears to do nothing: they press Create, nothing moves, no explanation
anywhere. Same defect class as `PaymentHandlerService` in the booking flow, where a refused charge
is reported on the dialog's context and the only evidence is `adb logcat`.

**The fix** (`admin_training_materials_page.dart`): the dialog now reports refusals *inline*,
next to the fields they are about, via the `StatefulBuilder`'s own `setLocal`. An inline message
is visible, is announced by assistive technology, and keeps the form and everything typed into it
— none of which a SnackBar under a modal barrier can do. The copy is unchanged, so the existing
assertions still hold.

Three paths were affected and all three now surface:
- an empty title or file URL,
- a non-numeric duration,
- **a failed save** — the data layer reports the server's message with a SnackBar on the page, and
  because a failure leaves the dialog open it was hidden for exactly the same reason. The dialog
  now states that the save did not succeed rather than leaving the button looking inert.

> `AdminQueuesTest.trainingRequiresTitleAndUrl` — **now green**, verified on a rebuilt APK.
> `flutter analyze` reports no new findings (6 before, 6 after).

### 4.11 A failed group booking told the client nothing — FIXED

Chasing the "same bug" in `PaymentHandlerService` found that **the diagnosis in the old notes was
stale**, and the real defect was worse.

What the notes said — that a refused charge is reported on a dialog's context and renders behind
it — is no longer true. Two changes had already fixed it: Review & pay became a *page*
(`Navigator.push`, returning a `Scaffold`) rather than a `Dialog`, and the "Processing Payment"
spinner is now popped through its own captured `_loadingDialogContext` before any message is
shown. The single-booking failure message is visible today.

The group path was not. `makePayment`'s catch block read:

```dart
} catch (e) {
  _closeLoadingDialog();
  if (groupBooking) { BottomnavigationBar.returnToShell(context); }
  if (!groupBooking) _showSnackBar('Error: $e', Colors.red);
}
```

A group booking that threw — a dropped connection, a server error, anything — closed the spinner,
dropped the client back on the home shell and **explained nothing**. Their booking had not
happened and nobody said so; the only evidence was `adb logcat`.

The suppression was not arbitrary. `returnToShell` does `pushAndRemoveUntil` and takes the whole
stack with it, so a SnackBar resolved from `context` afterwards had no route to appear on — the
author silenced the message rather than fixing where it was sent.

**The fix**: capture the app-level `ScaffoldMessengerState` once, on entry to `makePayment`, while
the context is certainly alive, and report on *that*. `MaterialApp`'s messenger outlives route
changes, so the message survives `returnToShell`. The `if (!groupBooking)` guard is gone and the
client is told first, then moved. It is the same reasoning `main.dart` already applies to session
expiry — "navigatorKey, not a captured context: the widget that made the failing request may
already be gone by the time the answer comes back".

> `flutter analyze`: no new findings (20 before, 20 after).
>
> **Not fully verified on-device.** The environment has no bookable availability, so
> `booking-payment.xml` reaches the slot picker and skips — the charge, and therefore the failure
> message, cannot be exercised here. The fix is verified by analysis and by the path running to
> that gate; seeding availability would let it be confirmed end to end.

### 4.12 The controls that are genuinely strong

Worth stating, because a findings list with no passes is not a test report:

- **No SQL injection.** Six payload classes across query parameters and path segments, compared
  against a control request; all bound as literals, and the `users` table survived the stacked
  `DROP TABLE` probes.
- **No path traversal** on the public document reader.
- **No IDOR** on any of the ten scoped routes probed — `resolveScopedClientId` and its equivalents
  resolve the caller from the token and ignore the supplied id entirely;
  `/userprofile/getUserProfile` goes further and refuses with an explicit 403.
- **Authentication is uniform.** 104 guarded routes, no credential and a forged token: not one
  answered.
- **The super-admin boundary is intact** on all ten reserved routes, in both directions.
- **Brute force is defended twice** — per-account lockout (423) and per-source rate limiting (429),
  the first engaging by the fifth attempt.
- **Hardening headers are complete**: HSTS, `nosniff`, `X-Frame-Options`, a real CSP,
  `Referrer-Policy`, and no `X-Powered-By`.
- **Performance is healthy.** p95 of 1-200 ms across seventeen blocking reads; 77-578 req/s under
  ten concurrent callers with zero errors.

---

## 5. What is deliberately not automated

| Area | Why |
|---|---|
| The 236 write endpoints, at API level | a sweep that wrote would corrupt the fixtures the functional suites assert against; they need per-route setup and teardown, which is the next body of work |
| Real payment capture | `booking-payment.xml` exists and works, but spends money (Stripe test mode) and leaves an appointment, so it is opt-in |
| Stripe Connect onboarding | needs a browser session against Stripe's own hosted flow |
| Persona IDV, SMS, email delivery | third-party; the webhooks are the testable half |
| iOS | no iOS runner configured; the page objects are Android accessibility-id based |

---

## 6. Running it

```bash
# no device needed — seconds, several hundred real HTTP calls
mvn test -DsuiteXmlFile=src/test/resources/suites/security-testng.xml \
         -Dfirebase.webApiKey=<web key from firebase_options.dart> -Ddb.password=… -DretryCount=0

# one emulator + backend + served portal
mvn test -DsuiteXmlFile=src/test/resources/suites/full-regression.xml \
         -Ddb.password=… -Dweb.baseUrl=http://localhost:8080 -DretryCount=0

# two emulators — writes real appointments
mvn test -DsuiteXmlFile=src/test/resources/suites/paired-testng.xml \
         -Ddevices.client=emulator-5554 -Ddevices.professional=emulator-5556

# where the gaps are
python3 scripts/coverage_matrix.py --gaps
```

Everything that cannot find the environment it needs **skips with the fix in the message** rather
than failing. An unprovisioned machine is not a defect, and a red suite that means "you didn't
export a key" teaches people to ignore red.
