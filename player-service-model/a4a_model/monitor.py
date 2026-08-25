"""Offline feature-drift monitoring for the similarity model."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from .model import MODEL_FEATURES, FeaturePreprocessor, raw_feature_frame


def _bin_edges(values: np.ndarray, bin_count: int) -> np.ndarray:
    quantiles = np.quantile(values, np.linspace(0.0, 1.0, bin_count + 1))
    internal_edges = np.unique(quantiles[1:-1])
    return np.concatenate(([-np.inf], internal_edges, [np.inf]))


def _population_stability_index(reference: np.ndarray, current: np.ndarray, bin_count: int = 10) -> float:
    edges = _bin_edges(reference, bin_count)
    reference_counts, _ = np.histogram(reference, bins=edges)
    current_counts, _ = np.histogram(current, bins=edges)
    epsilon = 1e-6
    reference_distribution = np.maximum(reference_counts / len(reference), epsilon)
    current_distribution = np.maximum(current_counts / len(current), epsilon)
    return float(np.sum((current_distribution - reference_distribution) * np.log(current_distribution / reference_distribution)))


def _status(psi: float) -> str:
    if psi >= 0.2:
        return "drift"
    if psi >= 0.1:
        return "watch"
    return "stable"


def calculate_drift(reference: pd.DataFrame, current: pd.DataFrame) -> dict[str, Any]:
    """Compare feature distributions using the reference training data."""

    preprocessor = FeaturePreprocessor.fit(reference)
    reference_matrix = preprocessor.transform_dataframe(reference)
    current_matrix = preprocessor.transform_dataframe(current)
    reference_raw = raw_feature_frame(reference)
    current_raw = raw_feature_frame(current)
    reports: dict[str, Any] = {}

    for index, feature in enumerate(MODEL_FEATURES):
        reference_values = reference_matrix[:, index]
        current_values = current_matrix[:, index]
        psi = _population_stability_index(reference_values, current_values)
        raw_column = {"birthZ": "birthFraction", "heightZ": "height", "weightZ": "weight"}.get(feature)
        if raw_column:
            reference_missing_rate = float(reference_raw[raw_column].isna().mean())
            current_missing_rate = float(current_raw[raw_column].isna().mean())
        else:
            reference_missing_rate = 0.0
            current_missing_rate = 0.0
        reports[feature] = {
            "psi": round(psi, 6),
            "status": _status(psi),
            "reference_missing_rate": round(reference_missing_rate, 6),
            "current_missing_rate": round(current_missing_rate, 6),
            "missing_rate_delta": round(current_missing_rate - reference_missing_rate, 6),
        }

    average_psi = float(np.mean([report["psi"] for report in reports.values()])) if reports else 0.0
    return {
        "reference_rows": len(reference),
        "current_rows": len(current),
        "overall_psi": round(average_psi, 6),
        "overall_status": _status(average_psi),
        "features": reports,
    }


def _cli() -> None:
    parser = argparse.ArgumentParser(description="Compare a current player CSV with the model reference data")
    parser.add_argument("--reference", required=True, help="Training/reference CSV")
    parser.add_argument("--current", required=True, help="Current production CSV")
    parser.add_argument("--output", default="drift_report.json", help="Output JSON report")
    args = parser.parse_args()
    report = calculate_drift(pd.read_csv(args.reference), pd.read_csv(args.current))
    Path(args.output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    _cli()
