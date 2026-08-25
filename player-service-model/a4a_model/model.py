"""Training and inference utilities for the player similarity model.

The original model was trained only from a notebook and the server recreated
its preprocessing independently. This module is now the single source of
truth for feature engineering, model training, evaluation, and model
selection. The saved artifact is a :class:`SimilarityModel` bundle rather
than a bare scikit-learn estimator.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

import joblib
import numpy as np
import pandas as pd
from sklearn.neighbors import NearestNeighbors


MODEL_FEATURES = ("birthZ", "heightZ", "weightZ", "batsN", "throwsN")
REQUIRED_COLUMNS = ("playerID", "birthYear", "birthMonth", "birthDay", "weight", "height", "bats", "throws")
DEFAULT_CONFIG: dict[str, Any] = {
    "algorithm": "auto",
    "metric": "euclidean",
    "n_neighbors": 25,
    "feature_weights": [1.0, 1.0, 1.0, 1.0, 1.0],
}


def _as_numeric(values: pd.Series) -> pd.Series:
    return pd.to_numeric(values, errors="coerce")


def _hand_to_number(value: Any) -> float:
    if value == "R":
        return 1.0
    if value == "L":
        return -1.0
    return 0.0


def _birth_fraction(frame: pd.DataFrame) -> pd.Series:
    year = _as_numeric(frame["birthYear"])
    month = _as_numeric(frame["birthMonth"])
    day = _as_numeric(frame["birthDay"])
    return year + (month - 1.0) / 12.0 + (day - 1.0) / 30.0


def raw_feature_frame(frame: pd.DataFrame) -> pd.DataFrame:
    """Build the unscaled numeric and categorical feature columns."""

    missing = [column for column in REQUIRED_COLUMNS if column not in frame.columns]
    if missing:
        raise ValueError(f"Training data is missing required columns: {', '.join(missing)}")

    return pd.DataFrame(
        {
            "birthFraction": _birth_fraction(frame),
            "height": _as_numeric(frame["height"]),
            "weight": _as_numeric(frame["weight"]),
            "batsN": frame["bats"].map(_hand_to_number),
            "throwsN": frame["throws"].map(_hand_to_number),
        },
        index=frame.index,
    )


@dataclass
class FeaturePreprocessor:
    """The fitted transformations shared by training and API inference."""

    means: dict[str, float]
    standard_deviations: dict[str, float]

    @classmethod
    def fit(cls, frame: pd.DataFrame) -> "FeaturePreprocessor":
        raw = raw_feature_frame(frame)
        means: dict[str, float] = {}
        standard_deviations: dict[str, float] = {}
        for column in ("birthFraction", "height", "weight"):
            mean = float(raw[column].mean())
            standard_deviation = float(raw[column].std())
            if not np.isfinite(mean):
                mean = 0.0
            if not np.isfinite(standard_deviation) or standard_deviation == 0.0:
                standard_deviation = 1.0
            means[column] = mean
            standard_deviations[column] = standard_deviation
        return cls(means=means, standard_deviations=standard_deviations)

    def transform_dataframe(self, frame: pd.DataFrame, feature_weights: Iterable[float] | None = None) -> np.ndarray:
        raw = raw_feature_frame(frame)
        transformed = pd.DataFrame(index=frame.index)
        for source, target in (
            ("birthFraction", "birthZ"),
            ("height", "heightZ"),
            ("weight", "weightZ"),
        ):
            transformed[target] = (
                raw[source].fillna(self.means[source]) - self.means[source]
            ) / self.standard_deviations[source]
        transformed["batsN"] = raw["batsN"].fillna(0.0)
        transformed["throwsN"] = raw["throwsN"].fillna(0.0)
        matrix = transformed[list(MODEL_FEATURES)].fillna(0.0).to_numpy(dtype=float)
        return _apply_feature_weights(matrix, feature_weights)

    def transform_profile(self, profile: dict[str, Any], feature_weights: Iterable[float] | None = None) -> np.ndarray:
        values = {
            "birthZ": _standardize(profile.get("birth_year"), self.means["birthFraction"], self.standard_deviations["birthFraction"]),
            "heightZ": _standardize(profile.get("height"), self.means["height"], self.standard_deviations["height"]),
            "weightZ": _standardize(profile.get("weight"), self.means["weight"], self.standard_deviations["weight"]),
            "batsN": _hand_to_number(profile.get("bats")),
            "throwsN": _hand_to_number(profile.get("throws")),
        }
        matrix = np.array([[values[column] for column in MODEL_FEATURES]], dtype=float)
        return _apply_feature_weights(matrix, feature_weights)

    def metadata(self) -> dict[str, Any]:
        return {
            "means": self.means,
            "standard_deviations": self.standard_deviations,
            "features": list(MODEL_FEATURES),
        }


def _standardize(value: Any, mean: float, standard_deviation: float) -> float:
    if value is None or pd.isna(value):
        return 0.0
    return (float(value) - mean) / standard_deviation


def _apply_feature_weights(matrix: np.ndarray, feature_weights: Iterable[float] | None) -> np.ndarray:
    weights = np.ones(len(MODEL_FEATURES), dtype=float) if feature_weights is None else np.asarray(list(feature_weights), dtype=float)
    if weights.shape != (len(MODEL_FEATURES),):
        raise ValueError(f"feature_weights must contain {len(MODEL_FEATURES)} values")
    return matrix * weights


@dataclass
class SimilarityModel:
    """A fitted, versioned model bundle used by the Flask service."""

    estimator: NearestNeighbors
    preprocessor: FeaturePreprocessor
    player_ids: list[str]
    config: dict[str, Any]
    model_version: str
    trained_at: str
    training_rows: int
    data_hash: str
    evaluation: dict[str, Any] = field(default_factory=dict)

    def transform_dataframe(self, frame: pd.DataFrame) -> np.ndarray:
        return self.preprocessor.transform_dataframe(frame, self.config["feature_weights"])

    def transform_profile(self, profile: dict[str, Any]) -> np.ndarray:
        return self.preprocessor.transform_profile(profile, self.config["feature_weights"])

    def kneighbors(self, matrix: np.ndarray, n_neighbors: int, return_distance: bool = False):
        count = min(max(1, n_neighbors), len(self.player_ids))
        return self.estimator.kneighbors(matrix, n_neighbors=count, return_distance=return_distance)

    def metadata(self) -> dict[str, Any]:
        return {
            "model_version": self.model_version,
            "trained_at": self.trained_at,
            "training_rows": self.training_rows,
            "data_hash": self.data_hash,
            "config": self.config,
            "evaluation": self.evaluation,
            "preprocessor": self.preprocessor.metadata(),
        }


def _data_hash(frame: pd.DataFrame) -> str:
    return hashlib.sha256(pd.util.hash_pandas_object(frame, index=True).values.tobytes()).hexdigest()[:16]


def _model_version(config: dict[str, Any], data_hash: str) -> str:
    config_hash = hashlib.sha256(json.dumps(config, sort_keys=True).encode("utf-8")).hexdigest()[:10]
    return f"similarity-{data_hash}-{config_hash}"


def fit_similarity_model(frame: pd.DataFrame, config: dict[str, Any] | None = None) -> SimilarityModel:
    if frame.empty:
        raise ValueError("Training data must contain at least one player")
    if "playerID" not in frame.columns:
        raise ValueError("Training data must contain a playerID column")
    selected_config = dict(DEFAULT_CONFIG)
    if config:
        selected_config.update(config)
    selected_config["feature_weights"] = list(selected_config["feature_weights"])

    if frame["playerID"].isna().any():
        raise ValueError("Training data contains a player without playerID")
    if frame["playerID"].duplicated().any():
        raise ValueError("Training data contains duplicate playerID values")

    preprocessor = FeaturePreprocessor.fit(frame)
    matrix = preprocessor.transform_dataframe(frame, selected_config["feature_weights"])
    selected_config["n_neighbors"] = min(int(selected_config["n_neighbors"]), len(frame))
    estimator = NearestNeighbors(
        n_neighbors=selected_config["n_neighbors"],
        algorithm=selected_config["algorithm"],
        metric=selected_config["metric"],
    )
    estimator.fit(matrix)
    data_hash = _data_hash(frame)
    return SimilarityModel(
        estimator=estimator,
        preprocessor=preprocessor,
        player_ids=frame["playerID"].astype(str).tolist(),
        config=selected_config,
        model_version=_model_version(selected_config, data_hash),
        trained_at=datetime.now(timezone.utc).isoformat(),
        training_rows=len(frame),
        data_hash=data_hash,
    )


def enrich_features(
    frame: pd.DataFrame,
    preprocessor: FeaturePreprocessor | None = None,
    feature_weights: Iterable[float] | None = None,
) -> pd.DataFrame:
    """Return source data with the persisted feature columns used by inference."""

    fitted = preprocessor or FeaturePreprocessor.fit(frame)
    enriched = frame.copy()
    raw = raw_feature_frame(frame)
    enriched["birthFraction"] = raw["birthFraction"]
    matrix = fitted.transform_dataframe(frame, feature_weights)
    for index, column in enumerate(MODEL_FEATURES):
        enriched[column] = matrix[:, index]
    return enriched


def evaluate_model(model: SimilarityModel, frame: pd.DataFrame, sample_size: int = 1000) -> dict[str, Any]:
    """Calculate unlabeled retrieval diagnostics.

    Without curated similar-player labels, business metrics such as precision
    and recall would be misleading. The second-neighbor distance is therefore
    reported as a proxy diagnostic, not as a claim of recommendation quality.
    """

    if len(frame) < 2:
        return {
            "proxy_type": "unlabeled_retrieval_diagnostics",
            "sample_size": len(frame),
            "mean_second_neighbor_distance": None,
            "median_second_neighbor_distance": None,
            "coverage": 1.0 if len(frame) else 0.0,
        }
    sample = frame.head(min(sample_size, len(frame)))
    matrix = model.transform_dataframe(sample)
    distances, _ = model.estimator.kneighbors(matrix, n_neighbors=min(2, len(model.player_ids)), return_distance=True)
    second_neighbor_distances = distances[:, 1]
    return {
        "proxy_type": "unlabeled_retrieval_diagnostics",
        "sample_size": len(sample),
        "mean_second_neighbor_distance": float(np.mean(second_neighbor_distances)),
        "median_second_neighbor_distance": float(np.median(second_neighbor_distances)),
        "coverage": float(np.count_nonzero(np.isfinite(second_neighbor_distances)) / len(second_neighbor_distances)),
    }


def default_automl_candidates() -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    for metric in ("euclidean", "manhattan", "cosine"):
        for n_neighbors in (5, 10, 25):
            candidates.append({
                "algorithm": "brute" if metric == "cosine" else "auto",
                "metric": metric,
                "n_neighbors": n_neighbors,
                "feature_weights": [1.0, 1.0, 1.0, 1.0, 1.0],
            })
    return candidates


def automl_search(frame: pd.DataFrame, candidates: Iterable[dict[str, Any]] | None = None) -> tuple[SimilarityModel, list[dict[str, Any]]]:
    """Compare a bounded set of similarity configurations automatically."""

    results: list[dict[str, Any]] = []
    best_model: SimilarityModel | None = None
    best_score = float("inf")
    for candidate in candidates or default_automl_candidates():
        candidate_model = fit_similarity_model(frame, candidate)
        evaluation = evaluate_model(candidate_model, frame)
        distance = evaluation["mean_second_neighbor_distance"]
        score = float(distance) if distance is not None else float("inf")
        results.append({"config": candidate_model.config, "score": score, "evaluation": evaluation})
        if score < best_score:
            best_score = score
            best_model = candidate_model

    if best_model is None:
        raise ValueError("AutoML search did not evaluate any candidates")
    best_model.evaluation = {
        "selection": "automated_minimum_mean_second_neighbor_distance",
        "proxy_type": "unlabeled_retrieval_diagnostics",
        "selected_score": best_score,
    }
    return best_model, results


def train_from_csv(
    input_path: str | Path,
    model_path: str | Path,
    features_path: str | Path,
    metrics_path: str | Path | None = None,
    use_automl: bool = True,
) -> SimilarityModel:
    input_path = Path(input_path)
    frame = pd.read_csv(input_path)
    if use_automl:
        model, candidates = automl_search(frame)
    else:
        model = fit_similarity_model(frame)
        candidates = []
    model.evaluation = {
        **model.evaluation,
        **evaluate_model(model, frame),
        "automl_candidates": candidates,
    }
    joblib.dump(model, model_path)
    Path(features_path).parent.mkdir(parents=True, exist_ok=True)
    enrich_features(frame, model.preprocessor, model.config["feature_weights"]).to_csv(features_path, index=False)
    if metrics_path:
        Path(metrics_path).parent.mkdir(parents=True, exist_ok=True)
        Path(metrics_path).write_text(json.dumps(model.metadata(), indent=2) + "\n", encoding="utf-8")
    return model


def _cli() -> None:
    parser = argparse.ArgumentParser(description="Train and evaluate the player similarity model")
    parser.add_argument("--input", default="player.csv", help="Source player CSV")
    parser.add_argument("--model-output", default="team_model.joblib", help="Output joblib artifact")
    parser.add_argument("--features-output", default="features_db.csv", help="Output enriched feature CSV")
    parser.add_argument("--metrics-output", default="model_metrics.json", help="Output metadata and evaluation JSON")
    parser.add_argument("--no-automl", action="store_true", help="Train the baseline configuration only")
    args = parser.parse_args()
    model = train_from_csv(
        args.input,
        args.model_output,
        args.features_output,
        args.metrics_output,
        use_automl=not args.no_automl,
    )
    print(json.dumps(model.metadata(), indent=2))


if __name__ == "__main__":
    _cli()
