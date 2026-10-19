#!/usr/bin/env python3
"""Validate headless AI turn traces and write a portable JSON summary."""
import argparse
import json
import math
import re
from pathlib import Path

TURN = re.compile(r"^OJH turn (\d+) ([0-9]+(?:\.[0-9]+)?)$")
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("trace", type=Path)
    parser.add_argument("--expected-turns", type=int, required=True)
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    lines = args.trace.read_text(encoding="utf-8").splitlines()
    times = []
    for line in lines:
        match = TURN.fullmatch(line.strip())
        if match:
            turn, duration = int(match[1]), float(match[2])
            if turn != len(times) + 1:
                raise SystemExit(f"Invalid turn order: expected {len(times)+1}, got {turn}")
            if not math.isfinite(duration) or duration < 0:
                raise SystemExit(f"Invalid duration on turn {turn}: {duration}")
            times.append(duration)
    if "OJH ready" not in lines:
        raise SystemExit("Missing ready marker")
    if len(times) != args.expected_turns:
        raise SystemExit(f"Expected {args.expected_turns} turns, found {len(times)}")
    result = {
        "schema_version": 1,
        "scenario": "ai-turn-completion",
        "seed": args.seed,
        "expected_turns": args.expected_turns,
        "completed_turns": len(times),
        "passed": True,
        "total_turn_seconds": round(sum(times), 6),
        "mean_turn_seconds": round(sum(times) / len(times), 6),
        "slowest_turn_seconds": max(times),
    }
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))

if __name__ == "__main__":
    main()
