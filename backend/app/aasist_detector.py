"""CPU inference adapter for the official pretrained AASIST-L model.

Upstream project: https://github.com/clovaai/aasist
Copyright (c) 2021-present NAVER Corp., MIT license.
"""

from functools import lru_cache
from pathlib import Path
import sys

import numpy as np


AASIST_ROOT = Path("/opt/aasist")
WEIGHTS_PATH = AASIST_ROOT / "models" / "weights" / "AASIST-L.pth"
INPUT_SAMPLES = 64_600
MODEL_CONFIG = {
    "architecture": "AASIST",
    "nb_samp": INPUT_SAMPLES,
    "first_conv": 128,
    "filts": [70, [1, 32], [32, 32], [32, 24], [24, 24]],
    "gat_dims": [24, 32],
    "pool_ratios": [0.4, 0.5, 0.7, 0.5],
    "temperatures": [2.0, 2.0, 100.0, 100.0],
}


def _repeat_or_crop(samples: np.ndarray) -> np.ndarray:
    if samples.size >= INPUT_SAMPLES:
        return samples[:INPUT_SAMPLES]
    repeats = int(np.ceil(INPUT_SAMPLES / samples.size))
    return np.tile(samples, repeats)[:INPUT_SAMPLES]


def _prepare_windows(samples: np.ndarray, sample_rate: int) -> np.ndarray:
    """Build up to three speech-heavy windows from the complete recording."""
    resampled = _resample(samples, sample_rate)
    if resampled.size <= INPUT_SAMPLES:
        return _repeat_or_crop(resampled)[None, :]

    last_start = resampled.size - INPUT_SAMPLES
    candidate_starts = np.linspace(0, last_start, num=5, dtype=int)
    candidates = [resampled[start : start + INPUT_SAMPLES] for start in candidate_starts]
    energies = [float(np.mean(window * window)) for window in candidates]
    selected = sorted(range(len(candidates)), key=energies.__getitem__, reverse=True)[:3]
    return np.stack([candidates[index] for index in selected])


def _resample(samples: np.ndarray, source_rate: int) -> np.ndarray:
    if source_rate == 16_000:
        return samples
    output_size = max(1, round(samples.size * 16_000 / source_rate))
    source_axis = np.linspace(0.0, 1.0, samples.size, endpoint=False)
    output_axis = np.linspace(0.0, 1.0, output_size, endpoint=False)
    return np.interp(output_axis, source_axis, samples)


@lru_cache(maxsize=1)
def _load_model():
    import torch

    if not WEIGHTS_PATH.is_file():
        raise RuntimeError(f"AASIST weights were not found at {WEIGHTS_PATH}")
    sys.path.insert(0, str(AASIST_ROOT))
    from models.AASIST import Model

    model = Model(MODEL_CONFIG)
    state = torch.load(WEIGHTS_PATH, map_location="cpu", weights_only=True)
    model.load_state_dict(state)
    model.eval()
    return model


def synthetic_probability(samples: np.ndarray, sample_rate: int) -> float:
    """Return AASIST-L softmax score for the spoof class."""
    import torch

    prepared = _prepare_windows(samples, sample_rate).astype(np.float32)
    batch = torch.from_numpy(prepared)
    model = _load_model()
    with torch.inference_mode():
        _, logits = model(batch)
        probabilities = torch.softmax(logits, dim=1)
    # AASIST labels: 0 = spoof, 1 = bona fide.
    # A median over several speech-heavy windows is less sensitive to a click,
    # leading silence, or microphone startup noise in phone recordings.
    return float(torch.median(probabilities[:, 0]).item())
