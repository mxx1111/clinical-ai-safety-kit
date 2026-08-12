# Security Policy

## Supported versions

Until the first stable release, only the latest commit on `main` is supported.

## Reporting a vulnerability

Do not open a public Issue for vulnerabilities involving secrets, remote code execution, authorization bypass, workflow injection, provenance-gate bypass, or sensitive data exposure.

Use GitHub private vulnerability reporting when it is enabled for the repository. If it is unavailable, contact the repository owner privately through the verified contact method on their GitHub profile.

Include a minimal reproducer using synthetic data. Never include real patient data, PHI, credentials, access tokens, private prompts, or production logs.

## Medical safety issues

False negatives in emergency escalation, leakage of sensitive identifiers, or bypasses of a `BLOCK` result should be treated as security-sensitive until triaged.
