import tempfile
import unittest
from pathlib import Path

import joblib
import pandas as pd

from a4a_model.model import (
    MODEL_FEATURES,
    automl_search,
    evaluate_model,
    fit_similarity_model,
    train_from_csv,
)


def player_frame() -> pd.DataFrame:
    return pd.DataFrame(
        [
            {"playerID": "p1", "birthYear": 1980, "birthMonth": 1, "birthDay": 1, "weight": 180, "height": 72, "bats": "R", "throws": "R"},
            {"playerID": "p2", "birthYear": 1981, "birthMonth": 2, "birthDay": 2, "weight": 181, "height": 72, "bats": "R", "throws": "R"},
            {"playerID": "p3", "birthYear": 1990, "birthMonth": 6, "birthDay": 12, "weight": 210, "height": 77, "bats": "L", "throws": "L"},
            {"playerID": "p4", "birthYear": 1991, "birthMonth": 7, "birthDay": 13, "weight": 211, "height": 77, "bats": "L", "throws": "L"},
        ]
    )


class ModelTests(unittest.TestCase):
    def test_training_and_profile_inference_share_feature_contract(self):
        frame = player_frame()
        model = fit_similarity_model(frame, {"n_neighbors": 3})

        row_features = model.transform_dataframe(frame.iloc[[0]])
        profile_features = model.transform_profile({
            "birth_year": 1980,
            "height": 72,
            "weight": 180,
            "bats": "R",
            "throws": "R",
        })

        self.assertEqual(row_features.shape, (1, len(MODEL_FEATURES)))
        self.assertEqual(profile_features.shape, row_features.shape)
        self.assertEqual(model.player_ids, ["p1", "p2", "p3", "p4"])
        self.assertEqual(model.kneighbors(row_features, 2).shape, (1, 2))

    def test_evaluation_reports_unlabeled_proxy_metrics(self):
        frame = player_frame()
        model = fit_similarity_model(frame)

        evaluation = evaluate_model(model, frame)

        self.assertEqual(evaluation["proxy_type"], "unlabeled_retrieval_diagnostics")
        self.assertEqual(evaluation["sample_size"], 4)
        self.assertEqual(evaluation["coverage"], 1.0)
        self.assertIsNotNone(evaluation["mean_second_neighbor_distance"])

    def test_automl_returns_selected_model_and_candidate_report(self):
        model, candidates = automl_search(
            player_frame(),
            [
                {"algorithm": "auto", "metric": "euclidean", "n_neighbors": 2, "feature_weights": [1, 1, 1, 1, 1]},
                {"algorithm": "brute", "metric": "manhattan", "n_neighbors": 2, "feature_weights": [1, 1, 1, 1, 1]},
            ],
        )

        self.assertEqual(len(candidates), 2)
        self.assertIn("selection", model.evaluation)
        self.assertIn("selected_score", model.evaluation)

    def test_training_writes_versioned_artifact_features_and_metrics(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "players.csv"
            model_path = root / "team_model.joblib"
            features_path = root / "features_db.csv"
            metrics_path = root / "model_metrics.json"
            player_frame().to_csv(source, index=False)

            model = train_from_csv(source, model_path, features_path, metrics_path, use_automl=False)
            loaded = joblib.load(model_path)

            self.assertTrue(model_path.exists())
            self.assertTrue(features_path.exists())
            self.assertTrue(metrics_path.exists())
            self.assertEqual(loaded.model_version, model.model_version)
            self.assertTrue(model.model_version.startswith("similarity-"))
            self.assertIn("automl_candidates", model.evaluation)


if __name__ == "__main__":
    unittest.main()
