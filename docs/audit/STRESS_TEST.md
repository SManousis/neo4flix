# Bounded Stress-Smoke Evidence

Date: 2026-09-15
Profile: `scripts/k6/smoke.js`
Status: **Partial — bounded public and authenticated smoke passed**

The checked-in profile is intentionally small: one to five virtual users, a
short duration, and anonymous catalog/genre reads only. It is a local readiness
probe, not a deployment-scale capacity audit. It accepts 2xx/3xx/429 responses
and fails on other HTTP responses or the explicit latency/error thresholds.

Run with native k6 when installed:

```powershell
k6 run --vus 1 --duration 15s .\scripts\k6\smoke.js
```

The documented Docker fallback is:

```powershell
docker run --rm -i --network host -v "${PWD}\scripts\k6:/scripts:ro" grafana/k6:0.53.0 run /scripts/smoke.js
```

An authenticated disposable-account smoke profile is also available. It never
embeds a password or prints the access token; the account is deleted in k6
`teardown`:

```powershell
$secure = Read-Host 'Disposable k6 password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
  $env:K6_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
  docker run --rm -i --network host -e BASE_URL=http://host.docker.internal:8080 -e K6_PASSWORD -v "${PWD}\scripts\k6:/scripts:ro" grafana/k6:0.53.0 run --vus 1 --duration 15s /scripts/authenticated-smoke.js
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
  Remove-Item Env:K6_PASSWORD -ErrorAction SilentlyContinue
}
```

## Current run record

| Field | Result |
| --- | --- |
| Native k6 discovery | `k6` was not installed; the pinned Docker runner was used |
| Profile execution | Passed with `grafana/k6:0.53.0`, 1 VU for 15 seconds |
| Covered routes | `GET /api/v1/movies?page=0&size=1`, `GET /api/v1/genres` |
| Authenticated coverage | None; recommendation/rating throughput is not claimed |
| Requests/checks | 30 requests; 30/30 checks passed; HTTP failure rate 0.00% |
| Latency | p95 `http_req_duration`: 14.83 ms |
| Graph continuity | Read-only relationship count remained `42` before and after the smoke |

## Authenticated smoke run

Profile: `scripts/k6/authenticated-smoke.js`
Runner: `grafana/k6:0.53.0`, 1 VU for 15 seconds

| Field | Result |
| --- | --- |
| Requests/checks | 48 requests; 45/45 authenticated endpoint checks passed |
| HTTP failure rate | 0.00% |
| Latency | p95 `http_req_duration`: 131.53 ms |
| Disposable cleanup | 0 `k6-*` users remained after teardown |
| Graph continuity | Relationship count remained `42` after cleanup |

These bounded public and authenticated smokes are recorded. Sustained
deployment-scale concurrency, deterministic load-seed performance analysis,
and release SLO interpretation are N/A for this intentionally local-only
project; the bounded local smoke remains the relevant acceptance evidence.

The authenticated profile is a bounded smoke probe only; a successful run does
not establish sustained throughput or a release SLO. No deployment environment
or production SLO is in scope for this project.

## Bounded sustained run

The same profile was run at 5 VUs for 30 seconds against the local development
stack. It completed 150 iterations and 453 HTTP requests (including setup and
teardown), with 450/450 endpoint checks passing, 0.00% server-failure rate, and
p95 latency of 11.83 ms. The profile observed 115 HTTP 429 responses; these are
classified as intentional rate limiting, not server failures. Teardown left 0
disposable users and the relationship count remained 42.

This result is useful local integrity/rate-limit evidence, but it is not a
production capacity claim. No deployment environment or production SLO is in
scope for this project.
