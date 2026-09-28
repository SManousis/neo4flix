# Human Usability Test

Date prepared: 2026-09-15
Status: **Completed manually; detailed observations were not recorded in this repository**

The automated Playwright journey provides supporting interaction evidence, but
it must not be reported as human observation. A facilitator should run the
following path against the seeded local stack and record the participant
without collecting passwords, tokens, or identifying data.

| Step | Participant task | Record |
| --- | --- | --- |
| 1 | Create an account and sign in | completion, hesitation, confusion |
| 2 | Find a movie by title and open its details | search success and time |
| 3 | Rate the movie | control discoverability and feedback |
| 4 | Add it to the watchlist, then remove it | state clarity and confirmation |
| 5 | Open recommendations and explain why one result appears | explanation comprehension |
| 6 | Apply a genre/rating filter | filter discoverability and empty-state clarity |
| 7 | Share a recommendation and open the public share page | sharing confidence and errors |

## Current evidence

- Automated browser coverage confirms login, reload, guarded navigation, logout,
  sharing, disposable-admin cleanup, and 2FA flow. A fresh local rerun passed 8
  tests with 1 credential-gated ADMIN skip; see
  `docs/audit/batch-11-browser-failure-verification.md`.
- The user confirmed on 2026-09-24 that the manual human audit/usability checks
  were completed. This file intentionally does not invent participant identity,
  confusion notes, satisfaction scores, or time-on-task results that were not
  supplied for storage.

## Suggested record

Use an anonymous participant ID, capture one short observation per step, and
record whether the task was completed without facilitator intervention. Delete
any screenshots or notes containing credentials or personal data after the audit.
