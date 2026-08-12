# Agent Instructions

These instructions apply to the entire repository.

## Mission

Build an explainable, testable safety evaluation gateway for medical AI applications. The project does not diagnose, prescribe, or replace professional care.

## Required process

1. Read `README.md`, `AI_CONTRIBUTION_POLICY.md`, `CONTRIBUTING.md`, and relevant source files completely before editing.
2. Search open work before starting. Do not duplicate an active contribution.
3. Use synthetic data only. Never add PHI, secrets, private prompts, or production logs.
4. Add regression tests for every behavior change.
5. Run `./mvnw verify` and the receipt verifier before reporting completion.
6. State anything that was not tested.
7. Add or update exactly one receipt in `.ai/receipts/` for the contribution.

## Safety invariants

- Finding evidence must not expose the sensitive value it detected.
- Emergency signals must fail safely when escalation language is absent.
- Rules must remain deterministic by default.
- Model-based evaluators must be optional and clearly separated from deterministic rules.
- Do not claim clinical validity from unit tests.
- Do not weaken a severity or bypass behavior without an explicit Issue and high-risk review.

## Code style

- Target Java 21.
- Prefer immutable records and small, focused classes.
- Keep rule codes stable after release.
- Avoid adding dependencies when the JDK or existing libraries are sufficient.
- Public behavior must be documented in the README or API documentation.
