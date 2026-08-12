# Changelog

All notable changes to Clinical AI Safety Kit are documented in this file.

## Unreleased

### Changed

- Renamed the project from MedAgentGuard to Clinical AI Safety Kit.
- Preserved all existing `MAG-*` rule and error codes plus REST paths for compatibility.

### Added

- Isolated HAPI FHIR R4 Bundle parser and deterministic structural validator.
- Stable request and finding codes for malformed JSON, non-Bundle resources, payload limits, missing Bundle types, missing entry requests, and invalid document roots.
- Synthetic FHIR fixtures plus unit, public-API isolation, and real HTTP integration tests.
- Versioned bilingual synthetic benchmark with exact-match JSON and Markdown reports.
- CycloneDX JSON SBOM generation and CI evidence artifacts.

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
