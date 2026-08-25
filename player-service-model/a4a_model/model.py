from pathlib import Path

import joblib
import pandas as pd
from sklearn.neighbors import NearestNeighbors

#poetry run python a4a_model/model.py

FEATURES = ["birthZ", "heightZ", "weightZ", "batsN", "throwsN"]
N_NEIGHBORS = 25
MODEL_FILE = "team_model.joblib"
FEATURES_FILE = "features_db.csv"


def _zscore(values: pd.Series) -> pd.Series:
    """Calculate a population z-score and replace missing values with zero."""
    values = pd.to_numeric(values, errors="coerce")
    return ((values - values.mean()) / values.std(ddof=0)).fillna(0.0)


def train() -> None:
    """Train and save the player similarity model used by server.py."""
    data_dir = Path(__file__).resolve().parent
    player_file = data_dir / "player.csv"
    model_file = data_dir / MODEL_FILE
    features_file = data_dir / FEATURES_FILE

    df = pd.read_csv(player_file)

    # Match the feature engineering from train.ipynb.
    birth_fraction = (
        pd.to_numeric(df["birthYear"], errors="coerce")
        + (pd.to_numeric(df["birthMonth"], errors="coerce") - 1.0) / 12.0
        + (pd.to_numeric(df["birthDay"], errors="coerce") - 1.0) / 30.0
    )
    df["birthFraction"] = birth_fraction
    df["birthZ"] = _zscore(birth_fraction)
    df["weightZ"] = _zscore(df["weight"])
    df["heightZ"] = _zscore(df["height"])
    df["batsN"] = df["bats"].map({"R": 1.0, "L": -1.0}).fillna(0.0)
    df["throwsN"] = df["throws"].map({"R": 1.0, "L": -1.0}).fillna(0.0)

    model = NearestNeighbors(n_neighbors=N_NEIGHBORS)
    model.fit(df[FEATURES])

    joblib.dump(model, model_file)
    df.to_csv(features_file, index=False)

    print(f"Trained model with {len(df)} players")
    print(f"Saved model: {model_file}")
    print(f"Saved features: {features_file}")


if __name__ == "__main__":
    train()
