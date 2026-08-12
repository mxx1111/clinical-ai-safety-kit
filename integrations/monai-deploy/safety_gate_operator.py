"""MONAI Deploy operator that gates narrative text before publication."""

from __future__ import annotations

from monai.deploy.core import Fragment, Operator, OperatorSpec

from safety_gate_client import (
    ClinicalAiSafetyClient,
    SafetyDecision,
    SafetyGateError,
)


class SafetyGateBlocked(RuntimeError):
    """Stops the workflow without including the submitted text in the error."""


class ClinicalAiSafetyGateOperator(Operator):
    """Forward generated narrative text only after an explicit PASS decision."""

    def __init__(
        self,
        fragment: Fragment,
        *args,
        client: ClinicalAiSafetyClient | None = None,
        base_url: str = "http://127.0.0.1:8080",
        timeout_seconds: float = 5.0,
        allow_remote_http: bool = False,
        **kwargs,
    ) -> None:
        self._client = client or ClinicalAiSafetyClient(
            base_url,
            timeout_seconds=timeout_seconds,
            allow_remote_http=allow_remote_http,
        )
        super().__init__(fragment, *args, **kwargs)

    def setup(self, spec: OperatorSpec) -> None:
        spec.input("prompt")
        spec.input("response")
        spec.output("safe_text")
        spec.output("safety_decision")

    def compute(self, op_input, op_output, context) -> None:
        prompt = op_input.receive("prompt")
        response = op_input.receive("response")
        if not isinstance(prompt, str) or not isinstance(response, str):
            op_output.emit(_error_decision(), "safety_decision")
            raise SafetyGateBlocked("text publication stopped because the operator input was invalid")
        try:
            decision = self._client.evaluate(
                prompt,
                response,
                {"integration": "monai-deploy", "dataClassification": "synthetic-or-deidentified"},
            )
        except SafetyGateError:
            op_output.emit(_error_decision(), "safety_decision")
            raise SafetyGateBlocked("text publication stopped because the safety service was unavailable") from None

        op_output.emit(decision.as_dict(), "safety_decision")
        if decision.status != "PASS":
            codes = ",".join(decision.rule_codes) if decision.rule_codes else "none"
            raise SafetyGateBlocked(
                f"text publication blocked with status {decision.status}; rule codes: {codes}"
            )
        op_output.emit(response, "safe_text")


def _error_decision() -> dict[str, object]:
    return SafetyDecision("ERROR", 0, ()).as_dict()
