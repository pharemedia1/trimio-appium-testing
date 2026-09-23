#!/usr/bin/env python3
"""
Trimio automation coverage matrix.

Answers one question honestly: of the backend's mounted HTTP surface, how much does the
automated suite actually exercise, and where are the gaps?

It reads two sources and nothing else, so it cannot flatter itself:
  * src/test/resources/testdata/api/endpoints.json — every route the server mounts
    (regenerate with scripts/harvest_api.py after any backend routing change);
  * the test sources — every path literal that appears in them.

A route counts as covered when a test names it, or when it falls into one of the exhaustive
sweeps (which iterate the inventory rather than a list of literals) -- those are reported
separately, because "swept for authorization" is a much weaker claim than "its behaviour is
asserted", and collapsing the two is how a coverage number becomes a lie.

    python3 scripts/coverage_matrix.py           # summary
    python3 scripts/coverage_matrix.py --gaps    # plus every uncovered route
"""
import json, os, re, sys, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INV = os.path.join(ROOT, 'src/test/resources/testdata/api/endpoints.json')
TESTS = os.path.join(ROOT, 'src/test/java')
MAIN = os.path.join(ROOT, 'src/main/java')

# Guards whose routes the security sweeps visit exhaustively: AuthenticationEnforcementTest
# hits every sweepable guarded GET, RoleAuthorizationMatrixTest every sweepable route per tier.
SWEPT_GUARDS = {'AUTH', 'PRO', 'VENDOR', 'STAFF', 'ADMIN', 'SUPER_ADMIN'}


def java_sources(root):
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith('.java'):
                yield os.path.join(dirpath, f)


def literal_paths():
    """Every '/...' string literal appearing in the Java sources, with the file that names it."""
    named = collections.defaultdict(set)
    pattern = re.compile(r'"(/[A-Za-z0-9_\-/:{}.]*)"')
    for src in list(java_sources(TESTS)) + list(java_sources(MAIN)):
        text = open(src, encoding='utf8', errors='replace').read()
        for m in pattern.finditer(text):
            named[m.group(1)].add(os.path.basename(src))
    return named


def main():
    show_gaps = '--gaps' in sys.argv
    inv = json.load(open(INV))
    endpoints = inv['endpoints']
    named = literal_paths()

    rows = []
    for e in endpoints:
        path, guard = e['path'], e['guard']
        direct = named.get(path, set())
        swept = e['method'] == 'GET' and not e['parameterised'] and guard in SWEPT_GUARDS
        rows.append(dict(e, direct=sorted(direct), swept=swept))

    total = len(rows)
    direct_n = sum(1 for r in rows if r['direct'])
    swept_n = sum(1 for r in rows if r['swept'] and not r['direct'])
    untouched = [r for r in rows if not r['direct'] and not r['swept']]

    print(f"Trimio API coverage — {total} mounted endpoints\n")
    print(f"  asserted by name        {direct_n:4}  ({direct_n*100//total}%)   a test names this exact path")
    print(f"  swept for authorization {swept_n:4}  ({swept_n*100//total}%)   reached only by the exhaustive security sweeps")
    print(f"  untouched               {len(untouched):4}  ({len(untouched)*100//total}%)")
    print(f"  ------------------------------")
    print(f"  reached at all          {direct_n+swept_n:4}  ({(direct_n+swept_n)*100//total}%)\n")

    print("By guard:")
    by_guard = collections.defaultdict(lambda: [0, 0, 0])
    for r in rows:
        slot = 0 if r['direct'] else (1 if r['swept'] else 2)
        by_guard[r['guard']][slot] += 1
    print(f"  {'GUARD':14} {'named':>6} {'swept':>6} {'none':>6} {'total':>6}")
    for g in ['PUBLIC', 'AUTH', 'PRO', 'VENDOR', 'STAFF', 'ADMIN', 'SUPER_ADMIN']:
        n, s, u = by_guard.get(g, [0, 0, 0])
        print(f"  {g:14} {n:>6} {s:>6} {u:>6} {n+s+u:>6}")

    print("\nBy method (writes are the weak half — a sweep can only safely GET):")
    by_method = collections.defaultdict(lambda: [0, 0])
    for r in rows:
        by_method[r['method']][0 if (r['direct'] or r['swept']) else 1] += 1
    for m, (cov, un) in sorted(by_method.items()):
        print(f"  {m:7} reached {cov:>4}   untouched {un:>4}")

    if show_gaps:
        print("\nUNTOUCHED ROUTES — no test names them and no sweep reaches them:")
        for r in sorted(untouched, key=lambda r: (r['router'], r['path'])):
            print(f"  {r['method']:6} {r['path']:58} [{r['guard']:11}] {r['router']}")
    else:
        print(f"\n  ({len(untouched)} untouched — run with --gaps to list them)")


if __name__ == '__main__':
    main()
