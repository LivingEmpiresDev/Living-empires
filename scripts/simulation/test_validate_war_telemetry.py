import unittest

from validate_war_telemetry import validate


class WarTelemetryValidationTests(unittest.TestCase):
    def test_valid_contiguous_turns(self):
        validate("SIM diplomacy turn=1 warPairs=0\nSIM transitions turn=1 warsStarted=0 warsEnded=0\nSIM diplomacy turn=2 warPairs=3\nSIM transitions turn=2 warsStarted=3 warsEnded=0", 2)

    def test_war_end_is_accounted_for(self):
        validate("SIM diplomacy turn=1 warPairs=2\\nSIM transitions turn=1 warsStarted=2 warsEnded=0\\nSIM diplomacy turn=2 warPairs=1\\nSIM transitions turn=2 warsStarted=0 warsEnded=1", 2)

    def test_inconsistent_war_transition_fails(self):
        with self.assertRaises(ValueError):
            validate("SIM diplomacy turn=1 warPairs=2\\nSIM transitions turn=1 warsStarted=1 warsEnded=0", 1)

    def test_missing_transition_fails(self):
        with self.assertRaises(ValueError):
            validate("SIM diplomacy turn=1 warPairs=0", 1)

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
