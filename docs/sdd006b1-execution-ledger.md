# SDD ledger — docs/superpowers/plans/2026-10-10-sdd006b1-alert-evidence-plan.md
Base: f20134f.
Ruling: B1 evidence before B2 scheduler/inbox — source correctness prerequisite, user authorized next incremental business step — cost if wrong: combine followup slice without changing rule UI.
Ruling: reuse fixed E:/aiStock feature checkout and Windows-native ledger — existing local deployment and user folder choice — cost: later isolation/rebase and manual ledger maintenance.
Preflight: strict fixed RAW/calendar identity, paired source two dates, price/rate availability separate; no RAW/forecast/model/rule/mail mutation.
Task1: starting tests.
Task1 complete: missing validator/client RED; validator6 and collector realHTTP1 GREEN; full nonMySQL market/common test PASS runtime/sdd006b1-task1-green.log. Task2 missing service RED runtime/sdd006b1-mysql-red.log.
Task2 complete: missing service/controller RED; isolated MySQL11 + Validator7 + collectorHTTP1 + internalHTTP1 GREEN, affected market/common fullverifyPASS runtime/sdd006b1-task2-green.log. No real deployment yet. Final review and fullbackend next.

Review dispatched once: /root/sdd006b1_final_review, read-only f20134f..d631f7f; fullbackend regression in progress. B1 source protocol reuses forecast-collect only, not forecast publication. Corrected overall spec status to distinguish A/B0 accepted, B1 implementation and future B2/C.

Ruling: dedicated historical alert-collect replaces forecast endpoint reuse — prior source feasibility record explicitly prohibits future-calendar dependency — cost if wrong: maintain one small request parser/route and regression forecast behavior. Python missing route/import RED then all76 GREEN. Java old endpoint red then new historical contract.
Final review: Critical0 Important3 Minor0. Numeric exact decimal RED expected100.0000000000000000001 got100.0; fullbody deadline RED TimeoutException at32seconds; RAW calendar fallback initial fixture corrected to legal published dates then RED expectedVERIFIED gotMISSING_PREVIOUS_CALENDAR. Single fix pass in progress.
Final: Ruling: B2/C left for later as scoped; real continuous availability and real ex-rights event sample require later dedicated evidence, not code-review claims — cost: added followup validation, never pretend test fixtures are real market events.

Final GREEN: backend clean verify389/0failures/0errors/0skipped; algorithm76 PASS. Three live stocks VERIFIED, independent hashes and Decimal rates matched. Restart reused same operation without recollection: evidence/receipt identical. Six services UP. Original RAW/prediction/models/rules/preferences/mail retained. No MaaS/SMTP/training/new forecast. Code fix commit8e2265a. All tasks accepted within B1 scope; B2/C pending.
