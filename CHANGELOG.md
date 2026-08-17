# Changelog

All notable changes to Clinical AI Safety Kit are documented in this file.

## Unreleased

### Fixed

- Closed a bypass shared by every mitigation check. A mitigating phrase used to clear a rule wherever it appeared in the response, so `MAG-EMERGENCY-001` stayed silent on "this is not an emergency, get some sleep" — the CRITICAL rule was defeated by negating the very word it looked for. Mitigating phrases now count only when they are not negated within the same clause.
- `MAG-MEDICATION-001` no longer accepts bare "label" or "参考" as a source, and requires the citation to sit near the dosage it is supposed to support.
- `MAG-DIAGNOSIS-001` no longer accepts bare "medical professional" or "专业医生" as a limitation statement, so claiming clinical authority no longer reads as disclaiming it.

### Changed

- Benchmark reports a detection rate on unsafe cases and a false-positive rate on safe cases instead of a single exact-match percentage, and carries `baseline`, `adversarial` and `known-gap` categories.
- `known-gap` cases are enforced in both directions: the build fails if one regresses and also when a rule improvement fixes one without the published limitation list being updated.
- Rule version bumped to `2026-08-17.1`.
- Renamed the project from MedAgentGuard to Clinical AI Safety Kit.
- Preserved all existing `MAG-*` rule and error codes plus REST paths for compatibility.

### Added

- Isolated HAPI FHIR R4 Bundle parser and deterministic structural validator.
- Stable request and finding codes for malformed JSON, non-Bundle resources, payload limits, missing Bundle types, missing entry requests, and invalid document roots.
- Synthetic FHIR fixtures plus unit, public-API isolation, and real HTTP integration tests.
- Versioned bilingual synthetic benchmark with exact-match JSON and Markdown reports.
- CycloneDX JSON SBOM generation and CI evidence artifacts.
- Fail-closed MONAI Deploy text safety gate example with a dependency-free client and synthetic tests.
- Bilingual v0.2 launch kit, privacy-safe first-user feedback form, and original social-preview artwork.
- Privacy-safe aggregate GitHub metrics collector, deterministic tests, and read-only scheduled snapshot workflow.

## 0.1.0 — 2026-08-12

### Added

- Deterministic bilingual medical AI safety evaluation engine.
- Emergency escalation, medication citation, privacy echo, and diagnostic certainty rules.
- REST endpoints for evaluations and the active rule catalog.
- Health and readiness endpoints.
- Unit and real HTTP integration tests using synthetic data.
- AI-written, human-governed contribution policy.
- Machine-readable AI contribution receipts and protected GitHub gate.
- Maven Wrapper, container build, bilingual documentation, and CI.
