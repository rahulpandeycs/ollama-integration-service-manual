import unittest

from a4a_model.monitor import calculate_drift
from tests.test_model import player_frame


class MonitorTests(unittest.TestCase):
    def test_identical_reference_and_current_data_is_stable(self):
        frame = player_frame()

        report = calculate_drift(frame, frame.copy())

        self.assertEqual(report["overall_status"], "stable")
        self.assertEqual(report["reference_rows"], 4)
        self.assertEqual(report["current_rows"], 4)
        self.assertTrue(all(feature["status"] == "stable" for feature in report["features"].values()))

    def test_shifted_current_data_is_flagged(self):
        reference = player_frame()
        current = player_frame()
        current["height"] = 90
        current["weight"] = 300

        report = calculate_drift(reference, current)

        self.assertIn(report["overall_status"], {"watch", "drift"})
        self.assertIn(report["features"]["heightZ"]["status"], {"watch", "drift"})
        self.assertIn(report["features"]["weightZ"]["status"], {"watch", "drift"})


if __name__ == "__main__":
    unittest.main()
