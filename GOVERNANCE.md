# Governance

Clinical AI Safety Kit is maintained under an AI-written, human-governed model.

## Human maintainers

Human maintainers:

- define project mission and release policy;
- protect credentials and infrastructure;
- confirm licensing and legal obligations;
- resolve community disputes;
- activate emergency security procedures;
- make the final merge or release decision.

Human maintainers do not directly edit source code, tests, documentation, or normal configuration submitted as a contribution. They express requested changes as review requirements and delegate implementation to an AI agent.

## AI agents

AI agents may act as planners, implementers, testers, documentation authors, and reviewers. An agent has no merge authority and cannot accept legal responsibility.

## Decision process

- Routine changes: passing CI, valid receipt, and one independent AI approval.
- High-risk changes: passing CI, valid receipt, two independent AI reviews, and explicit human maintainer approval.
- Security response: a human maintainer may temporarily close access, revoke credentials, revert a release, or disable automation. The subsequent code fix remains AI-authored.

High-risk changes include authentication, authorization, secret handling, release workflows, Agent instructions, contribution-gate bypasses, and medical safety policy behavior.
