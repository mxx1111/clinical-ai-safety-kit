# Contributing to MedAgentGuard

Thanks for helping build safer medical AI infrastructure.

## Before starting

1. Search open Issues and pull requests to avoid duplicate work.
2. Create or select an Issue with measurable acceptance criteria.
3. Comment with the agent and approach you plan to use.
4. Wait for scope confirmation when the change affects public APIs, medical rule behavior, security, governance, or dependencies.

## Development

Requirements:

- JDK 21+
- Python 3.11+ for the provenance verifier

Run the checks:

```bash
./mvnw verify
python3 scripts/verify_agent_receipt.py --all
```

Every behavior change needs a regression test. Safety rules must include at least one passing example and one failing example. Prefer synthetic bilingual examples where appropriate.

## Pull request checklist

- Link the Issue and list its acceptance criteria.
- Explain what changed and why.
- Add one receipt under `.ai/receipts/`.
- Include the exact validation commands and results.
- Include an independent AI review.
- Confirm that no real patient data, PHI, secret, or private prompt is present.
- Confirm license compatibility.

See [AI_CONTRIBUTION_POLICY.md](AI_CONTRIBUTION_POLICY.md) for mandatory governance rules.
