#!/bin/bash
# Run Trimio's mobile coverage: the regression and the paired suite, reseeding between them.
# Run with: bash scripts/run-mobile-suites.sh
set -euo pipefail
# No job-control notices when the backend is restarted.
set +m

# WHY THE RESEED BETWEEN SUITES
#
# The paired suite WRITES. PAIR-012 accepts a live on-demand offer, which creates a real
# appointment assigned to a real professional, and that appointment invalidates two of the
# regression's fixture invariants:
#
#   - it is the client's SOONEST upcoming booking and does not recur, so
#     recurringCancelAsksForScope opens it, finds no series and FAILS on
#     "Cancelling a series must ask whether to cancel only the next visit or all future visits"
#   - it sits on the professional's calendar inside the 135-minute dispatch exclusion window
#     (DEFAULT_JOB_MINUTES 120 + POST_JOB_COOLDOWN 15), so the next paired run is told
#     "Nobody's free right now" and skips
#
# Observed as both: a regression failure traced to appointment 26545 created by the paired run
# twenty minutes earlier, and two separate paired runs skipping on dispatchability.
#
# THE FIXTURES ALSO DECAY ON THEIR OWN, which is why the reseed is before the paired suite and
# not only after the regression. seed_suite_fixtures.js places the professional's booking
# MOVE_FORWARD_MINUTES (240) out, and the exclusion window is 135 -- so invariant 2 holds for
# only about 105 minutes. A regression takes 96. Starting the paired suite on the regression's
# own seeding lands right at that edge.
#
# So: reseed, run, reseed, run. The script then waits for the script's OWN dispatchability check
# to pass before starting the paired suite, rather than assuming the repair took.

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="${TRIMIO_BACKEND:-$HOME/StudioProjects/trimio/backend}"
FIXTURES="${TRIMIO_FIXTURES:-$BACKEND/scripts/seed_suite_fixtures.js}"
HEALTH_URL="${TRIMIO_HEALTH_URL:-http://localhost:3000/health}"
PORT="$(printf '%s' "$HEALTH_URL" | sed -E 's#^[a-z]+://[^:/]+:?([0-9]*)/.*#\1#')"
PORT="${PORT:-3000}"
CLIENT_DEVICE="${DEVICES_CLIENT:-emulator-5554}"
PRO_DEVICE="${DEVICES_PROFESSIONAL:-emulator-5556}"

[ -d "$BACKEND" ]  || { echo "ERROR: backend not found at $BACKEND (set TRIMIO_BACKEND)" >&2; exit 1; }
[ -f "$FIXTURES" ] || { echo "ERROR: fixture script not found at $FIXTURES (set TRIMIO_FIXTURES)" >&2; exit 1; }

# -- credentials -------------------------------------------------------------------------------
# -Ddb.password is NOT optional here. ClientReviewTest resets the saved review draft between its
# tests and skips when it cannot, because running those three against each other's leftovers
# produces wrong answers rather than missing ones.
DBPASS="${DB_PASSWORD:-}"
if [ -z "$DBPASS" ] && [ -f "$BACKEND/.env" ]; then
  DBPASS="$(grep -E '^DATABASE_URL=' "$BACKEND/.env" | sed -E 's#.*://[^:]+:([^@]+)@.*#\1#')"
fi
if [ "${#DBPASS}" -lt 3 ]; then
  echo "ERROR: no database password. The three review tests would skip. Set DB_PASSWORD." >&2
  exit 1
fi
printf 'db password: %s chars\n' "${#DBPASS}"

LOGDIR="$REPO/logs"; mkdir -p "$LOGDIR"

# -- backend ------------------------------------------------------------------------------------
# Stopped by LISTENING PORT, never by command-line pattern: `pkill -f 'node server.js'` matches
# the command line of any script that mentions it, and killed three queued runner scripts.
stop_backend() {
  local pid; pid="$(lsof -ti "tcp:$PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [ -n "$pid" ]; then
    kill $pid 2>/dev/null || true; sleep 3
    pid="$(lsof -ti "tcp:$PORT" -sTCP:LISTEN 2>/dev/null || true)"
    if [ -n "$pid" ]; then kill -9 $pid 2>/dev/null || true; sleep 1; fi
  fi
  # Explicit, and not incidental. Ending this function on `[ -n "$pid" ] && ...` made its RETURN
  # VALUE the result of that test, so with the backend already stopped it returned 1 and `set -e`
  # killed the script at the first call -- before a single line of output.
  return 0
}

# RAISED auth caps, unlike the security runner which needs the shipped ones. A regression signs in
# once per test -- seventy-odd times -- and the shipped credential limiter is 10 per 15 minutes
# per IP, so on shipped caps the suite throttles ITSELF and skips on "Too many attempts".
start_backend() {
  local tag="$1"
  stop_backend
  ( cd "$BACKEND" && AUTH_RATE_MAX=100000 AUTH_CREDENTIAL_RATE_MAX=100000 \
      nohup node server.js > "$LOGDIR/backend-$tag.log" 2>&1 & disown ) 2>/dev/null
  local w=0
  until curl -fs -o /dev/null --max-time 3 "$HEALTH_URL" 2>/dev/null; do
    sleep 1; w=$((w + 1))
    [ "$w" -gt 45 ] && { echo "ERROR: backend did not start. See $LOGDIR/backend-$tag.log" >&2; exit 1; }
  done
  printf '  backend up (raised caps, for repeated sign-ins) after %ss\n' "$w"
}

restore_shipped_caps() {
  stop_backend
  ( cd "$BACKEND" && env -u AUTH_RATE_MAX -u AUTH_CREDENTIAL_RATE_MAX \
      nohup node server.js > "$LOGDIR/backend-shipped.log" 2>&1 & disown ) 2>/dev/null
  sleep 6
  printf '  backend restored to SHIPPED caps (http %s)\n' \
    "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$HEALTH_URL" || echo '000')"
}

reseed() {
  printf '\n-- reseeding fixtures --\n'
  ( cd "$BACKEND" && node "$FIXTURES" ) 2>&1 | grep -E '✗|→|all invariants|unmet' || true
}

# Verified, not assumed: "Nobody's free right now" is a correct app answer that reads like a
# harness bug, so the dispatchability the reseed just claimed gets checked independently.
#
# IT REPAIRS RATHER THAN WAITS, and that distinction cost two runs. The first version polled
# --check every 60s for 21 minutes and gave up, twice, on the theory that something would clear
# on its own. Nothing can: a booking inside the 135-minute exclusion window only leaves it when
# something MOVES it, and the only thing that moves it is the repair. Polling was waiting for an
# event that had no cause.
#
# Observed the second time with the reseed reporting ZERO repairs -- "all invariants hold", so
# the fixture script's own check saw a dispatchable professional -- and the identical check
# reporting excluded one minute later. I could not identify what changed in between: the
# wall-clock slide I first assumed does not fit (the nearest booking was 236 minutes out, and
# 105 minutes of drift would be needed to reach the window). Rather than guess further, this now
# does the thing that is correct whatever the cause, and PRINTS what each repair moved so the
# next occurrence leaves evidence instead of 21 identical lines.
# Captures the output and matches it as a STRING. Never `node ... | grep -q`.
#
# THAT PIPELINE CANNOT SUCCEED UNDER `set -o pipefail`, which this script sets. grep -q exits the
# instant it matches, closing the pipe; node then dies with SIGPIPE, and pipefail reports the
# pipeline as failed even though the pattern WAS found. Demonstrated directly:
#
#   node ... --check | grep -q '✓ ...'                  -> status 0
#   set -o pipefail; node ... --check | grep -q '✓ ...'  -> status 141
#
# That single line cost two full runs. Both spent 21 minutes reporting "professional still
# excluded by their calendar" while the professional was dispatchable the whole time, and the
# paired suite skipped all three of its tests afterwards. It also sent me after the fixture
# script for "reporting a repair that did not work" -- it had reported correctly throughout.
dispatchable() {
  local out
  out="$( ( cd "$BACKEND" && node "$FIXTURES" --check ) 2>&1 || true )"
  [[ "$out" == *"✓ the professional is dispatchable"* ]]
}

wait_until_dispatchable() {
  local t
  for t in 1 2 3; do
    if dispatchable; then
      printf '  professional is dispatchable\n'
      return 0
    fi
    echo "  professional is excluded by their calendar; repairing (attempt ${t}/3)"
    ( cd "$BACKEND" && node "$FIXTURES" ) 2>&1 \
      | grep -E '✗|→|CONFIRMED|STILL|unmet' | sed 's/^/    /' || true
  done

  if dispatchable; then
    printf '  professional is dispatchable\n'
    return 0
  fi
  echo "  WARNING: still excluded after 3 repair attempts. The paired suite will skip, and that" >&2
  echo "           skip is the app behaving correctly -- not a harness failure. Two invariants" >&2
  echo "           fighting over one appointment is the usual reason a repair cannot converge." >&2
  return 0
}

# --paired-only runs the second half alone. Added because verifying a change to the paired half
# otherwise costs a 96-minute regression first, which is how a broken dispatchability check
# survived two full runs before being understood.
PAIRED_ONLY=0
[ "${1:-}" = "--paired-only" ] && PAIRED_ONLY=1

overall=0

if [ "$PAIRED_ONLY" = 0 ]; then
# NOTE: every grep over a suite log passes -a. Appium writes ANSI colour codes into
# these logs, so without it grep calls the file binary and prints "Binary file ...
# matches" instead of the results -- which it did, hiding a paired run's outcome.
printf '\n===== 1/2  mobile regression =====\n'
start_backend regression
reseed
( cd "$REPO" && mvn test -o \
    -DsuiteXmlFile=src/test/resources/suites/mobile-regression-testng.xml \
    -DretryCount=0 -Ddb.password="$DBPASS" ) > "$LOGDIR/mobile-regression.log" 2>&1 || overall=1
grep -a -E 'Tests run:.*Skipped' "$LOGDIR/mobile-regression.log" | tail -1 || true
printf '  failures=%s skips=%s   log: %s\n' \
  "$(grep -acoE '<<< FAIL: ' "$LOGDIR/mobile-regression.log" || true)" \
  "$(grep -acoE '<<< SKIP: ' "$LOGDIR/mobile-regression.log" || true)" \
  "$LOGDIR/mobile-regression.log"
grep -a -hoE '<<< (FAIL|SKIP): \w+' "$LOGDIR/mobile-regression.log" | sort -u | sed 's/^/    /' || true
else
  printf '\n===== --paired-only: skipping the regression =====\n'
  start_backend paired
fi

printf '\n===== 2/2  paired, on fixtures reseeded after the regression =====\n'
reseed
wait_until_dispatchable
( cd "$REPO" && mvn test -o \
    -DsuiteXmlFile=src/test/resources/suites/paired-testng.xml \
    -DretryCount=0 -Ddb.password="$DBPASS" \
    -Ddevices.client="$CLIENT_DEVICE" -Ddevices.professional="$PRO_DEVICE" ) \
  > "$LOGDIR/mobile-paired.log" 2>&1 || overall=1
grep -a -E 'Tests run:.*Skipped' "$LOGDIR/mobile-paired.log" | tail -1 || true
grep -a -hoE '<<< (PASS|FAIL|SKIP): \w+|the offer discloses a payout of .*' "$LOGDIR/mobile-paired.log" \
  | sed 's/^/    /' || true

# The paired suite has just written an appointment that breaks the invariants again. Leaving the
# fixtures repaired means the next thing anyone runs -- a single test, a rerun, tomorrow's
# regression -- starts from a known state rather than from this suite's leftovers.
printf '\n===== leaving the fixtures repaired for whatever runs next =====\n'
reseed
restore_shipped_caps

printf '\n'
if [ "$overall" = 0 ]; then
  echo "MOBILE SUITES PASSED. Skips above, if any, are listed by name."
else
  echo "MOBILE SUITES: see the failures listed above." >&2
fi
exit "$overall"
