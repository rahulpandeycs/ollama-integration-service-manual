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

The saved model is a scikit-learn `NearestNeighbors` model.

It is an unsupervised similarity model. It does not:

- Predict a class.
- Predict player performance.
- Calculate win probability.
- Optimize a baseball lineup.
- Learn from feedback automatically.

It finds players whose feature vectors are closest to the requested player or profile.

The model is serialized using `joblib`:

```python
joblib.dump(nn_model, "team_model.joblib")
```

At application startup, the model is loaded once:

```python
nn_model = joblib.load("team_model.joblib")
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

The training notebook performs these steps:

1. Load `a4a_model/player.csv` into a pandas DataFrame.
2. Create the birth-date, height, and weight features.
3. Normalize batting and throwing hand.
4. Fill missing numeric values.
5. Train a `NearestNeighbors` model using the five engineered features.
6. Save the model to `team_model.joblib`.
7. Save the enriched player data to `a4a_model/features_db.csv`.

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

Negative feedback adds the member to an in-memory exclusion set for the seed player.

Important limitations:

- Feedback is not persisted.
- Feedback is lost when the process restarts.
- Positive feedback currently does not improve ranking.
- The prediction ID is returned but is not currently used to validate the recommendation.
- The service does not verify that `member_id` belongs to the original prediction.

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

The runtime preprocessing is manually recreated in `a4a_model/server.py`. A future improvement would be to save a complete preprocessing and model pipeline so training and inference always use exactly the same transformations.

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
