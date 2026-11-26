# SDD ledger — plan: docs/superpowers/plans/2026-10-13-sdd007b-stock-comparison.md

Base: f8bd5fe. Execution: native; user approved 2026-10-13.
Ruling: reuse existing codex/sdd004-market-refresh checkout — user fixed E:/aiStock and existing Compose paths; no main/master changes; cost is shared working tree, controlled by selective commits.
Ruling: Windows PowerShell uses checked-in execution ledger rather than bash-only helpers — same task/base/test record retained; no cross-plan workspace cleanup.
Pre-flight: Task1 options/evidence -> Task2 verifier and Task3 create: immutable paired IDs, full snapshots, ISO/string contract.
Pre-flight: Task3 owner Operation/Result -> Task4 API -> Task5 UI: five-field Input; recover GET is read-only; FAILED same key terminal.
Pre-flight: Task2 Calculation -> Task3 storage -> Task5: private full snapshots, public refs+chart, summaries only in list.
Task1: in progress. Tasks2–6: pending. No external market/AI/mail calls authorized by this plan.
Task1: complete — market 25/25 and client 2/2; RED missing Options/client observed; logs runtime/sdd007b-task1-*.log. Isolated MySQL was stopped, existing test container restarted; production not changed.
Ruling: bounded HTTP transport is copied into comparison package, not generalized across unrelated AI code — avoids changing legacy behavior; costs small isolated duplication.
Task2: complete — calculator 6/6 and evidence verifier 2/2; RED missing Records/calculator/verifier observed; runtime/sdd007b-task2-red.log and task2-green.log. Independent numeric fixture includes annualized 224.499443%; strict own-stock risk gaps and volume independence pass.
Ruling: sources use validated JsonNode snapshots instead of duplicated MS02 record classes — preserves exact source strings/hash serialization across service boundary; cost is runtime validation, covered by verifier.
Task3: complete — prediction comparison 15/15 (10 prior + 4 MySQL + 1 service); logs task3-red/service-red/green. Driver DATETIME returned LocalDateTime: fixed explicit UTC conversion after observed failure. Concurrent claim, expired-generation success/failure rejection, rollback, >2^53 cursors, busy/no-operation all pass.
Ruling: add summary_json column beside result_json — SQL list reads summaries without loading full charts/sources; cost small derived storage, created atomically.
Task4: in progress; auth/CSRF/owner and exact proxy tests written before product API.
Task4: complete — proxy 2/2; prediction comparison 16/16 including real Spring security + MySQL HTTP flow. Unknown/duplicate JSON/query, body cap, CSRF/Origin, ADMIN-other-owner404, source outage preserving old reads, request operation recovery all pass; log task4-green.log.
Task5: complete — frontend 130/130 passed, initial build passed; logs task5-red/green, frontend-final and frontend-build. Coverage includes initial source default, double submit, storage corruption/denial, stale options, 4 recovery states, fixed detail refresh, unavailable metric, keyset cursor and 401 event; existing page entry tests pass.
Ruling: separate ComparisonChart.vue from result panel — keeps canvas lifecycle out of metric presentation and enables component tests without fake visual claims; no additional business scope.
Task6: in progress. Full backend isolated regression running. Final independent review next; no deployment success claimed yet.

Final review: one independent reviewer, f8bd5fe..38e0fcf. Accepted three P2 findings: first-page upper lost, late history detail overwrites new result, missing vs suspension distinction lost. Fix pass includes exact calendar conflict options and transport slow/truncated response coverage; no second reviewer dispatched.
Ruling: allow upper-only history query to re-read first page under original upper, reject before without upper — avoids cursor arithmetic/Long overflow; explicit refresh alone obtains new upper.
Ruling: persist per-stock requested-range dataIssues (date/field/state), omit them from list summaries — distinguishes missing vs suspended including outside usable endpoints; no price interpolation and no formula change.
Real-evidence audit: actual Java calculator vs independent Python Decimal, three stocks x 3M/6M/1Y x four metrics = 36, all passed; full chart points/source hash checked; runtime/sdd007b/audit, no external API/DB writes.

Final fix pass: acc3979, frontend initial two regressions RED then GREEN; calendar options RED then GREEN; MySQL upper-only and dataIssues RED then GREEN; slow/truncated client tests already GREEN. Full backend clean verify 582/582 and frontend 133/133/build passed. No second final reviewer.
Task6: complete — real offline 36 metrics/curves/source hash PASS; deployed four images with old rollback tags retained; Java6+algorithm UP, MS03 V8; user normal login created 01a31765-75cf-4bf5-95be-df7037e57b81 and confirmed all normal. Readonly persisted audit 8 metrics+122 points+source hash PASS. Counts and selected critical fingerprints retained; session/calendar/migration increments attributed in verification. No slice AI/mail/vendor calls; existing automatic calendar updater ran separately.
Ruling: keep existing branch and fixed checkout, no remote merge/push menu — user explicitly requests continuing project in place and repository has no remote; no destructive workspace cleanup.
Deferred: independent narrow-screen/keyboard visual checks, unchanged bundle size warnings, compare-evidence auto-update and C AI (separate slices); prior minors remain listed. Browser automation kernel path failure; user acceptance recorded accurately, no claim of automated visual pass.
