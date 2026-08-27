import numpy as np

from app.aasist_detector import INPUT_SAMPLES, _prepare_windows


def test_short_recording_is_repeated_to_one_model_window():
    windows = _prepare_windows(np.ones(16_000), 16_000)

    assert windows.shape == (1, INPUT_SAMPLES)


def test_long_recording_selects_three_high_energy_windows():
    samples = np.concatenate([np.zeros(70_000), np.ones(70_000)])

    windows = _prepare_windows(samples, 16_000)

    assert windows.shape == (3, INPUT_SAMPLES)
    assert float(windows[0].mean()) > 0.9
