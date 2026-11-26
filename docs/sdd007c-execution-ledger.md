# SDD ledger — plan: docs/superpowers/plans/2026-10-13-sdd007c-comparison-ai.md
Base: 09f1f8c. User approved continuation after review, 2026-10-13. Execution: inline.
Ruling: reuse fixed E:/aiStock checkout on codex/sdd004-market-refresh, not main/master — established user preference; selective commits, no remote push.
Ruling: Windows native ledger and runtime logs replace bash helpers — task/base/RED/GREEN evidence retained, leave other .superpowers workspaces alone.
Pre-flight: Task1 Facts/context/hash -> Task2 model prompt -> Task3 stored context -> Task4 service: nested ComparisonAiFacts.Facts and generateComparison(Map) match.
Pre-flight: Task3 Claim(execute,receipt,replayed,cached) -> Task4 Generation(operation,replayed,cached) -> Task5 typed receipt; controller uses flags, not state alone, for201/200.
Pre-flight: Task3 gate COMPARISON_EXPLANATION -> Task6 rollback drain; old gate cannot read new kind, C defaults disabled.
Pre-flight: Task4 four /comparison-explanations routes -> Task5 auth event/storage keys; B /comparisons remains independent.
Task1: in progress. Task2–6 pending. No provider, MaaS or mail calls.

Task1: complete — 7/7 new tests pass; missing-class RED and final GREEN logs runtime/sdd007c-task1-red.log and task1-green.log. Fixed values/order/hash, missing vs suspended, genuine zero, dynamic stock count and unsupported assertions covered. Initial regex compile escape corrected before GREEN.
Ruling: full fact placeholders are independent clauses with small connector allowlist; natural-language correctness is not universally guaranteed.
Task2: in progress; dedicated comparison client and strict parsing.

Task2: complete — 17/17 comparison facts/template plus old MaasClientTest and new ComparisonMaasClientTest passed; missing generateComparison RED observed. Logs task2-red/green. C strict inner+outer JSON; old question inner-only parsing unchanged; no external calls.
Ruling: dedicated ComparisonMaasClientTest file rather than expanding old compressed test — preserves old tests and isolates new cases.
Task3: in progress; isolated MySQL tests first.

Task3: complete — 6 new MySQL tests plus 4 existing QuestionMysqlTest pass (10/10), missing Repository/Input RED observed. Concurrent same-key execution, owner/cache/failure, global old/new gate, pure reads, stale worker, actual lock wait across expiry and rollback/no-orphan covered. Log task3-green.log. V9 is isolated only, not production.
Task4: in progress; HTTP and proxy tests added before controllers.

Task4: complete — selected prediction27/gateway3 tests passed, including security/MySQL creation201, replay/cache200, failed200 preserving old result, USER/ADMIN owner boundary, CSRF/Origin, strict input/body cap, C65s vs B30s, disabled/no-provider and pure reads. Missing controllers/service/client RED observed; logs task4-red/green. C flag defaults false.
Task5: in progress; missing Vue/API/state modules RED observed, task5-red.log.

Task5: complete — frontend145/145 and strict build passed; module-missing RED and two recovery regressions RED→GREEN observed. Unknown new request no longer inherits old FAILED state; running latest blocks new request; corrupt recovery warning retained. Logs task5-red, task5-recovery-red, frontend-final/build. Existing size warnings unchanged.
Task6: in progress; full backend regression running, fresh whole-slice review next. C production feature flag still not enabled, no true MaaS/vendor/mail calls.

Final review: one independent read-only agent,09f1f8c..db71fbb. Accepted2 P2, standalone false clause and controlled503 loss; both observed RED and targeted GREEN. Supplemented late POST/logout and actual old QuestionRepository gate cross-flow. Frontend147/build passed. Full backend final rerun underway.
Ruling: every nonempty template clause must be allowed connector+one fact placeholder — closes standalone literal basis/relationship assertions; costs unrestricted prose, intentional consistency-first scope.

Task6: final backend clean verify 604/604, failures/errors/skipped 0; frontend147/147 plus strict build passed. Isolated probe compiled the actual 09f1f8c old gate, observed new-kind rejection, conditionally cleaned expired C slot and resumed legacy gate; transaction rolled back, original test state retained. Production image rollback/Flyway-old-startup not rehearsed. Logs runtime/sdd007c-backend-final.log, frontend-final/build, rollback-probe.log.

Task6: deployed prediction/gateway/frontend with C false; all six Java and algorithm UP, five service registrations, V9 applied, new C counts0 and empty slot, original critical fingerprints unchanged. Gateway deliberately does not register; initial check corrected against register-enabled:false, not a product failure. User authorized at most1 real MaaS call for existing two-stock comparison; enable C for explicit user click, never auto-retry. No email/provider calls authorized by this approval. Browser automation still kernel path failure; normal user session required.

Live acceptance: user clicked once, operation7e1a921f-ca12-42e3-91bf-d19322489e6e saved FAILED/AI_OUTPUT_INVALID, result0, slot cleared. One authorized call consumed; C disabled again, no automatic retry. Browser503 reproduced by nginx missing comparison-explanations long route (8s vs35s MaaS/65s gateway). Nginx timeout corroborated by live logs. Exact provider-format violation not retained; do not invent it or weaken validation. Add safe reason-only diagnostics for a future separately authorized call. User-reported runtime defect is a new fix, not a second independent-review pass.

Live defect fix: nginx budget regression RED8s -> GREEN, isolated actual Nginx local9s response200, ordinary API8s503 retained. Safe diagnostics2 tests RED -> GREEN, targeted16/16; final full backend606/606 failures/errors/skipped0, frontend148/148 and strict build PASS. Original failed operation facts8/8 and sourceHash/formula/cutoff audited consistent; no second paid call. Production deployment of this fix next.

Live defect fix deployed9e6aeb1: effective Nginx exact long route and nginx-t PASS; all six Java plus algorithm UP, Cfalse/mailfalse. Original critical data hashes unchanged. First operation FAILED retained,8/8 source facts and sourceHash/formula/cutoff consistent. Real successful model output remains unaccepted; requested at most1 additional diagnostic call, none executed before explicit approval.

User explicitly authorized1 additional diagnostic call (total authorized2, already consumed1). Enable C and require exactly1 new explicit click; no provider/mail calls and no automatic retry.

Second authorized live call: operation41593a5f-ec65-460c-b75d-956b46e66d36 SUCCEEDED, explanationa4b1997e-8aeb-4ac4-a74c-92b33a599144; user sees3 paragraphs. Read-only audit19refs/8metrics/sourceHash/formula allPASS. Total2 operations(1FAILED,1SUCCEEDED),1 result, slotempty, remaining authorized calls0. Waiting read-only user refresh; no more generate calls. First exact invalid output remains unknown, successful second output does not prove every future output valid.
Ruling: keep C enabled after successful explicit acceptance for normal user-initiated use; perform no further agent generation beyond2 authorized calls — GETs and the panel never auto-generate; cost if wrong is an unintended explicit manual new-context call, UI fee notice and no auto-retry retained.

Task6: complete — final backend606/606, frontend148/148/build PASS; one independent review plus verified fixes; original data preserved; V9/healthy deployment; isolated actual oldgate rollback PASS (full oldimage/Flyway startup not rehearsed); two separately authorized live calls, firstFAILED retained and secondSUCCEEDED19refs/8metrics auditPASS; user confirms refresh preserved, counts2ops/1result unchanged, no further provider request. Native plan ledger is persistent record; leave old .superpowers untouched. Keep branch/checkout per established preference, no remote push.
