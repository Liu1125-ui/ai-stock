# SDD ledger — plan: docs/superpowers/plans/2026-10-10-sdd006a-alert-rule-plan.md
Base: 7570a16. User approved design and inline plan.
Ruling: continue the existing clean codex/sdd004-market-refresh feature branch in the user-designated project; no new worktree or branch. Existing deployment paths and runtime evidence depend on this checkout.
Ruling: Windows native manual task ledger replaces POSIX task helper invocation; task identity, preflight and RED/GREEN evidence remain recorded.
Pre-flight: Task1 boundary -> Task2 HTTP client: exact fields/UTC asOf and Shanghai trading dates. Task2 rule DTO -> Task3/4 proxy/page: decimal strings and revision; emailEnabled is consent, deliveryAvailability remains NOT_ENABLED. Task3 session Identity has no emailVerified: profile fetched separately.
Task 1: in progress.

Task 1: complete — 309b69a; 5 boundary tests RED (missing contract) → GREEN. Task 2: in progress. Initial shell/path errors corrected before genuine RED.

Task 2: MySQL 5/5 GREEN. Initial real concurrent run reproduced guard shared-to-exclusive lock upgrade deadlock; replaced INSERT IGNORE with exclusive UPSERT, same concurrent test now passes. Logs runtime/sdd006a-task2-mysql.log and task2-green.log.
Task 3: in progress; HTTP security tests first.

Task 2 committed 9540e19. Task 3 security initial 7 GREEN; additional transport/access coverage included full run. Task 4 initial missing component/401 event RED; implemented, corrected actual ElementPlus input selector and fixture literal type; full 89 frontend tests and strict build GREEN. Ruling: extract notification preference panel into a separately tested component mounted by ProfilePage — isolate async owner lifecycle and avoid expanding account/password handlers — cost if wrong: move component inline without API change. Final whole-stage fresh review dispatched once; full backend regression in progress.

Final review: one Important (edit conflict silently becomes create) reproduced by regression test RED; keep editing identity/draft, lock conflicted save until explicit reselection. Minor (percentage limit/precision are version-policy constants rather than runtime configuration) deferred, current defaults correct; document and centralize in future policy endpoint. No security bypass found. Full backend351 plus boundaryHTTP1=352 GREEN, frontend89 GREEN before fix. Task3 commit7f9b1c3 Task4 commit59d4076.

Task4 conflict fix686c5f4: full frontend90 and strict build GREEN. Task5 actual503 diagnosed at runtime; genuine Spring proxy regression RED then GREEN, fixcd0cc20. Internal response upper bound RED missing class then GREEN, fix6ee6c01. Whole backend354 GREEN, last notification+common30 GREEN after response cap; final nonduplicated reports356. SixHTTPservicesUP. Browser createdSH600030 rule, changedthreshold31.123456, disabled, reloaded; preferenceson/off finaloff. Mailcount/attempts2/2 unchanged, originalprediction/model unchanged. RuntimeDocker restored, frontend restarted after gatewayIP change. Task1–5 complete. One fresh independent final review only. NoMaaScall, noalertsSMTP, noDBreset, nopush/merge.

Final rulings: reused current checkout to preserve deployment paths (risk: future isolation/rebase may be needed); native Windows ledger replaced POSIX helper (risk: manual record upkeep); separate preference component isolated owner/async lifecycle (risk: move inline if later design requires). Deferred Minor: centralize percentage limits/precision policy. Additional runtime limits and followups are documented, not represented as completed B/C.
