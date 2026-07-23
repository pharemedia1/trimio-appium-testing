#!/bin/bash
# Switch the local EnterpriseDB PostgreSQL cluster on port 5432 to "trust"
# authentication for localhost only. Run with: sudo bash scripts/pg-trust-auth.sh
set -euo pipefail

TARGET_PORT=5432
DATADIR=""
VER=""

for v in 17 18 16 15 14; do
  d="/Library/PostgreSQL/$v/data"
  pidfile="$d/postmaster.pid"
  [ -f "$pidfile" ] || continue
  port="$(sed -n '4p' "$pidfile" | tr -d '[:space:]')"
  echo "PostgreSQL $v -> port ${port:-?}"
  if [ "$port" = "$TARGET_PORT" ]; then DATADIR="$d"; VER="$v"; fi
done

if [ -z "$DATADIR" ]; then
  echo "ERROR: no running cluster found on port $TARGET_PORT" >&2
  exit 1
fi

HBA="$DATADIR/pg_hba.conf"
echo "==> Target: PostgreSQL $VER ($HBA)"

backup="$HBA.bak.$(date +%Y%m%d%H%M%S)"
cp "$HBA" "$backup"
echo "==> Backup: $backup"

# Flip localhost (IPv4, IPv6, and unix-socket) auth methods to trust.
perl -pi -e '
  s/^(host\s+all\s+all\s+127\.0\.0\.1\/32\s+)\S+/${1}trust/;
  s/^(host\s+all\s+all\s+::1\/128\s+)\S+/${1}trust/;
  s/^(local\s+all\s+all\s+)\S+/${1}trust/;
' "$HBA"

echo "==> New localhost rules:"
grep -E '^(local|host)[[:space:]]+all[[:space:]]+all' "$HBA" || true

# Reload config (pg_hba changes do NOT require a restart -> live connections survive).
sudo -u postgres "/Library/PostgreSQL/$VER/bin/pg_ctl" reload -D "$DATADIR"
echo "==> Reloaded. Trust auth is now active for localhost on port $TARGET_PORT."
