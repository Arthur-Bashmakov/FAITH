from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import wave

import numpy as np

from app.aasist_detector import synthetic_probability


MODEL_VERSION = "aasist-l-asvspoof2019-uniform-q25-v5"
HUMAN_MAX_SCORE = 0.40
SYNTHETIC_MIN_SCORE = 0.95
SUPPORTED_AUDIO_EXTENSIONS = {".wav", ".mp3", ".m4a", ".aac", ".ogg", ".flac"}


class InvalidAudioError(ValueError):
    pass


@dataclass(frozen=True)
class Prediction:
    probability: float
    verdict: str
    model_version: str = MODEL_VERSION


def classify_score(probability: float) -> str:
    """Map the uncalibrated model score to a conservative user-facing zone."""
    if probability >= SYNTHETIC_MIN_SCORE:
        return "synthetic"
    if probability <= HUMAN_MAX_SCORE:
        return "human"
    return "uncertain"


def _read_wav(content: bytes) -> tuple[np.ndarray, int]:
    """Decode PCM WAV without invoking external codecs."""
    try:
        with wave.open(BytesIO(content), "rb") as source:
            channels = source.getnchannels()
            sample_width = source.getsampwidth()
            sample_rate = source.getframerate()
            frame_count = source.getnframes()
            raw = source.readframes(frame_count)
    except (wave.Error, EOFError) as exc:
        raise InvalidAudioError("Файл должен быть корректной PCM WAV-записью") from exc

    if sample_width not in (1, 2, 4) or sample_rate < 8_000:
        raise InvalidAudioError("Неподдерживаемый формат WAV")

    dtype = {1: np.uint8, 2: np.int16, 4: np.int32}[sample_width]
    samples = np.frombuffer(raw, dtype=dtype).astype(np.float64)
    if sample_width == 1:
        samples = (samples - 128.0) / 128.0
    else:
        samples /= float(2 ** (sample_width * 8 - 1))
    if channels > 1:
        samples = samples.reshape(-1, channels).mean(axis=1)
    if samples.size < sample_rate // 2:
        raise InvalidAudioError("Запись должна быть не короче 0,5 секунды")
    return samples, sample_rate


def _decode_audio(content: bytes, file_name: str) -> bytes:
    suffix = Path(file_name).suffix.lower()
    if suffix not in SUPPORTED_AUDIO_EXTENSIONS:
        raise InvalidAudioError("Неподдерживаемый формат аудиофайла")
    if suffix == ".wav":
        return content

    with TemporaryDirectory(prefix="faith-audio-") as directory:
        source = Path(directory) / f"source{suffix}"
        target = Path(directory) / "decoded.wav"
        source.write_bytes(content)
        try:
            completed = subprocess.run(
                [
                    "ffmpeg", "-v", "error", "-y", "-i", str(source),
                    "-ac", "1", "-ar", "16000", "-acodec", "pcm_s16le", str(target),
                ],
                capture_output=True,
                check=False,
                timeout=30,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise InvalidAudioError("Не удалось декодировать аудиофайл") from exc
        if completed.returncode != 0 or not target.exists():
            raise InvalidAudioError("Аудиофайл повреждён или имеет неподдерживаемый формат")
        return target.read_bytes()


def analyze_wav(content: bytes) -> Prediction:
    """Validate speech-like audio and run the pretrained AASIST-L model."""
    samples, sample_rate = _read_wav(content)
    samples = samples - samples.mean()
    rms = float(np.sqrt(np.mean(samples**2)) + 1e-12)
    # A distant phone microphone often produces valid speech below the old
    # 0.005 threshold. Reject only an effectively empty signal, then apply a
    # bounded gain. The cap prevents background noise from being amplified
    # without limit and avoids aggressive denoising that could erase the
    # artefacts the anti-spoofing model is supposed to detect.
    if rms < 0.0015:
        raise InvalidAudioError("В записи не обнаружен сигнал достаточной громкости")
    gain = min(6.0, max(1.0, 0.025 / rms))
    samples = np.clip(samples * gain, -0.98, 0.98)
    rms = float(np.sqrt(np.mean(samples**2)) + 1e-12)
    normalized = samples / rms

    spectrum = np.abs(np.fft.rfft(normalized)) + 1e-12
    spectral_concentration = float(np.max(spectrum) / np.sum(spectrum))
    if spectral_concentration > 0.35:
        raise InvalidAudioError("В записи обнаружен тональный сигнал, но не распознана речь")
    probability = synthetic_probability(samples, sample_rate)
    probability = min(0.99, max(0.01, probability))
    verdict = classify_score(probability)
    return Prediction(probability=round(probability, 4), verdict=verdict)


def analyze_audio(content: bytes, file_name: str) -> Prediction:
    """Decode a supported audio container and analyze normalized PCM WAV data."""
    return analyze_wav(_decode_audio(content, file_name))
