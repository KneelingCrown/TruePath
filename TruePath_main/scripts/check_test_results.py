"""Fail CI on missing, empty, failed, or entirely skipped JUnit results."""

import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def check(directory):
    reports = sorted(Path(directory).glob("TEST-*.xml"))
    if not reports:
        raise ValueError("No JUnit reports found; unit tests were not verified")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        root = ET.parse(report).getroot()
        if root.tag != "testsuite":
            raise ValueError(f"Unsupported JUnit report: {report.name}")
        cases = root.findall("testcase")
        counts = {
            "tests": len(cases),
            "failures": sum(c.find("failure") is not None for c in cases),
            "errors": sum(c.find("error") is not None for c in cases),
            "skipped": sum(c.find("skipped") is not None for c in cases),
        }
        for key, value in counts.items():
            declared = int(root.get(key, "0"))
            if declared != value:
                raise ValueError(f"Inconsistent {key} count in {report.name}")
            totals[key] += value
    passed = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    summary = (
        f"Unit tests: {totals['tests']} total, {passed} passed, "
        f"{totals['failures']} failures, {totals['errors']} errors, "
        f"{totals['skipped']} skipped."
    )
    print(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as stream:
            stream.write(summary + "\n")
    if passed <= 0 or totals["failures"] or totals["errors"]:
        raise ValueError("Unit-test readiness check failed")


if __name__ == "__main__":
    try:
        check(sys.argv[1])
    except (ValueError, OSError, ET.ParseError, IndexError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
