# MONAI Deploy text safety gate

[English](README.md) · [简体中文](README.zh-CN.md)

This optional example places Clinical AI Safety Kit between a narrative-generating operator and a downstream publisher such as MONAI Deploy App SDK's `DICOMTextSRWriterOperator`.

It does **not** evaluate image tensors, segmentations, model accuracy, or clinical validity. It evaluates only the prompt/context and generated narrative text sent to the Clinical AI Safety Kit HTTP API.

## Data flow

```text
MONAI image inference → narrative generator → safety gate → DICOM Text SR writer
                                              ├─ PASS: forward narrative
                                              └─ BLOCK/error: stop publication
```

The gate is fail-closed: `WARN`, `BLOCK`, malformed responses, timeouts, and unavailable services never emit `safe_text`.

## Files

- [`safety_gate_client.py`](safety_gate_client.py): dependency-free HTTP client with bounded input, timeout, response-size, and response-schema checks.
- [`safety_gate_operator.py`](safety_gate_operator.py): MONAI Deploy `Operator` with `prompt` and `response` inputs plus `safe_text` and `safety_decision` outputs.
- [`tests/`](tests): synthetic tests using a loopback fake service and MONAI API stubs; no GPU, model, DICOM file, or MONAI installation is required.

## Start the safety service

From the repository root:

```bash
./mvnw spring-boot:run
```

The example defaults to `http://127.0.0.1:8080`. Plain HTTP is restricted to loopback unless `allow_remote_http=True` is explicitly configured. In a multi-container deployment, use authenticated network controls and HTTPS; never send real patient data, PHI, DICOM tags, or identifiers to an untrusted endpoint.

## Add the operator to a MONAI Deploy application

The upstream narrative operator must emit a de-identified context string and the generated narrative. Place the gate before the DICOM writer:

```python
from pathlib import Path

from monai.deploy.operators.dicom_text_sr_writer_operator import DICOMTextSRWriterOperator

from safety_gate_operator import ClinicalAiSafetyGateOperator


safety_gate = ClinicalAiSafetyGateOperator(
    self,
    base_url="https://clinical-ai-safety.internal",
    name="clinical_ai_safety_gate",
)
dicom_sr_writer = DICOMTextSRWriterOperator(
    self,
    output_folder=Path(app_context.output_path),
    model_info=model_info,
    copy_tags=True,
    name="dicom_text_sr_writer",
)

self.add_flow(
    narrative_generator,
    safety_gate,
    {("prompt_text", "prompt"), ("report_text", "response")},
)
self.add_flow(
    series_selector,
    dicom_sr_writer,
    {("study_selected_series_list", "study_selected_series_list")},
)
self.add_flow(safety_gate, dicom_sr_writer, {("safe_text", "text")})
```

The exact output names of `narrative_generator` are application-specific. `series_selector` represents the application's existing `DICOMSeriesSelectorOperator`; its DICOM output goes directly to the writer and must never be connected to the safety gate. If the gate blocks, the downstream writer receives no narrative text. Capture the `safety_decision` output in a privacy-safe audit operator if your workflow needs a decision record.

## Test

```bash
python3 -m unittest discover -s integrations/monai-deploy/tests -p 'test_*.py'
```

The adapter targets the `Fragment`, `Operator`, and `OperatorSpec` interface in MONAI Deploy App SDK 3.0.0 and was checked against the public API shape on the upstream `main` branch on 2026-08-12. The repository CI tests the adapter logic with API stubs because installing the current SDK requires its NVIDIA Holoscan/CUDA runtime. Validate the complete packaged application in your own supported MONAI Deploy environment before use.

## Boundaries and attribution

- This is an integration example, not a clinical workflow or medical device.
- It must not receive real patient data or identifiers.
- A PASS result is not approval for clinical use and does not establish report correctness.
- MONAI is a project of the MONAI Consortium. Clinical AI Safety Kit is an independent project and is not affiliated with or endorsed by Project MONAI or NVIDIA.
- No MONAI source code, model, image, DICOM asset, or dataset is included here.
