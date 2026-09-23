-- Fixtures for the mobile regression tests that otherwise SKIP.
--
-- Each test below guards itself with a SkipException naming a precondition the environment does
-- not meet, so the suite reports "70 run, 22 skipped" and the skipped paths are never exercised.
-- This seeds those preconditions. It is idempotent: every statement is a no-op on a second run.
--
-- TEST DATABASE ONLY. The backend runs on a sk_test_ Stripe key; nothing here touches live money.
-- Run:  psql -h localhost -p 5433 -U postgres -d trimio -f scripts/seed_test_fixtures.sql
--
-- Cast of characters (all seeded by backend/scripts/seed_login_pair.js):
--   client       41501  Casey Client        trimiotest+client1@gmail.com
--   professional 41504  Pat Pro   = professional_id 878   trimiotest+pro1@gmail.com
-- Note professional_id is NOT a user_id: the mapping lives in the `professional` table.

\set ON_ERROR_STOP on
BEGIN;

-- ---------------------------------------------------------------------------
-- 1. An ACTIVE membership for the client.
--    Unblocks: ClientMembershipTest.managePlanShowsCredits
--              ClientMembershipTest.cancellationOffersAlternatives
--    Both skip on "The signed-in client has no active membership."
--    Plan 2 = Standard, client audience, 1 credit per cycle. The credit is granted through
--    membership_credit_ledger because the balance is derived from the ledger, not from the
--    plan -- an active subscription with no ledger row shows zero credits and the
--    showsCreditsRemaining() assertion would fail rather than skip.
-- ---------------------------------------------------------------------------
INSERT INTO membership_subscriptions
    (user_id, plan_id, status, locked_monthly_price, currency,
     current_period_start, current_period_end, started_at, audience, quantity)
SELECT 41501, 2, 'active', 89.00, 'usd',
       now() - interval '5 days', now() + interval '25 days', now() - interval '5 days',
       'client', 1
WHERE NOT EXISTS (
    SELECT 1 FROM membership_subscriptions
    WHERE user_id = 41501 AND status = 'active'
);

INSERT INTO membership_credit_ledger
    (subscription_id, user_id, entry_type, credit_delta, cash_value_delta,
     expires_at, notes, balance_before, balance_after)
SELECT s.subscription_id, 41501, 'grant', 1, 89.00,
       s.current_period_end, 'Test fixture: credit grant for the regression suite', 0, 1
FROM membership_subscriptions s
WHERE s.user_id = 41501 AND s.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM membership_credit_ledger l
      WHERE l.subscription_id = s.subscription_id AND l.entry_type = 'grant'
  );

COMMIT;

BEGIN;

-- ---------------------------------------------------------------------------
-- 2. A SUSPENDED professional, so the admin Quality page's "Suspended" list has a row.
--    Unblocks: AdminQualityTest.suspensionRequiresAReason
--    Skips on "The 'Suspended' list has no professional to act on in this environment".
--    Every one of the 257 professionals was account_status='active'.
--    1212 (Andre Dixon) is chosen deliberately: OFF DUTY with ZERO appointments, so suspending
--    it cannot reduce booking availability and cannot orphan an existing booking. Do NOT use
--    1508 -- that one is reserved below as the pro without payouts.
-- ---------------------------------------------------------------------------
UPDATE professional
   SET account_status   = 'suspended',
       suspended_at     = now() - interval '2 days',
       suspended_reason = 'Test fixture: repeated late arrivals'
 WHERE professional_id = 1212
   AND account_status <> 'suspended';

-- ---------------------------------------------------------------------------
-- 3. Active client enforcements, so the admin enforcement register is not empty.
--    Unblocks: AdminQueuesTest.enforcementsAreListed
--              AdminQueuesTest.extensionStatesItsBounds
--    Both skip on "No active enforcements in this environment".
--    "Active" is defined by the list endpoint (routes/enforcement.admin.router.js) as
--    (expires_at IS NULL OR expires_at > now()) AND metadata->>'void' = 'false', so both are set
--    explicitly -- a NULL metadata makes COALESCE(...,'false') pass but leaves the row looking
--    unlike anything the app writes.
--    Every row carries a reason: enforcementsAreListed asserts each hold states one.
--    Targets are the ma-seed clients, NOT trimiotest+client1..4 -- those four hold the payment
--    methods the booking and payment suites depend on, and suspending one would break them.
-- ---------------------------------------------------------------------------
INSERT INTO client_enforcement_actions
    (client_id, action, reason, created_by, created_at, expires_at, metadata)
SELECT v.client_id, 'suspend', v.reason, 53058, now() - interval '1 day',
       now() + v.lifetime, '{"void":"false"}'::jsonb
FROM (VALUES
        (41699, 'Test fixture: repeated no-shows',            interval '14 days'),
        (41700, 'Test fixture: chargeback abuse',             interval '30 days'),
        (41701, 'Test fixture: abusive conduct toward a pro', interval '7 days')
     ) AS v(client_id, reason, lifetime)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.user_id = v.client_id)
  AND NOT EXISTS (
      SELECT 1 FROM client_enforcement_actions e
      WHERE e.client_id = v.client_id
        AND (e.expires_at IS NULL OR e.expires_at > now())
        AND COALESCE(e.metadata->>'void','false') = 'false'
  );

-- ---------------------------------------------------------------------------
-- 4. A RECURRING series on ALL of the client's upcoming appointments.
--    Unblocks: ClientAppointmentsTest.recurringCancelAsksForScope
--    Skips on "The first appointment is not part of a recurring series".
--
--    Marking only the SOONEST one is not enough, and the reason is worth recording. The test
--    calls openFirst(), which opens whichever SECTION hasAnyAppointment() remembered -- and that
--    tries Future, Past, Today in that order. So "the first appointment" is the first card of the
--    FUTURE section, not the next appointment by clock time. With only today's marked, the test
--    opened tomorrow's un-marked booking and reported no series, while a recurring appointment
--    sat two hours away in Today.
--
--    Every upcoming, non-cancelled booking in the next 30 days is therefore marked, so whichever
--    section the page object lands in carries a series. Thirty days also outlives a single run:
--    pinning the fixture to one appointment breaks as soon as that appointment is in the past,
--    which is how this first came back after working earlier in the day.
-- ---------------------------------------------------------------------------
UPDATE appointments
   SET is_recurring        = true,
       recurrence_pattern  = COALESCE(recurrence_pattern, 'weekly'),
       recurrence_interval = COALESCE(recurrence_interval, 2),
       recurrence_count    = COALESCE(recurrence_count, 6)
 WHERE client_id = 41501
   AND is_recurring IS NOT TRUE
   AND scheduled_start_time > now()
   AND scheduled_start_time < now() + interval '30 days'
   AND status NOT IN ('canceled', 'cancelled');

-- ---------------------------------------------------------------------------
-- 5. A SECOND service on a completed, unreviewed appointment.
--    Unblocks: ClientReviewTest.allServicesMustBeRated
--    Skips on "The appointment has a single service already rated, so the per-service gate
--    cannot trigger -- use an appointment with two services."
--    Every completed appointment the client had carried exactly one service, so the rule that
--    EVERY service must be rated had nothing to bite on. 26529 is the most recent completed and
--    not-yet-reviewed one; service 21 belongs to professional 878, who performed it.
-- ---------------------------------------------------------------------------
INSERT INTO appointment_services (appointment_id, service_id, final_price, quoted_price)
SELECT 26529, 21, 85.00, 85.00
WHERE EXISTS (SELECT 1 FROM appointments WHERE appointment_id = 26529)
  AND NOT EXISTS (
      SELECT 1 FROM appointment_services
      WHERE appointment_id = 26529 AND service_id = 21
  );

COMMIT;

BEGIN;

-- ---------------------------------------------------------------------------
-- 6. A client with NO payment method.
--    Unblocks: ClientStyleMeNowTest.noCardBlocksBooking
--    Skips on "The signed-in client already has a card on file -- use a client without a
--    payment method to exercise this path."
--    This one cannot be satisfied by seeding alone: the assertion is about the ABSENCE of a
--    card, and the suite's main client (41501) must keep its card or the booking and payment
--    suites lose their charge. So a SECOND client is dedicated to the cardless case and the
--    test is pointed at it via roleAccounts.clientNoCard.
--    41617 (trimiotest+client4, 'Sasha Client') is chosen because it is fully provisioned --
--    active, email-verified, with a profile and an address, so it can sign in and reach the
--    booking flow -- and because no test refers to it. 41507 is NOT used: ObjectScopingTest
--    and MembershipApiTest both pin that id.
--    REVERSIBLE: `node backend/scripts/seed_login_pair.js` re-attaches pm_card_visa to every
--    seeded client, this one included.
-- ---------------------------------------------------------------------------
DELETE FROM client_payment_methods WHERE client_id = 41617;

-- ---------------------------------------------------------------------------
-- 7. A professional whose Stripe payouts are NOT enabled.
--    Unblocks: ProfessionalEarningsTest.withdrawalRequiresPayoutOnboarding
--    Skips on "The signed-in professional already has payouts enabled."
--    Same shape as 6: the suite's professional (878) must keep payouts enabled, so a second
--    professional carries the negative case and the test signs in as it.
--    881 (trimiotest+pro4) is verified, approved and unreferenced by any test. Professional
--    1508 is the other account with payouts disabled, but it is email-unverified with no
--    user_profile, so it cannot complete a sign-in.
--    REVERSIBLE: set payouts_enabled = true for professional_id 881.
-- ---------------------------------------------------------------------------
UPDATE professional_stripe_accounts
   SET payouts_enabled = false,
       onboarding_completed_at = NULL,
       requirements_due = COALESCE(requirements_due, '["external_account"]'::jsonb)
 WHERE professional_id = 881;

COMMIT;

BEGIN;

-- ---------------------------------------------------------------------------
-- 8. A professional on WARNING -- one that can still be suspended.
--    Unblocks: AdminQualityTest.suspensionRequiresAReason
--    The suspended professional seeded in section 2 is NOT enough, and the reason is worth
--    recording: the Quality detail page offers the actions that make sense for the CURRENT
--    status, so an already-SUSPENDED professional is offered "Deactivate" and "Reactivate" and
--    no "Suspend" at all. A test that proves suspending without a reason is refused therefore
--    needs a professional who is not yet suspended. The Warning list is the natural home for it
--    -- Quality has no "active" card -- and the count on that card was 0.
--    1203 (Elias Robinson) is off duty with no appointments, so nothing else is affected.
-- ---------------------------------------------------------------------------
UPDATE professional
   SET account_status = 'warning',
       last_warning_at = now() - interval '3 days'
 WHERE professional_id = 1203
   AND account_status = 'active';

COMMIT;

-- ---------------------------------------------------------------------------
-- 9. Clear half-finished review DRAFTS for the test client.
--    Unblocks nothing on its own, but without it ClientReviewTest is not repeatable.
--    The review flow saves a `reviews` row with status='draft' and a step_progress as the user
--    moves through the wizard, and every review test abandons the flow deliberately (submitting
--    is manual, because a submitted review is public and attached to a real professional). So the
--    SECOND run reopens a wizard that is already part-answered and "with no star chosen, Continue
--    must be disabled" fails against last run's answers rather than against the app.
--    ClientReviewTest now does this itself in an @BeforeMethod via DbHelper.clearReviewDrafts,
--    which only works when the suite can reach the database -- see the note on DB_PASSWORD below.
-- ---------------------------------------------------------------------------
DELETE FROM reviews WHERE reviewer_user_id = 41501 AND status = 'draft';

-- ---------------------------------------------------------------------------
-- 10. A day with NO availability, for the fully-booked path.
--     Unblocks: ClientBookingTest.fullyBookedDayShowsMessage
--     Skips on "No fully-booked day is present in this environment".
--     Sunday (weekday 0) is chosen because it is already the thin day: 2 professionals carry a
--     Sunday schedule against 46 on every other weekday, so clearing it costs the suite nothing.
--     Every test that needs availability calls openFirstDayWithTimes, which starts from today and
--     walks forward, so it steps over an empty Sunday without noticing.
--     is_available = false rather than DELETE, so the rows -- and the fact that these two
--     professionals nominally work Sundays -- survive for whoever looks next.
-- ---------------------------------------------------------------------------
UPDATE professional_schedule SET is_available = false WHERE weekday = 0;

-- ---------------------------------------------------------------------------
-- RUNNING THE SUITE
--   The mobile suite needs the database password to reset state between tests. It is deliberately
--   NOT in config.properties -- no credentials in the repo -- so pass it in the environment:
--       export DB_PASSWORD=<the local test password>
--       mvn -o test -DsuiteXmlFile=src/test/resources/suites/mobile-regression-testng.xml
--   ConfigReader reads UPPER_SNAKE env vars, so DB_PASSWORD maps to db.password. Without it
--   DbHelper.isConfigured() is false and every database-backed reset silently does nothing --
--   which does not fail loudly, it just makes the review tests order-dependent.
-- ---------------------------------------------------------------------------
