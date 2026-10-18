#!/usr/bin/env python3
"""Validate real-game AI war-pair observations, one record per simulated turn."""
import argparse
import re
from pathlib import Path

PATTERN = re.compile(r"SIM diplomacy turn=(\d+) warPairs=(\d+)")
TRANSITIONS = re.compile(r"SIM transitions turn=(\d+) warsStarted=(\d+) warsEnded=(\d+)")

def validate(content: str, expected_turns: int) -> None:
    observations = [(int(t), int(w)) for t, w in PATTERN.findall(content)]
    actual = [t for t, _ in observations]
    expected = list(range(1, expected_turns + 1))
    if actual != expected:
        raise ValueError(f"war telemetry missing, duplicated or out of order: expected {expected_turns} contiguous records, got {len(actual)}")
    if any(w < 0 for _, w in observations):
        raise ValueError("negative war-pair count")
    transitions = [(int(t), int(start), int(end)) for t, start, end in TRANSITIONS.findall(content)]
    if [t for t, _, _ in transitions] != expected:
        raise ValueError("war transition telemetry missing, duplicated or out of order")
    previous = 0
    for (turn, wars), (_, started, ended) in zip(observations, transitions):
        if wars != previous + started - ended:
            raise ValueError(f"war-count conservation failed on turn {turn}: {previous} + {started} - {ended} != {wars}")
        previous = wars
    print(f"Validated {expected_turns} contiguous war-state observations; max simultaneous pairs={max(w for _, w in observations)}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--expected-turns", type=int, required=True)
    args = parser.parse_args()
    validate(args.log.read_text(encoding="utf-8", errors="replace"), args.expected_turns)
