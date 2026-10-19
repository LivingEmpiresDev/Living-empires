"""Regression tests for permanent simulation trace validation."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("validate_turn_trace.py")


class TraceValidationTests(unittest.TestCase):
    def run_case(self, trace, expected, should_pass):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "trace.log"
            report = Path(directory) / "report.json"
            source.write_text(trace, encoding="utf-8")
            process = subprocess.run(
                [sys.executable, str(SCRIPT), str(source), "--expected-turns",
                 str(expected), "--seed", "42", "--output", str(report)],
                capture_output=True, text=True,
            )
            self.assertEqual(process.returncode == 0, should_pass, process.stderr)
            if should_pass:
                data = json.loads(report.read_text(encoding="utf-8"))
                self.assertEqual(data["completed_turns"], expected)
                self.assertTrue(data["passed"])
            else:
                self.assertFalse(report.exists())

    def test_valid_trace(self):
        self.run_case("OJH ready\nOJH turn 1 0.2\nOJH turn 2 0.4\n", 2, True)

    def test_missing_turn(self):
        self.run_case("OJH ready\nOJH turn 1 0.2\nOJH turn 3 0.4\n", 3, False)

    def test_duplicate_turn(self):
        self.run_case("OJH ready\nOJH turn 1 0.2\nOJH turn 1 0.4\n", 2, False)

    def test_missing_ready(self):
        self.run_case("OJH turn 1 0.2\n", 1, False)

    def test_incomplete_game(self):
        self.run_case("OJH ready\nOJH turn 1 0.2\n", 2, False)

    def test_invalid_duration(self):
        self.run_case("OJH ready\nOJH turn 1 -0.2\n", 1, False)


if __name__ == "__main__":
    unittest.main()
