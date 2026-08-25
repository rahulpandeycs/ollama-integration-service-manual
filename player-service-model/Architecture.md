# A4A Model Architecture

## Overview

The A4A model is a Python Flask service that recommends baseball players similar to a selected player or hypothetical player profile.

The service runs independently from the Java Spring Boot application:

```text
Java Player Service :8080
        |
        | HTTP integration, if enabled
        v
Python ML Service :5000
        |
        +--> team_model.joblib
        +--> features_db.csv
```

The Java application does not load the `.joblib` file directly. The Python service owns model loading and inference.

## Model Type

The saved model is a versioned `SimilarityModel` bundle containing a scikit-learn `NearestNeighbors` estimator, its fitted preprocessing statistics, the player ID ordering, configuration, training data hash, and evaluation metadata.

It is an unsupervised similarity model. It does not:

- Predict a class.
- Predict player performance.
- Calculate win probability.
- Optimize a baseball lineup.
- Learn from feedback automatically.

It finds players whose feature vectors are closest to the requested player or profile.

The model bundle is serialized using `joblib`:

```python
joblib.dump(similarity_model, "team_model.joblib")
```

At application startup, the model bundle is loaded once:

```python
similarity_model = joblib.load("team_model.joblib")
```

## Training Data

The training job reads:

```text
player.csv
```

The training process creates engineered features and saves:

```text
team_model.joblib
features_db.csv
```

`a4a_model/features_db.csv` contains the original player data plus the engineered feature columns used during inference.

## Feature Engineering

The model uses these five features:

```python
features = [
    "birthZ",
    "heightZ",
    "weightZ",
    "batsN",
    "throwsN"
]
```

### `birthZ`

The birth date is converted into a numeric value using:

```text
birthYear + (birthMonth - 1) / 12 + (birthDay - 1) / 30
```

The result is standardized using a z-score.

### `heightZ`

Player height is standardized using a z-score.

### `weightZ`

Player weight is standardized using a z-score.

### `batsN`

Batting hand is converted to a numeric value:

```text
R → 1.0
L → -1.0
Unknown → 0.0
```

### `throwsN`

Throwing hand is converted similarly:

```text
R → 1.0
L → -1.0
Unknown → 0.0
```

Missing numeric feature values are replaced with `0.0`, which represents the mean after standardization.

## Training Process

The reproducible `a4a_model.model` training module, invoked through the stable `a4a_model.train` entrypoint, performs these steps:

1. Load `a4a_model/player.csv` into a pandas DataFrame.
2. Create the birth-date, height, and weight features.
3. Normalize batting and throwing hand.
4. Fill missing numeric values.
5. Train a `NearestNeighbors` model using the five engineered features.
6. Compare a bounded set of distance metrics and neighbor counts automatically.
7. Save the selected model bundle to `team_model.joblib`.
8. Save the enriched player data and metadata/evaluation report to `a4a_model/features_db.csv` and `a4a_model/model_metrics.json`.

The model is configured with:

```python
NearestNeighbors(n_neighbors=25)
```

The default distance metric is Euclidean distance.

A smaller distance means that two players are more similar according to the selected features.

## Runtime Inference

The service supports two types of input.

### Seed player input

The caller provides a player ID:

```json
{
  "seed_id": "aaronha01",
  "team_size": 5
}
```

The service:

1. Finds the player in `a4a_model/features_db.csv`.
2. Extracts the five model features.
3. Finds the nearest players.
4. Converts result indexes back to player IDs.
5. Applies any feedback exclusions.
6. Returns the recommended member IDs.

### Feature-based input

The caller provides a hypothetical player profile:

```json
{
  "features": {
    "birth_year": 1934,
    "height": 72,
    "weight": 180,
    "bats": "R",
    "throws": "R"
  },
  "team_size": 5
}
```

The service converts the supplied values into the same feature representation used during training and performs nearest-neighbor search.

The request must provide either `seed_id` or `features`.

## Exposed APIs

The Flask service listens on port `5000`.

### Generate a Team

```text
POST /team/generate
```

#### Request using a seed player

```bash
curl \
  -H "Content-Type: application/json" \
  -d '{
    "seed_id": "abbotji01",
    "team_size": 10
  }' \
  http://127.0.0.1:5000/team/generate
```

#### Request using raw features

```bash
curl \
  -H "Content-Type: application/json" \
  -d '{
    "features": {
      "birth_year": 1934,
      "height": 72,
      "weight": 180,
      "bats": "R",
      "throws": "R"
    },
    "team_size": 10
  }' \
  http://127.0.0.1:5000/team/generate
```

#### Expected response

```json
{
  "seed_id": "abbotji01",
  "prediction_id": "38f5f02f-b1be-4282-8d0e-865b3995d50a",
  "model_version": "similarity-...",
  "team_size": 10,
  "member_ids": [
    "abbotji01",
    "combspa01",
    "maurero01",
    "cummijo01",
    "flemida01",
    "macdobo01",
    "eddych01",
    "morriha02",
    "mcgrifr01",
    "blossgr01"
  ]
}
```

`prediction_id` identifies the generated recommendation and can be sent with feedback.

The current implementation may include the seed player in `member_ids`.

### Submit Team Feedback

```text
POST /team/feedback
```

#### Request

```bash
curl \
  -H "Content-Type: application/json" \
  -d '{
    "seed_id": "abbotji01",
    "member_id": "maurero01",
    "feedback": -1,
    "prediction_id": "38f5f02f-b1be-4282-8d0e-865b3995d50a"
  }' \
  http://127.0.0.1:5000/team/feedback
```

The `feedback` value must be:

```text
-1 → reject member
1  → accept member
```

#### Expected response

```json
{
  "seed_id": "abbotji01",
  "prediction_id": "38f5f02f-b1be-4282-8d0e-865b3995d50a",
  "member_id": "maurero01",
  "accepted": true
}
```

Negative feedback adds the member to an in-memory exclusion set for the seed player. Feedback is accepted only when the seed and member belong to the referenced prediction. Generation and feedback events are emitted as structured JSON logs and can also be appended to a file with `FEEDBACK_LOG_PATH`.

Important limitations:

- Feedback is not persisted.
- Feedback is lost when the process restarts.
- Positive feedback currently does not improve ranking.
- Prediction records and exclusions are process-local and are lost when the service restarts.
- A durable event store is required for long-term feedback-based quality monitoring.

### Runtime monitoring

`GET /metrics` returns request counters, error and timeout counts, feedback outcomes, average generation latency, and the active model version. `GET /health` reports service status, model version, and the loaded player count.

The model service also logs each recommendation's `prediction_id`, seed, selected members, model version, team size, and nearest-neighbor distance. These events support offline monitoring of acceptance rate, negative feedback rate, latency, and recommendation-distance changes.

The `a4a_model.monitor` command compares a current player CSV with the training reference using feature-level population stability index (PSI) and missing-rate deltas. It produces `stable`, `watch`, or `drift` statuses that can be scheduled as a batch monitoring job.

### Model evaluation limits

The current `player.csv` does not contain labeled similar-player relationships or historical teams. The training command therefore reports coverage and nearest-neighbor distance diagnostics as proxy metrics. Precision, recall, MAP, or NDCG should only be reported after adding a reviewed or historical evaluation set.

### Generate Description Placeholder

```text
POST /llm/generate
```

Expected request shape:

```json
{
  "system_prompt": "You are a baseball assistant.",
  "user_prompt": "Describe this player."
}
```

Current behavior is a placeholder. It does not call a language model and returns a static response similar to:

```json
{
  "description": "Generated Description"
}
```

This endpoint is not connected to the Java service or Ollama.

### LLM Feedback Placeholder

```text
POST /llm/feedback
```

Expected request shape:

```json
{
  "feedback": "The description was useful."
}
```

Current behavior is also a placeholder and returns a static acknowledgement.

## Failure Simulation

The current `/team/generate` endpoint intentionally simulates unreliable behavior:

- Approximately 1% of requests raise a timeout error.
- Approximately 1% of requests sleep for six seconds.
- Other failures may occur if the seed player or features are invalid.

This is useful for testing client timeout, retry, and fallback behavior.

## Model and Data Limitations

The current model does not use:

- Birth country.
- Player position.
- Batting performance.
- Pitching performance.
- Team membership.
- Career success.
- Player statistics beyond height and weight.

Therefore, the model recommends physically and biographically similar players, not necessarily better players or complementary teammates.

Training and runtime inference use the same serialized `FeaturePreprocessor` inside the model bundle. This prevents the training notebook and API from silently drifting apart. The original bare joblib estimator remains readable as a legacy fallback, but new artifacts should be generated with `python -m a4a_model.train` and loaded through the package entrypoint.

The model artifact should only be loaded from a trusted source because `joblib` uses Python object serialization.

## Possible Future Features

The current model can support:

- Similar-player search.
- Scouting recommendations.
- Hypothetical player profile search.
- Similarity distances and explanations.
- Batch recommendations for multiple seed players.
- User-specific exclusions.
- Persistent recommendation history.
- Feedback-based re-ranking.

The following features would require new data or retraining:

- Country-aware recommendations.
- Position-balanced teams.
- Performance-based recommendations.
- Career outcome prediction.
- Win probability prediction.

## Running the Service

Build the container:

```bash
docker build -t a4a_model player-service-model
```

Run the service:

```bash
docker run -d -p 5000:5000 a4a_model
```

The container starts `a4a_model/server.py`, which loads:

```text
team_model.joblib
features_db.csv
```

Both files must be available in the model working directory.

## Architecture Summary

The model service is a nearest-neighbor recommendation service:

```text
Request
  |
  +--> seed_id or raw features
  |
Feature preparation
  |
NearestNeighbors.kneighbors(...)
  |
Map row indexes to player IDs
  |
Apply feedback exclusions
  |
Return prediction_id and member_ids
```
