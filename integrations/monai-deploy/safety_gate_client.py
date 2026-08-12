"""Privacy-conscious client for the Clinical AI Safety Kit evaluation API."""

from __future__ import annotations

import ipaddress
import json
import re
import socket
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Mapping
from urllib.parse import urlsplit, urlunsplit

MAX_TEXT_LENGTH = 100_000
MAX_RESPONSE_BYTES = 1_000_000
MAX_REQUEST_BYTES = 1_000_000
RULE_CODE = re.compile(r"MAG-[A-Z0-9]+(?:-[A-Z0-9]+)*")
SEVERITIES = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}


class SafetyGateError(RuntimeError):
    """Base error that never includes submitted clinical text."""


class SafetyGateConfigurationError(SafetyGateError):
    """Raised when the client configuration is unsafe or invalid."""


class SafetyGateUnavailable(SafetyGateError):
    """Raised when the safety service cannot return a valid decision."""


class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


@dataclass(frozen=True)
class SafetyDecision:
    status: str
    score: int
    rule_codes: tuple[str, ...]

    @property
    def allowed(self) -> bool:
        return self.status == "PASS"

    def as_dict(self) -> dict[str, Any]:
        return {
            "status": self.status,
            "score": self.score,
            "ruleCodes": list(self.rule_codes),
            "allowed": self.allowed,
        }


class ClinicalAiSafetyClient:
    """Evaluate text through the local or explicitly trusted safety service."""

    def __init__(
        self,
        base_url: str = "http://127.0.0.1:8080",
        *,
        timeout_seconds: float = 5.0,
        allow_remote_http: bool = False,
    ) -> None:
        if not 0 < timeout_seconds <= 30:
            raise SafetyGateConfigurationError("timeout_seconds must be greater than 0 and at most 30")
        self._endpoint = _evaluation_endpoint(base_url, allow_remote_http)
        self._timeout_seconds = timeout_seconds
        self._opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            _NoRedirectHandler(),
        )

    def evaluate(
        self,
        prompt: str,
        response: str,
        metadata: Mapping[str, Any] | None = None,
    ) -> SafetyDecision:
        _validate_text("prompt", prompt)
        _validate_text("response", response)
        try:
            payload = json.dumps(
                {"prompt": prompt, "response": response, "metadata": dict(metadata or {})},
                ensure_ascii=False,
                separators=(",", ":"),
            ).encode("utf-8")
        except (TypeError, ValueError):
            raise SafetyGateConfigurationError("metadata must be a JSON-serializable mapping") from None
        if len(payload) > MAX_REQUEST_BYTES:
            raise SafetyGateConfigurationError("evaluation request exceeded the size limit")
        request = urllib.request.Request(
            self._endpoint,
            data=payload,
            headers={"Content-Type": "application/json", "Accept": "application/json"},
            method="POST",
        )

        try:
            with self._opener.open(request, timeout=self._timeout_seconds) as http_response:
                if http_response.status != 200:
                    raise SafetyGateUnavailable(f"safety service returned HTTP {http_response.status}")
                if http_response.headers.get_content_type() != "application/json":
                    raise SafetyGateUnavailable("safety service returned an unexpected content type")
                raw_body = http_response.read(MAX_RESPONSE_BYTES + 1)
        except urllib.error.HTTPError as exc:
            status_code = exc.code
            exc.close()
            raise SafetyGateUnavailable(f"safety service returned HTTP {status_code}") from None
        except (urllib.error.URLError, TimeoutError, socket.timeout, OSError):
            raise SafetyGateUnavailable("safety service is unavailable") from None

        if len(raw_body) > MAX_RESPONSE_BYTES:
            raise SafetyGateUnavailable("safety service response exceeded the size limit")
        try:
            body = json.loads(raw_body)
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise SafetyGateUnavailable("safety service returned invalid JSON") from None
        return _parse_decision(body)


def _evaluation_endpoint(base_url: str, allow_remote_http: bool) -> str:
    try:
        parsed = urlsplit(base_url)
        port = parsed.port
    except ValueError:
        raise SafetyGateConfigurationError("base_url is invalid") from None
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise SafetyGateConfigurationError("base_url must use http or https and include a host")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise SafetyGateConfigurationError("base_url must not include credentials, a query, or a fragment")
    if parsed.scheme == "http" and not allow_remote_http and not _is_loopback_host(parsed.hostname):
        raise SafetyGateConfigurationError(
            "plain HTTP is restricted to loopback; use HTTPS or explicitly allow remote HTTP"
        )
    host = f"[{parsed.hostname}]" if ":" in parsed.hostname else parsed.hostname
    netloc = f"{host}:{port}" if port is not None else host
    base_path = parsed.path.rstrip("/")
    return urlunsplit((parsed.scheme, netloc, f"{base_path}/api/v1/evaluations", "", ""))


def _is_loopback_host(host: str) -> bool:
    if host.lower() == "localhost":
        return True
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False


def _validate_text(name: str, value: str) -> None:
    if not isinstance(value, str) or not value.strip():
        raise SafetyGateConfigurationError(f"{name} must be non-blank text")
    if len(value) > MAX_TEXT_LENGTH:
        raise SafetyGateConfigurationError(f"{name} exceeded the size limit")


def _parse_decision(body: Any) -> SafetyDecision:
    if not isinstance(body, dict):
        raise SafetyGateUnavailable("safety service response must be an object")
    status = body.get("status")
    score = body.get("score")
    findings = body.get("findings")
    _require_uuid(body.get("evaluationId"))
    _require_timestamp(body.get("evaluatedAt"))
    _require_non_blank_string(body.get("ruleVersion"), "ruleVersion")
    if status not in {"PASS", "WARN", "BLOCK"}:
        raise SafetyGateUnavailable("safety service response contained an invalid status")
    if not isinstance(score, int) or isinstance(score, bool) or not 0 <= score <= 100:
        raise SafetyGateUnavailable("safety service response contained an invalid score")
    if not isinstance(findings, list):
        raise SafetyGateUnavailable("safety service response contained invalid findings")

    rule_codes: list[str] = []
    for finding in findings:
        if not isinstance(finding, dict):
            raise SafetyGateUnavailable("safety service response contained an invalid finding")
        rule_code = _require_non_blank_string(finding.get("ruleCode"), "finding ruleCode")
        if not RULE_CODE.fullmatch(rule_code):
            raise SafetyGateUnavailable("safety service response contained an invalid rule code")
        if finding.get("severity") not in SEVERITIES:
            raise SafetyGateUnavailable("safety service response contained an invalid severity")
        _require_non_blank_string(finding.get("message"), "finding message")
        if not isinstance(finding.get("evidence"), str):
            raise SafetyGateUnavailable("safety service response contained invalid evidence")
        rule_codes.append(rule_code)

    if status == "PASS" and rule_codes:
        raise SafetyGateUnavailable("safety service returned an inconsistent PASS decision")
    if status == "PASS" and score != 100:
        raise SafetyGateUnavailable("safety service returned an inconsistent PASS score")
    if status == "BLOCK" and not rule_codes:
        raise SafetyGateUnavailable("safety service returned an inconsistent BLOCK decision")
    return SafetyDecision(status, score, tuple(sorted(set(rule_codes))))


def _require_non_blank_string(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise SafetyGateUnavailable(f"safety service response contained an invalid {field}")
    return value.strip()


def _require_uuid(value: Any) -> None:
    text = _require_non_blank_string(value, "evaluationId")
    try:
        uuid.UUID(text)
    except ValueError:
        raise SafetyGateUnavailable("safety service response contained an invalid evaluationId") from None


def _require_timestamp(value: Any) -> None:
    text = _require_non_blank_string(value, "evaluatedAt")
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        raise SafetyGateUnavailable("safety service response contained an invalid evaluatedAt") from None
    if parsed.tzinfo is None:
        raise SafetyGateUnavailable("safety service response contained an invalid evaluatedAt")
