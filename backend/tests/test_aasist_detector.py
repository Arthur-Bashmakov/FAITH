import numpy as np

from app.aasist_detector import INPUT_SAMPLES, _aggregate_spoof_scores, _prepare_windows


def test_short_recording_is_repeated_to_one_model_window():
    windows = _prepare_windows(np.ones(16_000), 16_000)

    assert windows.shape == (1, INPUT_SAMPLES)


def test_long_recording_covers_five_evenly_spaced_windows():
    samples = np.arange(140_000, dtype=np.float64)

    windows = _prepare_windows(samples, 16_000)

    assert windows.shape == (5, INPUT_SAMPLES)
    assert windows[0, 0] == 0
    assert windows[-1, -1] == samples[-1]


def test_lower_quartile_ignores_isolated_false_spoof_windows():
    phone_human_scores = np.array([0.9997, 0.0015, 0.0450, 0.9625, 0.0100])
    synthetic_scores = np.array([0.9994, 0.9992, 0.9930, 0.9619, 1.0000])

    assert _aggregate_spoof_scores(phone_human_scores) == 0.01
    assert _aggregate_spoof_scores(synthetic_scores) == 0.993
