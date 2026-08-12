#!/usr/bin/env python3
"""Validate MedAgentGuard AI contribution receipts using only the Python standard library."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(os.environ.get("MEDAGENTGUARD_ROOT", Path(__file__).resolve().parents[1])).resolve()
RECEIPT_DIR = ROOT / ".ai" / "receipts"
BOOTSTRAP_RECEIPT = RECEIPT_DIR / "0000-bootstrap.json"


class ReceiptError(ValueError):
    pass


def require_string(data: dict[str, Any], key: str, location: str) -> str:
    value = data.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ReceiptError(f"{location}.{key} must be a non-empty string")
    return value.strip()


def require_object(data: dict[str, Any], key: str, location: str) -> dict[str, Any]:
    value = data.get(key)
    if not isinstance(value, dict):
        raise ReceiptError(f"{location}.{key} must be an object")
    return value


def require_non_empty_list(data: dict[str, Any], key: str, location: str) -> list[Any]:
    value = data.get(key)
    if not isinstance(value, list) or not value:
        raise ReceiptError(f"{location}.{key} must be a non-empty array")
    return value


def validate_receipt(path: Path) -> None:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ReceiptError(f"cannot read valid JSON: {exc}") from exc

    if not isinstance(data, dict):
        raise ReceiptError("receipt root must be an object")
    if data.get("schemaVersion") != "1.0":
        raise ReceiptError("schemaVersion must be '1.0'")

    require_string(data, "receiptId", "receipt")
    require_string(data, "createdAt", "receipt")

    agent = require_object(data, "agent", "receipt")
    require_string(agent, "name", "agent")
    require_string(agent, "model", "agent")
    require_string(agent, "tool", "agent")

    task = require_object(data, "task", "receipt")
    require_string(task, "issue", "task")
    require_string(task, "summary", "task")
    criteria = require_non_empty_list(task, "acceptanceCriteria", "task")
    if not all(isinstance(item, str) and item.strip() for item in criteria):
        raise ReceiptError("task.acceptanceCriteria entries must be non-empty strings")

    scope = require_object(data, "scope", "receipt")
    require_string(scope, "description", "scope")
    files = require_non_empty_list(scope, "files", "scope")
    if not all(isinstance(item, str) and item.strip() for item in files):
        raise ReceiptError("scope.files entries must be non-empty strings")

    evidence = require_object(data, "evidence", "receipt")
    tests = require_non_empty_list(evidence, "tests", "evidence")
    for index, test in enumerate(tests):
        if not isinstance(test, dict):
            raise ReceiptError(f"evidence.tests[{index}] must be an object")
        require_string(test, "command", f"evidence.tests[{index}]")
        if test.get("result") not in {"passed", "failed", "not-run"}:
            raise ReceiptError(
                f"evidence.tests[{index}].result must be passed, failed, or not-run"
            )

    bootstrap = data.get("bootstrapException") is True
    reviewers = evidence.get("independentReviewers")
    if bootstrap:
        if path.resolve() != BOOTSTRAP_RECEIPT.resolve():
            raise ReceiptError("bootstrapException is allowed only for 0000-bootstrap.json")
        if task.get("issue") != "bootstrap":
            raise ReceiptError("the bootstrap receipt must use task.issue='bootstrap'")
    else:
        if not isinstance(reviewers, list) or not reviewers:
            raise ReceiptError("at least one independent reviewer is required")
        if not any(
            isinstance(reviewer, dict)
            and reviewer.get("independent") is True
            and reviewer.get("verdict") == "approved"
            and isinstance(reviewer.get("agent"), str)
            and reviewer.get("agent").strip()
            and isinstance(reviewer.get("model"), str)
            and reviewer.get("model").strip()
            for reviewer in reviewers
        ):
            raise ReceiptError("an independent reviewer with verdict='approved' is required")

    attestations = require_object(data, "attestations", "receipt")
    for key in (
        "syntheticDataOnly",
        "noSecretsOrPhi",
        "licenseReviewed",
        "limitationsDisclosed",
    ):
        if attestations.get(key) is not True:
            raise ReceiptError(f"attestations.{key} must be true")


def changed_receipts(base: str) -> list[Path]:
    process = subprocess.run(
        ["git", "diff", "--name-only", f"{base}...HEAD"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    changed = [line.strip() for line in process.stdout.splitlines() if line.strip()]
    return [
        ROOT / item
        for item in changed
        if item.startswith(".ai/receipts/") and item.endswith(".json")
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--all", action="store_true", help="validate every committed receipt")
    group.add_argument("--base", help="validate the single receipt changed since this git ref")
    args = parser.parse_args()

    if args.all:
        receipts = sorted(RECEIPT_DIR.glob("*.json"))
        if not receipts:
            print("No receipts found", file=sys.stderr)
            return 1
    else:
        receipts = changed_receipts(args.base)
        if len(receipts) != 1:
            print(
                f"A pull request must add or update exactly one receipt; found {len(receipts)}",
                file=sys.stderr,
            )
            return 1

    failed = False
    for receipt in receipts:
        try:
            validate_receipt(receipt)
            print(f"PASS {receipt.relative_to(ROOT)}")
        except ReceiptError as exc:
            failed = True
            print(f"FAIL {receipt.relative_to(ROOT)}: {exc}", file=sys.stderr)

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
