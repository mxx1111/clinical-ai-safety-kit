from __future__ import annotations

import json
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

INTEGRATION_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(INTEGRATION_ROOT))

from safety_gate_client import (  # noqa: E402
    ClinicalAiSafetyClient,
    SafetyGateConfigurationError,
    SafetyGateUnavailable,
)


class _Handler(BaseHTTPRequestHandler):
    response_status = 200
    response_body: object = {}
    response_headers: dict[str, str] = {}
    received_path = ""
    received_payload: object = None

    def do_POST(self) -> None:
        type(self).received_path = self.path
        length = int(self.headers.get("Content-Length", "0"))
        type(self).received_payload = json.loads(self.rfile.read(length))
        body = self.response_body
        encoded = body if isinstance(body, bytes) else json.dumps(body).encode("utf-8")
        self.send_response(self.response_status)
        self.send_header("Content-Type", "application/json")
        for name, value in self.response_headers.items():
            self.send_header(name, value)
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, format: str, *args) -> None:
        return


class ClinicalAiSafetyClientTest(unittest.TestCase):
    def setUp(self) -> None:
        _Handler.response_status = 200
        _Handler.response_body = _decision_body()
        _Handler.response_headers = {}
        _Handler.received_path = ""
        _Handler.received_payload = None
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.client = ClinicalAiSafetyClient(f"http://127.0.0.1:{self.server.server_port}")

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def test_pass_decision_and_request_contract(self) -> None:
        decision = self.client.evaluate(
            "Synthetic imaging context.",
            "No urgent narrative finding is generated.",
            {"caseId": "synthetic-monai-001"},
        )

        self.assertTrue(decision.allowed)
        self.assertEqual("PASS", decision.status)
        self.assertEqual("/api/v1/evaluations", _Handler.received_path)
        self.assertEqual("synthetic-monai-001", _Handler.received_payload["metadata"]["caseId"])

    def test_block_decision_is_not_allowed_and_codes_are_sorted(self) -> None:
        _Handler.response_body = _decision_body(
            status="BLOCK",
            score=20,
            findings=[
                _finding("MAG-PRIVACY-001"),
                _finding("MAG-DIAGNOSIS-001"),
                _finding("MAG-PRIVACY-001"),
            ],
        )

        decision = self.client.evaluate("Synthetic context.", "Synthetic output.")

        self.assertFalse(decision.allowed)
        self.assertEqual(("MAG-DIAGNOSIS-001", "MAG-PRIVACY-001"), decision.rule_codes)

    def test_invalid_response_fails_closed_without_echoing_input(self) -> None:
        _Handler.response_body = b"not-json"
        sensitive_marker = "SYNTHETIC-SECRET-MARKER"

        with self.assertRaises(SafetyGateUnavailable) as caught:
            self.client.evaluate(sensitive_marker, sensitive_marker)

        self.assertNotIn(sensitive_marker, str(caught.exception))

    def test_inconsistent_pass_fails_closed(self) -> None:
        _Handler.response_body = _decision_body(findings=[_finding("MAG-PRIVACY-001")])

        with self.assertRaises(SafetyGateUnavailable):
            self.client.evaluate("Synthetic context.", "Synthetic output.")

    def test_metadata_must_be_json_serializable(self) -> None:
        with self.assertRaises(SafetyGateConfigurationError):
            self.client.evaluate("Synthetic context.", "Synthetic output.", {"invalid": object()})

    def test_oversized_request_is_rejected_before_network_access(self) -> None:
        with self.assertRaises(SafetyGateConfigurationError):
            self.client.evaluate(
                "Synthetic context.",
                "Synthetic output.",
                {"oversized": "x" * 1_000_000},
            )

        self.assertIsNone(_Handler.received_payload)

    def test_missing_top_level_contract_field_fails_closed(self) -> None:
        required_fields = ("evaluationId", "status", "score", "findings", "evaluatedAt", "ruleVersion")
        for field in required_fields:
            with self.subTest(field=field):
                _Handler.response_body = _decision_body()
                del _Handler.response_body[field]
                with self.assertRaises(SafetyGateUnavailable):
                    self.client.evaluate("Synthetic context.", "Synthetic output.")

    def test_incomplete_finding_contract_fails_closed(self) -> None:
        required_fields = ("ruleCode", "severity", "message", "evidence")
        for field in required_fields:
            with self.subTest(field=field):
                finding = _finding("MAG-PRIVACY-001")
                del finding[field]
                _Handler.response_body = _decision_body(status="BLOCK", score=60, findings=[finding])
                with self.assertRaises(SafetyGateUnavailable):
                    self.client.evaluate("Synthetic context.", "Synthetic output.")

    def test_timestamp_without_timezone_fails_closed(self) -> None:
        _Handler.response_body = _decision_body()
        _Handler.response_body["evaluatedAt"] = "2026-08-12T00:00:00"

        with self.assertRaises(SafetyGateUnavailable):
            self.client.evaluate("Synthetic context.", "Synthetic output.")

    def test_redirect_is_not_followed(self) -> None:
        _Handler.response_status = 307
        _Handler.response_headers = {"Location": "https://example.invalid/collect"}

        with self.assertRaises(SafetyGateUnavailable) as caught:
            self.client.evaluate("Synthetic context.", "Synthetic output.")

        self.assertIn("HTTP 307", str(caught.exception))

    def test_remote_plain_http_requires_explicit_opt_in(self) -> None:
        with self.assertRaises(SafetyGateConfigurationError):
            ClinicalAiSafetyClient("http://safety.internal:8080")

        client = ClinicalAiSafetyClient("http://safety.internal:8080", allow_remote_http=True)
        self.assertIsNotNone(client)

    def test_remote_https_is_accepted_without_plain_http_opt_in(self) -> None:
        client = ClinicalAiSafetyClient("https://safety.example.invalid")
        self.assertIsNotNone(client)


def _finding(rule_code: str) -> dict[str, object]:
    return {
        "ruleCode": rule_code,
        "severity": "HIGH",
        "message": "Synthetic safety finding.",
        "evidence": "Synthetic evidence without submitted values.",
    }


def _decision_body(
    *,
    status: str = "PASS",
    score: int = 100,
    findings: list[dict[str, object]] | None = None,
) -> dict[str, object]:
    return {
        "evaluationId": "00000000-0000-4000-8000-000000000001",
        "status": status,
        "score": score,
        "findings": findings or [],
        "evaluatedAt": "2026-08-12T00:00:00Z",
        "ruleVersion": "2026-08-12.1",
    }


if __name__ == "__main__":
    unittest.main()
