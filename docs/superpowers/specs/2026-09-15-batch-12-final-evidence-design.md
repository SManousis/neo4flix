# Batch 12 Final Evidence Design

## Goal

Complete the remaining Batch 12 evidence artifacts by mapping security requirements and test outputs to fresh repository evidence, then record a truthful user-journey walkthrough without inventing human feedback.

## Scope

Create:

1. `docs/audit/SECURITY_CHECKLIST.md` — a requirement-to-proof matrix covering authentication, authorization, input safety, transport/browser controls, error handling, sensitive logging, and known optional deployment follow-ups.
2. `docs/audit/TEST_EVIDENCE.md` — exact commands, fresh result counts, browser/failure-mode evidence, and explicit skips/limitations.
3. `docs/audit/USABILITY_TEST.md` — the seven-step novice journey from the canonical validation document, with observed completion/confusion/dead-end/feedback fields. Automated browser evidence must be labeled as automated; a human-participant result must not be claimed without an actual participant.

Modify the active context and Batch 12 verification record; keep `docs/reference/00_MASTER_EXECUTION_PLAN.md` at `[~]` unless the human usability gate is genuinely satisfied.

## Evidence rules

- Every `[x]` row links to a test, source, or recorded command that actually ran.
- Optional k6, production TLS/HSTS, container scanning, and backup/restore remain explicitly open unless fresh evidence exists.
- Never print or commit credentials, tokens, private keys, TOTP secrets, or `.env` values.
- Distinguish automated Playwright/Testcontainers evidence from human usability evidence.
- Keep all changes documentation-only; no production behavior changes are needed.

## Usability handling

The automated browser suite already proves the functional journey, but it is not a human usability study. The usability document will record the automated journey as supporting evidence and list the human-participant gate as pending unless the user or another real participant completes the flow and supplies observations. This preserves the canonical “real human usability walkthrough” requirement rather than converting automation into a false claim.

## Verification

- Run the full Maven and frontend test suites already used by Batch 12.
- Run `git diff --check` and secret-like content scans over all new evidence.
- Review all evidence rows for source links, exact counts, and honest limitations.
- Push `main` and verify local/remote SHA equality.
