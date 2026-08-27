import pytest

from app.evaluation import LabeledScore, evaluate_scores, recommend_thresholds, verdict_for_score


SAMPLES = [
    LabeledScore("h1.wav", "human", 0.05),
    LabeledScore("h2.wav", "human", 0.30),
    LabeledScore("s1.wav", "synthetic", 0.97),
    LabeledScore("s2.wav", "synthetic", 0.99),
    LabeledScore("hard.wav", "synthetic", 0.70),
]


def test_evaluate_scores_keeps_middle_zone_uncertain():
    result = evaluate_scores(SAMPLES, human_max=0.40, synthetic_min=0.95)

    assert result.total == 5
    assert result.decided == 4
    assert result.uncertain == 1
    assert result.accuracy_on_decided == 1.0
    assert result.uncertain_rate == pytest.approx(0.2)


def test_evaluate_scores_counts_confident_errors():
    samples = [
        LabeledScore("human.wav", "human", 0.98),
        LabeledScore("synthetic.wav", "synthetic", 0.10),
    ]
    result = evaluate_scores(samples, human_max=0.40, synthetic_min=0.95)

    assert result.false_synthetic == 1
    assert result.false_human == 1
    assert result.accuracy_on_decided == 0.0


def test_recommend_thresholds_returns_valid_zones():
    human_max, synthetic_min, metrics = recommend_thresholds(SAMPLES)

    assert 0 <= human_max < synthetic_min <= 1
    assert metrics.false_human + metrics.false_synthetic == 0


def test_rejects_invalid_threshold_order():
    with pytest.raises(ValueError):
        verdict_for_score(0.5, human_max=0.8, synthetic_min=0.7)
