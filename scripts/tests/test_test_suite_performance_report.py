import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace


SCRIPT = Path(__file__).parents[1] / "test-suite-performance-report.py"
SPEC = importlib.util.spec_from_file_location("performance_report", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class PerformanceReportTest(unittest.TestCase):

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.reports = self.root / "reports"
        self.reports.mkdir()
        self.log = self.root / "maven.log"
        self.started_at = 1000.0

    def tearDown(self):
        self.temp.cleanup()

    def args(self):
        return SimpleNamespace(
            reports=self.reports,
            log=self.log,
            started_at=self.started_at,
            wall_seconds=12.0,
            slowest=10,
        )

    def write_report(self, failures=0):
        path = self.reports / "TEST-example.xml"
        path.write_text(
            f'<testsuite tests="1" failures="{failures}" errors="0" skipped="0" time="1.5">'
            '<testcase classname="Example" name="works" time="1.5"/></testsuite>',
            encoding="utf-8",
        )
        os.utime(path, (self.started_at + 1, self.started_at + 1))
        return path

    def test_distinguishes_application_and_fresh_container_starts(self):
        self.write_report()
        self.log.write_text(
            "GRAPHRAG_TEST_CONTEXT_START id=one\n"
            "GRAPHRAG_TEST_CONTAINER_START kind=postgresql scope=application id=p1\n"
            "GRAPHRAG_TEST_CONTAINER_START kind=postgresql scope=fresh id=p2\n"
            "GRAPHRAG_TEST_CONTAINER_START kind=neo4j scope=application id=n1\n"
            "BUILD SUCCESS\n",
            encoding="utf-8",
        )
        report = MODULE.build_report(self.args())
        self.assertEqual(1, report["container_starts"]["postgresql_application"])
        self.assertEqual(1, report["container_starts"]["postgresql_fresh"])
        self.assertEqual(1, report["container_starts"]["neo4j_application"])

    def test_rejects_stale_reports(self):
        path = self.write_report()
        os.utime(path, (self.started_at - 1, self.started_at - 1))
        self.log.write_text("BUILD SUCCESS\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "stale"):
            MODULE.build_report(self.args())

    def test_rejects_failed_runs(self):
        self.write_report(failures=1)
        self.log.write_text("BUILD SUCCESS\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "not successful"):
            MODULE.build_report(self.args())


if __name__ == "__main__":
    unittest.main()
