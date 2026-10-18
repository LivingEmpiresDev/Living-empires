import unittest

from validate_war_telemetry import validate


class WarTelemetryValidationTests(unittest.TestCase):
    def test_valid_contiguous_turns(self):
        validate("SIM diplomacy turn=1 warPairs=0\nSIM diplomacy turn=2 warPairs=3", 2)

    def test_missing_turn(self):
        with self.assertRaises(ValueError):
            validate("SIM diplomacy turn=1 warPairs=0\nSIM diplomacy turn=3 warPairs=1", 3)

    def test_duplicate_turn(self):
        with self.assertRaises(ValueError):
            validate("SIM diplomacy turn=1 warPairs=0\nSIM diplomacy turn=1 warPairs=1", 2)

    def test_missing_telemetry(self):
        with self.assertRaises(ValueError):
            validate("OJH turn 1 0.02", 1)


if __name__ == "__main__":
    unittest.main()
