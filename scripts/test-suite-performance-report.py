#!/usr/bin/env python3
"""Summarize a fresh, successful Maven Surefire run."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


CONTAINER_MARKER = re.compile(
    r"GRAPHRAG_TEST_CONTAINER_START kind=(postgresql|neo4j) scope=(application|fresh) id=(\S+)"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--started-at", type=float, required=True)
    parser.add_argument("--wall-seconds", type=float)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--slowest", type=int, default=10)
    return parser.parse_args()


def build_report(args: argparse.Namespace) -> dict[str, object]:
    xml_files = sorted(args.reports.glob("TEST-*.xml"))
    if not xml_files:
        raise ValueError("no Surefire XML reports found")
    stale = [str(path) for path in xml_files if path.stat().st_mtime + 0.001 < args.started_at]
    if stale:
        raise ValueError(f"stale Surefire reports found: {len(stale)}")

    suites = 0
    tests = 0
    failures = 0
    errors = 0
    skipped = 0
    aggregate_seconds = 0.0
    methods: list[dict[str, object]] = []
    for path in xml_files:
        root = ET.parse(path).getroot()
        suites += 1
        tests += int(root.attrib.get("tests", 0))
        failures += int(root.attrib.get("failures", 0))
        errors += int(root.attrib.get("errors", 0))
        skipped += int(root.attrib.get("skipped", 0))
        aggregate_seconds += float(root.attrib.get("time", 0.0))
        for test_case in root.findall("testcase"):
            methods.append({
                "test": f"{test_case.attrib.get('classname', '')}#{test_case.attrib.get('name', '')}",
                "seconds": float(test_case.attrib.get("time", 0.0)),
            })
    if failures or errors:
        raise ValueError(f"Surefire run was not successful: failures={failures}, errors={errors}")

    log = args.log.read_text(encoding="utf-8", errors="replace")
    if "BUILD SUCCESS" not in log:
        raise ValueError("Maven log does not contain BUILD SUCCESS")
    markers = CONTAINER_MARKER.findall(log)
    starts = {
        "postgresql_application": len({identity for kind, scope, identity in markers
                                       if kind == "postgresql" and scope == "application"}),
        "postgresql_fresh": len({identity for kind, scope, identity in markers
                                if kind == "postgresql" and scope == "fresh"}),
        "neo4j_application": len({identity for kind, scope, identity in markers
                                 if kind == "neo4j" and scope == "application"}),
        "neo4j_fresh": len({identity for kind, scope, identity in markers
                           if kind == "neo4j" and scope == "fresh"}),
    }
    methods.sort(key=lambda item: float(item["seconds"]), reverse=True)
    return {
        "successful": True,
        "suites": suites,
        "tests": tests,
        "skipped": skipped,
        "aggregate_seconds": round(aggregate_seconds, 3),
        "wall_seconds": args.wall_seconds,
        "spring_context_starts": log.count("GRAPHRAG_TEST_CONTEXT_START"),
        "container_starts": starts,
        "slowest_tests": methods[:args.slowest],
    }


def main() -> int:
    args = parse_args()
    try:
        report = build_report(args)
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"performance report error: {error}", file=sys.stderr)
        return 2
    rendered = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
