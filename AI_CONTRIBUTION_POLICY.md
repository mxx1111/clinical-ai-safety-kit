# AI Contribution Policy

## 1. Principle

MedAgentGuard is an AI-written, human-governed open-source project.

- Humans define goals, report problems, make governance decisions, handle credentials, accept legal responsibility, and perform the final merge.
- AI agents produce source code, tests, documentation, migrations, configuration, and review findings.
- A human must not hand-edit a proposed contribution and then describe it as agent-authored.

The project enforces evidence of the contribution process. It cannot prove the metaphysical origin of every character, so truthful disclosure remains mandatory.

## 2. Required contribution workflow

Every non-bootstrap pull request must:

1. Link to an Issue with explicit acceptance criteria.
2. Be implemented by an identified AI agent.
3. Include automated tests proportional to the risk.
4. Be reviewed by at least one independent AI agent that did not implement the change.
5. Include one new machine-readable receipt under `.ai/receipts/`.
6. Pass the project build, tests, receipt gate, and security checks.
7. Disclose incomplete verification, uncertainty, or unavailable dependencies.

## 3. Contribution receipt

The receipt records:

- agent and model identity;
- linked task and acceptance criteria;
- changed scope;
- tests and commands executed;
- independent AI reviewers;
- licensing, privacy, and medical-safety attestations.

The receipt must not contain prompts, logs, secrets, patient data, personal data, API keys, access tokens, or proprietary context.

## 4. Independence rule

At least one reviewer must be independent from the implementing agent. Prefer a different model family or toolchain. A reviewer must inspect the actual diff and test evidence, not only the PR summary.

Changes to any of the following require explicit review evidence:

- `.github/workflows/**`
- `AGENTS.md`
- `AI_CONTRIBUTION_POLICY.md`
- receipt validation scripts or schemas
- authentication, authorization, secrets, network access, or release configuration
- medical safety rule severity or bypass behavior

## 5. Medical and privacy constraints

- Never commit real patient data or PHI.
- Use only synthetic, public-domain, or properly licensed datasets.
- Do not claim clinical validity without documented external validation.
- Do not implement diagnosis or treatment recommendations as project output.
- Emergency behavior must fail safely and be covered by tests.
- Finding evidence must redact sensitive values.

## 6. Licensing and responsibility

The human submitter remains responsible for ensuring that the contribution may be distributed under Apache-2.0 and does not copy incompatible third-party material. AI agents cannot sign the Developer Certificate of Origin or accept legal responsibility.

Use this commit trailer when an AI agent materially assisted the contribution:

```text
Assisted-by: <agent-name> (<model-or-version>)
```

If a future DCO check is enabled, only the human contributor may add `Signed-off-by`.

## 7. Bootstrap exception

The initial repository commit necessarily creates this policy before the gate exists. Only `.ai/receipts/0000-bootstrap.json` may use `bootstrapException: true`. No later receipt may use that exception.
