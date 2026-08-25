import os
import unittest

os.environ.setdefault("MODEL_FAILURE_SIMULATION", "false")

from a4a_model.server import app, runtime


class ServerTests(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()

    def test_generates_team_with_model_metadata(self):
        response = self.client.post("/team/generate", json={"seed_id": "aaronha01", "team_size": 3})

        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertEqual(body["seed_id"], "aaronha01")
        self.assertEqual(body["team_size"], 3)
        self.assertEqual(body["model_version"], runtime.model_version)
        self.assertEqual(len(body["member_ids"]), 3)

    def test_unknown_seed_is_a_client_error(self):
        response = self.client.post("/team/generate", json={"seed_id": "missing", "team_size": 3})

        self.assertEqual(response.status_code, 400)
        self.assertIn("not found", response.get_json()["message"])

    def test_team_size_is_validated(self):
        response = self.client.post("/team/generate", json={"seed_id": "aaronha01", "team_size": 26})

        self.assertEqual(response.status_code, 400)

    def test_metrics_endpoint_exposes_runtime_model_version(self):
        response = self.client.get("/metrics")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["model_version"], runtime.model_version)
        self.assertIn("generation_requests_total", response.get_json()["counters"])

    def test_feedback_is_tied_to_a_prediction(self):
        generated = self.client.post("/team/generate", json={"seed_id": "aaronha01", "team_size": 2}).get_json()
        member_id = generated["member_ids"][0]

        response = self.client.post("/team/feedback", json={
            "seed_id": "aaronha01",
            "member_id": member_id,
            "feedback": -1,
            "prediction_id": generated["prediction_id"],
        })

        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.get_json()["accepted"])


if __name__ == "__main__":
    unittest.main()
