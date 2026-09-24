"""Summarize a CookX diagnostic export; never infer hardware accuracy from a log."""
import argparse
import collections
import json
from pathlib import Path


def summarize(data):
    if data.get("schemaVersion") != 1:
        raise ValueError("Unsupported diagnostic version")
    events = data.get("records", [])
    samples = [event["data"] for event in events if event.get("type") == "sample"]
    return {
        "source": data.get("source"),
        "event_counts": dict(collections.Counter(event.get("type") for event in events)),
        "protocol_counts": dict(collections.Counter(str(sample.get("protocolVersion")) for sample in samples)),
        "invalid_samples": sum(sample.get("valid") is False for sample in samples),
        "discontinuities": sum(bool(sample.get("discontinuity")) for sample in samples),
        "info": data.get("info", {}),
        "acceptance": "Pending device testing; no accuracy claim",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    print(json.dumps(summarize(json.loads(args.path.read_text(encoding="utf-8"))), ensure_ascii=False, indent=2))
