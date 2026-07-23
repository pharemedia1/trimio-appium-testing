"""Source-controlled test-case data for the Trimio suite.

Each module in this package exposes ``SHEET`` (the worksheet name) and ``CASES``
(a list of ``Case`` objects). ``scripts/generate_test_cases.py`` collects them and
writes ``docs/Trimio-Test-Cases.xlsx``.

Keeping the cases as code rather than as a hand-edited workbook means they diff,
review and regenerate like the rest of the framework — the .xlsx is a build output.
"""

from dataclasses import dataclass, field
from typing import List


@dataclass
class Case:
    """One test case.

    ``steps`` uses ``|`` as the step separator so a case stays a single readable
    line in source; the generator turns it into numbered lines in the cell.
    """

    module: str
    sub: str
    role: str
    platform: str
    title: str
    pre: str
    steps: str
    data: str
    exp: str
    pri: str = "P2"
    typ: str = "Functional"
    auto: str = "Manual"
    script: str = ""

    def numbered_steps(self) -> str:
        parts = [p.strip() for p in self.steps.split("|") if p.strip()]
        return "\n".join(f"{i}. {p}" for i, p in enumerate(parts, 1))


def C(module, sub, role, platform, title, pre, steps, data, exp,
      pri="P2", typ="Functional", auto="Manual", script="") -> Case:
    """Terse constructor so the case tables stay readable."""
    return Case(module, sub, role, platform, title, pre, steps, data, exp,
                pri, typ, auto, script)


# Column order of every test-case worksheet.
COLUMNS = [
    ("TC ID", 12),
    ("Module", 22),
    ("Sub-Module", 24),
    ("Role", 14),
    ("Platform", 12),
    ("Test Case Title", 52),
    ("Preconditions", 40),
    ("Test Steps", 58),
    ("Test Data", 32),
    ("Expected Result", 54),
    ("Priority", 9),
    ("Type", 14),
    ("Automation Status", 17),
    ("Automated Test", 46),
]
