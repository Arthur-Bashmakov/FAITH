from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.audio import HUMAN_MAX_SCORE, SUPPORTED_AUDIO_EXTENSIONS, SYNTHETIC_MIN_SCORE, analyze_audio
from app.evaluation import LabeledScore, evaluate_scores, recommend_thresholds


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate FAITH on a labeled local audio set")
    parser.add_argument("dataset", type=Path, help="Directory containing human/ and synthetic/")
    parser.add_argument("--output", type=Path, default=Path("evaluation-report.json"))
    parser.add_argument("--scores", type=Path, default=Path("evaluation-scores.csv"))
    args = parser.parse_args()

    samples: list[LabeledScore] = []
    failures: list[dict[str, str]] = []
    for label in ("human", "synthetic"):
        directory = args.dataset / label
        if not directory.is_dir():
            parser.error(f"Missing directory: {directory}")
        for path in sorted(directory.rglob("*")):
            if path.suffix.lower() not in SUPPORTED_AUDIO_EXTENSIONS:
                continue
            try:
                prediction = analyze_audio(path.read_bytes(), path.name)
                samples.append(LabeledScore(str(path.relative_to(args.dataset)), label, prediction.probability))
                print(f"{label:9} {prediction.probability:0.4f} {path.name}", flush=True)
            except Exception as exc:  # report a broken sample without losing the complete run
                failures.append({"file": str(path), "error": str(exc)})

    if not samples:
        parser.error("No supported audio files were found")
    current = evaluate_scores(samples, HUMAN_MAX_SCORE, SYNTHETIC_MIN_SCORE)
    suggested_human, suggested_synthetic, suggested = recommend_thresholds(samples)
    report = {
        "sample_count": len(samples),
        "failures": failures,
        "current": {
            "human_max": HUMAN_MAX_SCORE,
            "synthetic_min": SYNTHETIC_MIN_SCORE,
            "metrics": current.to_dict(),
        },
        "suggested_on_this_dataset": {
            "human_max": suggested_human,
            "synthetic_min": suggested_synthetic,
            "metrics": suggested.to_dict(),
            "warning": "Validate these thresholds on a separate dataset before changing production settings.",
        },
    }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    with args.scores.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=["file_name", "label", "score"])
        writer.writeheader()
        writer.writerows({"file_name": item.file_name, "label": item.label, "score": item.score} for item in samples)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
