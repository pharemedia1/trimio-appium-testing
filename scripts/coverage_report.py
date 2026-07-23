#!/usr/bin/env python3
"""Report automation coverage of the test-case suite, and why anything is uncovered.

    python3 scripts/coverage_report.py [--csv out.csv]

Cross-references every case in scripts/testcases/*.py against the @Test methods that
actually exist in src/test/java, so "automated" in the workbook can never drift from
reality. A case counts as covered only when the test it names is a real method.

Every uncovered case is classified, because "219 manual" is not an actionable number
and "81 of them would charge a real card" is:

  AUTOMATABLE   nothing stops it — this is the backlog worth burning down
  NEEDS-SANDBOX moves money, issues credentials, or is irreversible. Automatable only
                against a disposable environment (Stripe test mode, mail sandbox,
                per-run teardown); dangerous in an unattended suite against anything real
  NON-FUNCTIONAL performance, compatibility, accessibility, locale — real coverage, but
                it belongs to different tooling, not the functional regression run
"""

import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from testcases import admin, auth, client, crosscutting, professional, store, web

MODULES = [auth, client, professional, admin, store, web, crosscutting]
TEST_ROOT = Path(__file__).resolve().parent.parent / "src" / "test" / "java"

# An uncovered case lands in NEEDS-SANDBOX if it touches any of these. Deliberately broad:
# a false positive costs a second look, a false negative costs a real charge.
SANDBOX_MARKERS = (
    "payment", "card", "stripe", "charge", "refund", "payout", "withdraw", "tip",
    "invoice", "subscrib", "membership activated", "invite", "credential", "temporary password",
    "suspend", "reinstate", "approve", "reject", "publish", "deactivate", "delete",
)
NON_FUNCTIONAL_TYPES = {
    "Performance", "Compatibility", "Accessibility", "Reliability",
    "Data Integrity", "Configuration", "Test Infra",
}


def existing_test_methods() -> set:
    """Every {Class}#{method} that really exists, plus a bare {method} index."""
    found = set()
    for path in TEST_ROOT.rglob("*.java"):
        source = path.read_text()
        cls = path.stem
        for method in re.findall(r"public void (\w+)\(", source):
            found.add(f"{cls}#{method}")
            found.add(method)
    return found


def is_covered(case, methods) -> bool:
    if case.auto != "Automated" or not case.script:
        return False
    ref = case.script.split(" ")[0]
    if "#" not in ref:
        return False  # points at a page object, not a test
    short = ref.split(".")[-1]
    return short in methods or short.split("#")[-1] in methods


def classify(case) -> str:
    if case.typ in NON_FUNCTIONAL_TYPES:
        return "NON-FUNCTIONAL"
    haystack = f"{case.title} {case.exp} {case.steps}".lower()
    if any(marker in haystack for marker in SANDBOX_MARKERS):
        return "NEEDS-SANDBOX"
    return "AUTOMATABLE"


def main():
    methods = existing_test_methods()
    rows, totals, by_sheet = [], Counter(), defaultdict(Counter)

    for module in MODULES:
        for index, case in enumerate(module.CASES, 1):
            tc_id = f"{module.PREFIX}-{index:03d}"
            covered = is_covered(case, methods)
            status = "COVERED" if covered else classify(case)
            totals[status] += 1
            by_sheet[module.SHEET][status] += 1
            rows.append((tc_id, module.SHEET, case.pri, status, case.title, case.script))

    total = sum(totals.values())
    print(f"Trimio automation coverage — {total} cases\n")
    for status in ("COVERED", "AUTOMATABLE", "NEEDS-SANDBOX", "NON-FUNCTIONAL"):
        n = totals[status]
        print(f"  {status:<15}{n:>4}   {n / total * 100:5.1f}%")

    print("\nBy worksheet:")
    header = f"  {'sheet':<26}{'cov':>5}{'auto':>6}{'sandbox':>9}{'non-fn':>8}"
    print(header + "\n  " + "-" * (len(header) - 2))
    for sheet, counts in by_sheet.items():
        print(f"  {sheet:<26}{counts['COVERED']:>5}{counts['AUTOMATABLE']:>6}"
              f"{counts['NEEDS-SANDBOX']:>9}{counts['NON-FUNCTIONAL']:>8}")

    # The backlog, highest priority first — this is what to write next.
    backlog = [r for r in rows if r[3] == "AUTOMATABLE"]
    backlog.sort(key=lambda r: (r[2], r[0]))
    print(f"\nBacklog — automatable, not yet covered ({len(backlog)}), P1 first:")
    for tc_id, sheet, pri, _, title, _ in backlog[:25]:
        print(f"  {tc_id}  {pri}  {title[:72]}")
    if len(backlog) > 25:
        print(f"  … and {len(backlog) - 25} more")

    if "--csv" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--csv") + 1])
        import csv
        with out.open("w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["TC ID", "Sheet", "Priority", "Status", "Title", "Automated Test"])
            w.writerows(rows)
        print(f"\nWrote {out}")


if __name__ == "__main__":
    main()
