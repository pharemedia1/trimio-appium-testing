#!/bin/bash
# Run Trimio's security coverage: both suites, in the only order that works.
# Run with: bash scripts/run-security-suites.sh
set -euo pipefail
# No job-control notices: restarting the backend otherwise prints "Terminated: 15" against the
# previous process, which reads like a failure in the middle of an otherwise clean run.
set +m

# WHY THIS SCRIPT EXISTS
#
# The security coverage is two suites and cannot be one, because of a single object in the
# backend. middleware/authRateLimit.js builds ONE credentialLimiter -- per IP, 10 requests per
# 15 minutes -- and routes/authRoutes.js and routes/passwordRoutes.js both mount it. One
# instance means one counter shared across /auth/login, /auth/checkUserExists and
# /password/forgotPassword.
#
# Some tests must EXHAUST that counter (brute-force throttling is their subject). Others must
# have it INTACT (comparing a registered address against an unknown one is impossible when both
# answers are "Too many attempts"). Both cannot hold in one 15-minute window, and reordering
# does not help: the enumeration calls plus the suite's logins exceed the allowance either way,
# so the 429s just move to different tests.
#
# The limiter is in memory, so restarting the backend zeroes it. That is the whole trick here:
# the suite that needs an intact budget runs first against a fresh process, then the backend is
# restarted and the suite that spends the budget runs second.
#
# NOTHING IS WEAKENED TO ACHIEVE THIS. The caps are not raised and no limiter is bypassed; both
# suites run against the shipped 10-per-15-minutes exactly as production has it. If you raise
# AUTH_CREDENTIAL_RATE_MAX to make these pass more easily, the throttling tests stop testing
# anything -- they would confirm a limiter that never engages.

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="${TRIMIO_BACKEND:-$HOME/StudioProjects/trimio/backend}"
FRONTEND="${TRIMIO_FRONTEND:-$HOME/StudioProjects/trimio/frontend}"
HEALTH_URL="${TRIMIO_HEALTH_URL:-http://localhost:3000/health}"

[ -d "$BACKEND" ] || { echo "ERROR: backend not found at $BACKEND (set TRIMIO_BACKEND)" >&2; exit 1; }

# -- credentials -------------------------------------------------------------------------------
# Both are passed on the command line and never written to a file. Taken from the environment
# when set, otherwise read out of the app repo, which is where the real values already live.
KEY="${FIREBASE_WEB_API_KEY:-}"
if [ -z "$KEY" ] && [ -f "$FRONTEND/lib/firebase_options.dart" ]; then
  KEY="$(grep -oE "apiKey: '[^']+'" "$FRONTEND/lib/firebase_options.dart" | head -1 | sed "s/apiKey: '//;s/'//")"
fi
DBPASS="${DB_PASSWORD:-}"
if [ -z "$DBPASS" ] && [ -f "$BACKEND/.env" ]; then
  DBPASS="$(grep -E '^DATABASE_URL=' "$BACKEND/.env" | sed -E 's#.*://[^:]+:([^@]+)@.*#\1#')"
fi

# ABORT rather than run: without the Firebase key every test SKIPS and mvn still exits 0, which
# reports as a green run of 43 tests that never executed. Without the db password the review
# reset and two schema-reading tests skip. A green that means "nothing ran" is worse than a red.
if [ "${#KEY}" -lt 30 ]; then
  echo "ERROR: no Firebase web API key. Every test would skip and mvn would still exit 0." >&2
  echo "       Set FIREBASE_WEB_API_KEY, or make $FRONTEND/lib/firebase_options.dart readable." >&2
  exit 1
fi
if [ "${#DBPASS}" -lt 3 ]; then
  echo "ERROR: no database password. Set DB_PASSWORD, or provide $BACKEND/.env." >&2
  exit 1
fi
printf 'firebase key: %s chars   db password: %s chars\n' "${#KEY}" "${#DBPASS}"

# -- backend -----------------------------------------------------------------------------------
LOGDIR="$REPO/logs"; mkdir -p "$LOGDIR"

# Restart on SHIPPED caps. Any AUTH_RATE_MAX / AUTH_CREDENTIAL_RATE_MAX override inherited from
# the environment is dropped on purpose: a backend carrying raised caps does not FAIL the
# throttling tests, it PASSES them against a limiter that never fires.
restart_backend() {
  local tag="$1"
  if pgrep -f 'node server.js' >/dev/null 2>&1; then
    pkill -f 'node server.js' || true
    sleep 3
  fi
  # disown so the shell stops tracking it as a job: without that, the next restart prints
  # "Terminated: 15" against the process this one just killed, which reads like a failure in the
  # middle of a clean run. The stderr filter is belt and braces for shells that report anyway.
  { ( cd "$BACKEND" \
      && env -u AUTH_RATE_MAX -u AUTH_CREDENTIAL_RATE_MAX \
             nohup node server.js > "$LOGDIR/backend-$tag.log" 2>&1 &
      disown ) ; } 2>/dev/null
  local waited=0
  until curl -fs -o /dev/null --max-time 3 "$HEALTH_URL" 2>/dev/null; do
    sleep 1; waited=$((waited + 1))
    if [ "$waited" -gt 45 ]; then
      echo "ERROR: backend did not become healthy within ${waited}s. See $LOGDIR/backend-$tag.log" >&2
      exit 1
    fi
  done
  printf '  backend up on a FRESH limiter (shipped caps) after %ss\n' "$waited"
}

# -- the two suites, in order ------------------------------------------------------------------
run_suite() {
  local tag="$1" xml="$2" label="$3"
  printf '\n===== %s =====\n' "$label"
  restart_backend "$tag"
  local log="$LOGDIR/security-$tag.log" status=0
  ( cd "$REPO" && mvn test -o \
      -DsuiteXmlFile="src/test/resources/suites/$xml" \
      -DretryCount=0 \
      -Dfirebase.webApiKey="$KEY" \
      -Ddb.password="$DBPASS" ) > "$log" 2>&1 || status=$?
  grep -E 'Tests run:.*Skipped' "$log" | tail -1 || true
  local skips fails
  skips="$(grep -coE '<<< SKIP: ' "$log" || true)"
  fails="$(grep -coE '<<< FAIL: ' "$log" || true)"
  printf '  exit=%s  failures=%s  skips=%s   log: %s\n' "$status" "${fails:-0}" "${skips:-0}" "$log"
  if [ "${fails:-0}" != "0" ] || [ "${skips:-0}" != "0" ]; then
    grep -hoE '<<< (FAIL|SKIP): \w+' "$log" | sort -u | sed 's/^/    /' || true
  fi
  return "$status"
}

overall=0

# FIRST, on an intact budget: the tests that need real answers to compare.
run_suite enumeration security-enumeration-testng.xml \
  "1/2  intact budget: account enumeration + SEC-062" || overall=1

# SECOND, on a fresh budget it is free to spend: brute force, lockout and everything else.
run_suite main security-testng.xml \
  "2/2  spends the budget: brute force, lockout, authz, transport" || overall=1

# The measurement SEC-062 exists to make. "answered 0 ... attempt 1" means the budget was
# already gone and the test skipped rather than pretending; anything else is a real engagement
# point for /auth/checkUserExists.
printf '\n===== the throttle engagement point =====\n'
grep -hoE 'SEC-062: checkUserExists answered [0-9]+ request\(s\), then throttled at attempt -?[0-9]+' \
  "$LOGDIR/security-enumeration.log" | sed 's/^/  /' || echo "  (SEC-062 did not report)"

printf '\n'
if [ "$overall" = 0 ]; then
  echo "SECURITY SUITES PASSED, nothing skipped."
else
  echo "SECURITY SUITES: see the failures or skips listed above." >&2
fi
exit "$overall"
