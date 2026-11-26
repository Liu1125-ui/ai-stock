# D3B3 Revision Verification Implementation Plan

> Inline superpowers:executing-plans; ONE fresh whole-slice readonly reviewer at end.

Goal: durable RAW-head events trigger bounded rechecks, with controlled sameRAW-factor rechecks.
Architecture: MS02 V8 own-DB publication event journal; MS03 affected forecasts; MS04 V4 CAS event cursor and existing typed tasks.
Tech Stack: Java17 Spring Boot/MySQL8/Jdbc/HTTP and Docker Compose.
Spec: docs/superpowers/specs/2026-10-05-sdd005d3b3-revision-verification-design.md
Base: fdbe261; fixed existing codex workspace.

## Global Constraints
No future fabricated values, no cross-DB joins, append-only evidence; service identity before body/params; max50/page/128KiB/2sconnect/10sread; existing120sverify lease. Independent flag false until deployed. No secrets/remote/push.

## Review Focus
Event commit ordering and rollback; completed/hidden affected forecasts; event replay and RAW return-to-old-head; network failure/restart/CAS cannot advance skipped page; controlled recheck idempotency and no public route.

### Task 1: Event journal and affected forecasts
Files: market V8, MarketVersionRepository, RevisionEventRepository/Controller, MarketDatabaseFixture/RevisionEventMysqlTest; prediction VerificationDueRepository/Controller and RevisionAffectedMysqlTest.
Interfaces: event page items eventId(long),instrumentId,rawVersionId; affected reuses DuePage(cutoffDate,items,nextCursor).
- [x] RED publish dedup/revision/rollback/event order/bounds tests; complete+hidden/stock/date filtered affected tests.
- [x] Implement migration backfill, transactional global sequence and authenticated own-DB queries.
- [x] GREEN focused tests, README and commit.

### Task 2: Persistent event plan and controlled recheck
Files: scheduler V4, RevisionClient/HttpRevisionClient/RevisionScanRepository/RevisionVerificationPlan; TaskController/Configuration and config compose .env.example; RevisionScanMysqlTest/RevisionClientTest/security+wiring tests.
Interfaces: one pending active event with cutoff fixed per persisted round and forward-only day reset; affected Page; existing createVerification,receipt recovery.
- [x] RED CAS/atomic/restart/partial failure/invalidpage/replay and HTTP contract plus recheck tests.
- [x] Implement bounded client/event cursor/plan, service-only recheck key and independent flag.
- [x] GREEN all scheduler; README/commit.

### Task 3: Verify, review, deploy
- [x] Full backend cleanverify; unchanged frontend test/build evidence retained; one final reviewer, ONE Critical/Important TDD fix pass.
- [x] Enable real revision flag, deploy market/prediction/scheduler; check V8/V4 health/auth/public404, backfilled real events empty affected, controlled sameRAW SKIPPED/replay/restart/unchanged versions/snapshot/models.
- [x] Evidence, ledger/status documents and local commit. Real due-day accuracy remains open.

## 跨日修复决定
active事件每个持久轮次固定cutoff；跨北京时间20点或跨日后仅前进cutoff，清空after并增加revision，重新扫描当前到期范围。提交时再次检查cutoff，旧页不得完成事件。事件HTTP返回后重新读取clock。任务key改为raw-加SHA256(predictionId,eventId,rawVersionId,ruleVersion,实际dueThrough)，同轮重复去重，新截止不冲突，长度68。保留MS03的due不回退保护；未完成事件不会只核对过时前缀而永久完成。
