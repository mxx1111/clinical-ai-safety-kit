from __future__ import annotations

import sys
import types
import unittest
from pathlib import Path

INTEGRATION_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(INTEGRATION_ROOT))


class _FakeOperator:
    def __init__(self, fragment, *args, **kwargs):
        self.fragment = fragment


class _FakeFragment:
    pass


class _FakeOperatorSpec:
    def __init__(self):
        self.inputs = []
        self.outputs = []

    def input(self, name):
        self.inputs.append(name)

    def output(self, name):
        self.outputs.append(name)


monai_module = types.ModuleType("monai")
deploy_module = types.ModuleType("monai.deploy")
core_module = types.ModuleType("monai.deploy.core")
core_module.Fragment = _FakeFragment
core_module.Operator = _FakeOperator
core_module.OperatorSpec = _FakeOperatorSpec
sys.modules.setdefault("monai", monai_module)
sys.modules.setdefault("monai.deploy", deploy_module)
sys.modules.setdefault("monai.deploy.core", core_module)

from safety_gate_client import SafetyDecision, SafetyGateUnavailable  # noqa: E402
from safety_gate_operator import ClinicalAiSafetyGateOperator, SafetyGateBlocked  # noqa: E402


class _Input:
    def __init__(self, values):
        self.values = values

    def receive(self, name):
        return self.values[name]


class _Output:
    def __init__(self):
        self.values = {}

    def emit(self, value, name):
        self.values[name] = value


class _Client:
    def __init__(self, decision=None, error=None):
        self.decision = decision
        self.error = error

    def evaluate(self, prompt, response, metadata):
        if self.error:
            raise self.error
        return self.decision


class ClinicalAiSafetyGateOperatorTest(unittest.TestCase):
    def test_setup_declares_expected_ports(self) -> None:
        operator = ClinicalAiSafetyGateOperator(_FakeFragment(), client=_Client())
        spec = _FakeOperatorSpec()

        operator.setup(spec)

        self.assertEqual(["prompt", "response"], spec.inputs)
        self.assertEqual(["safe_text", "safety_decision"], spec.outputs)

    def test_pass_emits_decision_and_forwards_text(self) -> None:
        operator = ClinicalAiSafetyGateOperator(
            _FakeFragment(), client=_Client(SafetyDecision("PASS", 100, ()))
        )
        output = _Output()

        operator.compute(_Input({"prompt": "Synthetic context", "response": "Synthetic report"}), output, None)

        self.assertEqual("Synthetic report", output.values["safe_text"])
        self.assertTrue(output.values["safety_decision"]["allowed"])

    def test_block_emits_decision_but_never_forwards_text(self) -> None:
        operator = ClinicalAiSafetyGateOperator(
            _FakeFragment(),
            client=_Client(SafetyDecision("BLOCK", 60, ("MAG-EMERGENCY-001",))),
        )
        output = _Output()

        with self.assertRaises(SafetyGateBlocked):
            operator.compute(_Input({"prompt": "Synthetic context", "response": "Synthetic report"}), output, None)

        self.assertNotIn("safe_text", output.values)
        self.assertEqual(["MAG-EMERGENCY-001"], output.values["safety_decision"]["ruleCodes"])

    def test_warn_never_forwards_text(self) -> None:
        operator = ClinicalAiSafetyGateOperator(
            _FakeFragment(), client=_Client(SafetyDecision("WARN", 90, ("MAG-FUTURE-001",)))
        )
        output = _Output()

        with self.assertRaises(SafetyGateBlocked):
            operator.compute(_Input({"prompt": "Synthetic context", "response": "Synthetic report"}), output, None)

        self.assertNotIn("safe_text", output.values)
        self.assertFalse(output.values["safety_decision"]["allowed"])

    def test_block_decision_cannot_be_constructed_as_allowed(self) -> None:
        decision = SafetyDecision("BLOCK", 60, ("MAG-EMERGENCY-001",))
        self.assertFalse(decision.allowed)

    def test_non_string_input_fails_closed(self) -> None:
        operator = ClinicalAiSafetyGateOperator(
            _FakeFragment(), client=_Client(SafetyDecision("PASS", 100, ()))
        )
        output = _Output()

        with self.assertRaises(SafetyGateBlocked):
            operator.compute(_Input({"prompt": None, "response": "Synthetic report"}), output, None)

        self.assertNotIn("safe_text", output.values)
        self.assertEqual("ERROR", output.values["safety_decision"]["status"])

    def test_unavailable_service_emits_error_decision_without_text(self) -> None:
        operator = ClinicalAiSafetyGateOperator(
            _FakeFragment(), client=_Client(error=SafetyGateUnavailable("unavailable"))
        )
        output = _Output()

        with self.assertRaises(SafetyGateBlocked) as caught:
            operator.compute(
                _Input({"prompt": "SYNTHETIC-PRIVATE-PROMPT", "response": "SYNTHETIC-PRIVATE-REPORT"}),
                output,
                None,
            )

        self.assertNotIn("safe_text", output.values)
        self.assertEqual("ERROR", output.values["safety_decision"]["status"])
        self.assertNotIn("SYNTHETIC-PRIVATE", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
