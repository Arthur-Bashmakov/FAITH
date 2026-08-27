from __future__ import annotations

from dataclasses import asdict, dataclass
from math import inf
from typing import Iterable


@dataclass(frozen=True)
class LabeledScore:
    file_name: str
    label: str
    score: float


@dataclass(frozen=True)
class EvaluationMetrics:
    total: int
    decided: int
    uncertain: int
    true_human: int
    true_synthetic: int
    false_human: int
    false_synthetic: int
    accuracy_on_decided: float | None
    synthetic_precision: float | None
    synthetic_recall: float | None
    synthetic_f1: float | None
    uncertain_rate: float

    def to_dict(self) -> dict[str, int | float | None]:
        return asdict(self)


def verdict_for_score(score: float, human_max: float, synthetic_min: float) -> str:
    if not 0.0 <= human_max < synthetic_min <= 1.0:
        raise ValueError("Thresholds must satisfy 0 <= human_max < synthetic_min <= 1")
    if score <= human_max:
        return "human"
    if score >= synthetic_min:
        return "synthetic"
    return "uncertain"


def evaluate_scores(
    samples: Iterable[LabeledScore],
    human_max: float,
    synthetic_min: float,
) -> EvaluationMetrics:
    values = list(samples)
    if any(item.label not in {"human", "synthetic"} for item in values):
        raise ValueError("Labels must be 'human' or 'synthetic'")
    if any(not 0.0 <= item.score <= 1.0 for item in values):
        raise ValueError("Scores must be between 0 and 1")

    true_human = true_synthetic = false_human = false_synthetic = uncertain = 0
    for item in values:
        verdict = verdict_for_score(item.score, human_max, synthetic_min)
        if verdict == "uncertain":
            uncertain += 1
        elif verdict == item.label == "human":
            true_human += 1
        elif verdict == item.label == "synthetic":
            true_synthetic += 1
        elif verdict == "human":
            false_human += 1
        else:
            false_synthetic += 1

    decided = len(values) - uncertain
    correct = true_human + true_synthetic
    predicted_synthetic = true_synthetic + false_synthetic
    actual_synthetic_decided = true_synthetic + false_human
    precision = _ratio(true_synthetic, predicted_synthetic)
    recall = _ratio(true_synthetic, actual_synthetic_decided)
    f1 = None if precision is None or recall is None or precision + recall == 0 else 2 * precision * recall / (precision + recall)
    return EvaluationMetrics(
        total=len(values),
        decided=decided,
        uncertain=uncertain,
        true_human=true_human,
        true_synthetic=true_synthetic,
        false_human=false_human,
        false_synthetic=false_synthetic,
        accuracy_on_decided=_ratio(correct, decided),
        synthetic_precision=precision,
        synthetic_recall=recall,
        synthetic_f1=f1,
        uncertain_rate=_ratio(uncertain, len(values)) or 0.0,
    )


def recommend_thresholds(samples: Iterable[LabeledScore]) -> tuple[float, float, EvaluationMetrics]:
    """Find conservative thresholds with the fewest wrong confident verdicts.

    The objective penalizes a wrong verdict much more strongly than an uncertain
    result. This is appropriate for a demonstrator that must not present an
    uncalibrated model score as a guaranteed fact.
    """
    values = list(samples)
    if not values:
        raise ValueError("At least one labeled score is required")
    candidates = sorted({0.0, 1.0, *(round(item.score, 4) for item in values)})
    best: tuple[float, float, float, float, EvaluationMetrics] | None = None
    for human_max in candidates:
        for synthetic_min in candidates:
            if human_max >= synthetic_min:
                continue
            metrics = evaluate_scores(values, human_max, synthetic_min)
            wrong = metrics.false_human + metrics.false_synthetic
            objective = wrong * 10 + metrics.uncertain
            accuracy = metrics.accuracy_on_decided if metrics.accuracy_on_decided is not None else -inf
            candidate = (objective, -accuracy, human_max, synthetic_min, metrics)
            if best is None or candidate[:4] < best[:4]:
                best = candidate
    if best is None:
        raise ValueError("Could not derive valid thresholds")
    return best[2], best[3], best[4]


def _ratio(numerator: int, denominator: int) -> float | None:
    return None if denominator == 0 else numerator / denominator
