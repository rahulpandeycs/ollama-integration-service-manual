"""HTTP API for the player similarity model."""

import json
import logging
import os
import random
import sys
import threading
import time
import uuid
from pathlib import Path
from typing import Any, Literal, Optional

import joblib
import numpy as np
import pandas as pd
from flask import Flask, jsonify, request
from flask_pydantic import validate
from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

BASE_DIR = Path(__file__).resolve().parent
if str(BASE_DIR.parent) not in sys.path:
    sys.path.insert(0, str(BASE_DIR.parent))

try:
    from .model import FeaturePreprocessor, SimilarityModel
except ImportError:  # Also supports running the module directly as a script.
    from a4a_model.model import FeaturePreprocessor, SimilarityModel


MODEL_PATH = Path(os.getenv("MODEL_PATH", BASE_DIR / "team_model.joblib"))
FEATURES_PATH = Path(os.getenv("FEATURES_PATH", BASE_DIR / "features_db.csv"))
FEEDBACK_LOG_PATH = os.getenv("FEEDBACK_LOG_PATH")
FAILURE_SIMULATION_ENABLED = os.getenv("MODEL_FAILURE_SIMULATION", "true").lower() == "true"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class TeamException(Exception):
    pass


class Metrics:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._counters = {
            "generation_requests_total": 0,
            "generation_success_total": 0,
            "generation_errors_total": 0,
            "generation_timeouts_total": 0,
            "feedback_requests_total": 0,
            "feedback_accepted_total": 0,
            "feedback_rejected_total": 0,
            "unknown_seed_total": 0,
            "invalid_request_total": 0,
        }
        self._generation_latency_ms_total = 0.0

    def count(self, name: str, amount: int = 1) -> None:
        with self._lock:
            self._counters[name] = self._counters.get(name, 0) + amount

    def record_generation(self, outcome: str, latency_ms: float) -> None:
        with self._lock:
            self._counters["generation_requests_total"] += 1
            self._counters[f"generation_{outcome}_total"] += 1
            self._generation_latency_ms_total += latency_ms

    def snapshot(self, model_version: str) -> dict[str, Any]:
        with self._lock:
            counters = dict(self._counters)
            request_count = counters["generation_requests_total"]
            average_latency = self._generation_latency_ms_total / request_count if request_count else 0.0
        return {
            "model_version": model_version,
            "counters": counters,
            "average_generation_latency_ms": round(average_latency, 3),
        }


class ModelRuntime:
    """Loads either the new model bundle or the previous bare estimator."""

    def __init__(self, model_path: Path, features_path: Path) -> None:
        self.player_db = pd.read_csv(features_path)
        if "playerID" not in self.player_db.columns:
            raise ValueError("Feature database must contain a playerID column")
        loaded = joblib.load(model_path)
        if isinstance(loaded, SimilarityModel):
            self.bundle: SimilarityModel | None = loaded
            self.estimator = loaded.estimator
            self.preprocessor = loaded.preprocessor
            self.model_version = loaded.model_version
        else:
            self.bundle = None
            self.estimator = loaded
            self.preprocessor = FeaturePreprocessor.fit(self.player_db)
            self.model_version = os.getenv("MODEL_VERSION", "legacy-nearest-neighbor")
        self.all_players = set(self.player_db["playerID"].astype(str))

    def transform_dataframe(self, frame: pd.DataFrame) -> np.ndarray:
        if self.bundle:
            return self.bundle.transform_dataframe(frame)
        return self.preprocessor.transform_dataframe(frame)

    def transform_profile(self, profile: dict[str, Any]) -> np.ndarray:
        if self.bundle:
            return self.bundle.transform_profile(profile)
        return self.preprocessor.transform_profile(profile)

    def kneighbors(self, matrix: np.ndarray, count: int) -> tuple[np.ndarray, np.ndarray]:
        count = min(max(1, count), len(self.player_db))
        distances, indices = self.estimator.kneighbors(matrix, n_neighbors=count, return_distance=True)
        return distances, indices


app = Flask(__name__)
runtime = ModelRuntime(MODEL_PATH, FEATURES_PATH)
metrics = Metrics()

# Feedback exclusions and prediction records are intentionally process-local
# for this first iteration. Events are logged and can optionally be appended
# to FEEDBACK_LOG_PATH for offline quality analysis.
exclude_db: dict[str | None, set[str]] = {}
prediction_db: dict[str, dict[str, Any]] = {}
event_lock = threading.Lock()


class Features(BaseModel):
    birth_year: Optional[float] = None
    height: Optional[float] = None
    weight: Optional[float] = None
    bats: Optional[Literal["L", "R", "N"]] = None
    throws: Optional[Literal["L", "R", "N"]] = None


class TeamGenerateInput(BaseModel):
    seed_id: Optional[str] = None
    features: Optional[Features] = None
    team_size: int = Field(..., ge=1, le=25)

    @model_validator(mode="after")
    def has_input_source(self) -> "TeamGenerateInput":
        if not self.seed_id and self.features is None:
            raise ValueError("The payload must include either seed_id or features")
        return self


class TeamGenerateOutput(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    seed_id: Optional[str]
    prediction_id: str
    model_version: str
    team_size: int
    member_ids: list[str]


class TeamFeedbackInput(BaseModel):
    seed_id: str
    member_id: str
    feedback: Literal[-1, 1]
    prediction_id: str


class TeamFeedbackOutput(BaseModel):
    seed_id: str
    prediction_id: str
    member_id: str
    accepted: bool


def _emit_event(event: dict[str, Any]) -> None:
    event_json = json.dumps(event, separators=(",", ":"), default=str)
    logger.info(event_json)
    if not FEEDBACK_LOG_PATH:
        return
    with event_lock:
        path = Path(FEEDBACK_LOG_PATH)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8") as event_file:
            event_file.write(event_json + "\n")


def _simulate_failure() -> None:
    if not FAILURE_SIMULATION_ENABLED:
        return
    failure_simulator = random.random()
    if failure_simulator < 0.01:
        raise TimeoutError("Unable to generate result.")
    if failure_simulator < 0.02:
        time.sleep(6.0)


@app.post("/team/generate")
@validate()
def generate_team(body: TeamGenerateInput) -> TeamGenerateOutput:
    started_at = time.perf_counter()
    prediction_id = str(uuid.uuid4())
    outcome = "errors"
    try:
        _simulate_failure()
        seed = body.seed_id
        if seed:
            seed_rows = runtime.player_db[runtime.player_db.playerID == seed]
            if seed_rows.empty:
                metrics.count("unknown_seed_total")
                raise TeamException(f"The seed {seed} was not found")
            seed_features = runtime.transform_dataframe(seed_rows)
        else:
            seed_features = runtime.transform_profile(body.features.model_dump())

        exclusions = exclude_db.get(seed, set())
        count = min(body.team_size + len(exclusions), len(runtime.player_db))
        distances, member_indices = runtime.kneighbors(seed_features, count)
        candidate_ids = runtime.player_db.iloc[member_indices[0]]["playerID"].astype(str).tolist()
        member_ids = [member_id for member_id in candidate_ids if member_id not in exclusions][:body.team_size]

        prediction_db[prediction_id] = {
            "seed_id": seed,
            "member_ids": set(member_ids),
            "model_version": runtime.model_version,
            "created_at": time.time(),
        }
        _emit_event({
            "event": "team_generation",
            "prediction_id": prediction_id,
            "seed_id": seed,
            "member_ids": member_ids,
            "model_version": runtime.model_version,
            "team_size": len(member_ids),
            "nearest_distance": float(distances[0][0]) if len(distances[0]) else None,
        })
        outcome = "success"
        return TeamGenerateOutput(
            seed_id=seed,
            prediction_id=prediction_id,
            model_version=runtime.model_version,
            team_size=len(member_ids),
            member_ids=member_ids,
        )
    except TimeoutError:
        outcome = "timeouts"
        raise
    except Exception:
        if outcome == "errors":
            raise
        raise
    finally:
        latency_ms = (time.perf_counter() - started_at) * 1000.0
        metrics.record_generation(outcome, latency_ms)


@app.post("/team/feedback")
@validate()
def team_feedback(body: TeamFeedbackInput) -> TeamFeedbackOutput:
    metrics.count("feedback_requests_total")
    prediction = prediction_db.get(body.prediction_id)
    accepted = (
        body.seed_id in runtime.all_players
        and prediction is not None
        and prediction["seed_id"] == body.seed_id
        and body.member_id in prediction["member_ids"]
    )
    if accepted:
        metrics.count("feedback_accepted_total")
        if body.feedback < 0:
            exclude_db.setdefault(body.seed_id, set()).add(body.member_id)
    else:
        metrics.count("feedback_rejected_total")

    _emit_event({
        "event": "team_feedback",
        "prediction_id": body.prediction_id,
        "seed_id": body.seed_id,
        "member_id": body.member_id,
        "feedback": body.feedback,
        "accepted": accepted,
        "model_version": runtime.model_version,
    })
    return TeamFeedbackOutput(
        seed_id=body.seed_id,
        member_id=body.member_id,
        accepted=accepted,
        prediction_id=body.prediction_id,
    )


@app.get("/metrics")
def get_metrics() -> Any:
    return jsonify(metrics.snapshot(runtime.model_version))


@app.get("/health")
def health() -> Any:
    return jsonify({"status": "UP", "model_version": runtime.model_version, "players": len(runtime.all_players)})


@app.errorhandler(TeamException)
def handle_team_exception(exception: TeamException) -> tuple[Any, int]:
    return jsonify({"error": "Bad Request", "message": str(exception)}), 400


@app.errorhandler(TimeoutError)
def handle_timeout(exception: TimeoutError) -> tuple[Any, int]:
    return jsonify({"error": "Gateway Timeout", "message": str(exception)}), 504


@app.errorhandler(ValidationError)
def handle_validation_error(exception: ValidationError) -> tuple[Any, int]:
    metrics.count("invalid_request_total")
    return jsonify({"error": "Bad Request", "message": str(exception)}), 400


@app.errorhandler(Exception)
def handle_unexpected_exception(exception: Exception) -> tuple[Any, int]:
    logger.exception("Unhandled model service error")
    return jsonify({"error": "Internal Server Error", "message": "The model service could not complete the request"}), 500


class LLMInput(BaseModel):
    system_prompt: Optional[str] = None
    user_prompt: str


class LLMOutput(BaseModel):
    response: str


class LLMFeedbackInput(BaseModel):
    feedback: str


class LLMFeedbackOutput(BaseModel):
    system_prompt: Optional[str] = None
    user_prompt: str = ""


@app.post("/llm/generate")
@validate()
def generate_description(body: LLMInput) -> LLMOutput:
    return jsonify({"description": "Generated Description"}), 201


@app.post("/llm/feedback")
@validate()
def description_feedback(body: LLMFeedbackInput) -> LLMFeedbackOutput:
    return jsonify({"message": "Description feedback received"}), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", debug=True)
