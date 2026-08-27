from io import BytesIO
import wave

import numpy as np

from app import audio
from app.audio import InvalidAudioError, analyze_audio, analyze_wav, classify_score


def make_wav(
    seconds: float = 1.0,
    sample_rate: int = 16_000,
    pure_tone: bool = False,
    volume: float = 1.0,
) -> bytes:
    time = np.arange(int(seconds * sample_rate)) / sample_rate
    if pure_tone:
        signal = 0.25 * np.sin(2 * np.pi * 330 * time)
    else:
        rng = np.random.default_rng(42)
        envelope = 0.55 + 0.35 * np.sin(2 * np.pi * 3.2 * time)
        signal = envelope * (
            0.14 * np.sin(2 * np.pi * 180 * time)
            + 0.08 * np.sin(2 * np.pi * 430 * time)
            + 0.04 * rng.normal(size=time.size)
        )
    signal = (signal * volume * 32767).astype(np.int16)
    output = BytesIO()
    with wave.open(output, "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(sample_rate)
        target.writeframes(signal.tobytes())
    return output.getvalue()


def test_analysis_returns_bounded_probability(monkeypatch):
    monkeypatch.setattr(audio, "synthetic_probability", lambda samples, sample_rate: 0.23)
    prediction = analyze_wav(make_wav())
    assert 0.0 <= prediction.probability <= 1.0
    assert prediction.verdict in {"human", "uncertain", "synthetic"}


def test_quiet_speech_is_amplified_before_inference(monkeypatch):
    observed_rms = []

    def detector(samples, sample_rate):
        observed_rms.append(float(np.sqrt(np.mean(samples**2))))
        return 0.23

    monkeypatch.setattr(audio, "synthetic_probability", detector)
    prediction = analyze_wav(make_wav(volume=0.03))
    assert prediction.verdict == "human"
    assert observed_rms[0] >= 0.01


def test_detector_score_maps_to_synthetic_verdict(monkeypatch):
    monkeypatch.setattr(audio, "synthetic_probability", lambda samples, sample_rate: 0.99)
    prediction = analyze_wav(make_wav())
    assert prediction.probability == 0.99
    assert prediction.verdict == "synthetic"


def test_ambiguous_score_maps_to_uncertain_verdict(monkeypatch):
    monkeypatch.setattr(audio, "synthetic_probability", lambda samples, sample_rate: 0.89)
    prediction = analyze_wav(make_wav())
    assert prediction.probability == 0.89
    assert prediction.verdict == "uncertain"


def test_conservative_score_boundaries():
    assert classify_score(0.40) == "human"
    assert classify_score(0.41) == "uncertain"
    assert classify_score(0.94) == "uncertain"
    assert classify_score(0.95) == "synthetic"


def test_short_audio_is_rejected():
    try:
        analyze_wav(make_wav(seconds=0.1))
    except InvalidAudioError:
        return
    raise AssertionError("Short audio must be rejected")


def test_pure_tone_is_rejected_as_non_speech():
    try:
        analyze_wav(make_wav(pure_tone=True))
    except InvalidAudioError as exc:
        assert "не распознана речь" in str(exc)
        return
    raise AssertionError("A pure tone must not be classified as human speech")


def test_unsupported_extension_is_rejected():
    try:
        analyze_audio(make_wav(), "audio.txt")
    except InvalidAudioError:
        return
    raise AssertionError("Unsupported file extensions must be rejected")
