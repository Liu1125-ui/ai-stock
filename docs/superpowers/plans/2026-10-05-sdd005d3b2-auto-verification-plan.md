# SDD005D3B2 Automatic Verification Implementation Plan

> For agentic workers: use superpowers:executing-plans inline task-by-task; one final fresh-context review.

Goal: daily due forecasts enter persistent typed verification tasks and recover receipts after restart.
Architecture: MS03 own-DB keyset due endpoint; MS04 bounded HTTP, typed existing worker, durable CAS scan. No cross-DB access.
Tech Stack: Java17 Spring Boot/Jdbc/MySQL8/Java HttpClient, existing Docker Compose.
Spec: docs/superpowers/specs/2026-10-05-sdd005d3b2-auto-verification-design.md
Base: 58dabb3; fixed existing codex workspace authorized.

## Global Constraints
No future fabricated data; immutable forecast/evidence; internal service identity before parsing; max50/page,128KiB body,2s connect,100s execute/10s read,120s verification lease/90s market lease; local commits no secrets/no remote. D3B3 revision-trigger rechecks remain pending.

## Review Focus
1. Lost execute reply and receipt404 before lease must not create a second attempt.
2. Complete current pointer differs from newest-created version; scan uses pointer, hidden/downlined history remains eligible.
3. Scan concurrency/restart and changed cutoff must not skip a page or regress cursor.
4. Unknown/wrong operation/oversize/slow body must not become false success; permanent source errors stop retry.
5. No due points yields empty plan or controlled SKIPPED without evaluation; original forecast unchanged.

### Task 1: Due discovery and source error semantics
Files: verification/VerificationDueRepository.java, VerificationDueController.java, Configuration.java; HttpVerificationMarketClient.java; tests VerificationDueMysqlTest.java, DueHttpTest.java, VerificationMarketClientTest.java; README.
Consumes: own forecast_request, verification_head/current version; trusted internal identity.
Produces: DuePage(cutoffDate,items[predictionId,instrumentId,dueThrough,ruleVersion],nextCursor); exact endpoint above.
- [x] Write failing due-filter/keyset/limit/hidden/complete/future tests and controlled RAW mismatch error test.
- [x] Run RED; implement bounded own-DB discovery and controlled error whitelist.
- [x] Run targeted GREEN; commit.

### Task 2: Persistent typed tasks, transport and worker
Files: scheduler task package PredictionTaskClient/HttpPredictionTaskClient/PredictionTaskHttp/TaskConfiguration; TaskRepository/TaskWorker/TaskController, V3; TaskVerificationMysqlTest/PredictionTaskClientTest/TaskSecurityTest; README.
Consumes: Task1 exact page; existing verify operation and receipt endpoints.
Produces: PREDICTION_VERIFY claim/execution/recovery; verified-version or SKIPPED state; service-only manual task create.
- [x] Write RED typed dedup/state/lease/retry/lost-reply/restart tests and bounded HTTP/identity tests.
- [x] Implement migration, typed records, separate dispatch and receipt recovery, no network in DB transactions.
- [x] GREEN all scheduler including old market task tests; commit.

### Task 3: Durable daily plan and deployment
Files: VerificationScanRepository/DailyVerificationPlan, config/compose/.env.example; scan tests, docs proof/progress/status and existing frontend pending hint.
Consumes: bounded due page and createVerification; produces persisted cursor+task atomic CAS.
- [x] RED repeated page/restart/day-change/concurrent CAS/failure-not-advance tests.
- [x] Implement 50/page persistent scan and configurable flag; update frontend hint only when deployed enabled.
- [x] Full backend/frontend regressions/build; one fresh final reviewer, one Critical/Important TDD fix pass.
- [x] Deploy prediction/scheduler/frontend with real daily plan enabled; check auth/gateway, V3, actual not-due skips/dedup/restart, forecast/model unchanged; write proof and commit.
