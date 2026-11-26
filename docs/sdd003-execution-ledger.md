# SDD ledger — plan: docs/superpowers/plans/2026-09-30-sdd003-watchlist-market-plan.md

2026-09-30 用户确认，原生连续执行六任务。BASE=edffe6f。Task1进行中；Task2—6待执行。
Ruling: 固定在用户指定E:\aiStock正式工程内工作，创建codex/sdd003-watchlist-market分支，不创建额外工作树，也不操作TianMao项目。当前不是main/master；工作区初始干净。
Ruling: Bash技能辅助脚本以原生PowerShell记录等价台账/BASE/每任务证据，工作区使用已忽略runtime/sdd003，正式台账在docs；避免Windows shell路径转换误写其他项目。
Pre-flight Task1→Task2/4: MarketCandidate/Publication/RAW版本由MS-02唯一发布；Python只产候选，小数String输入映射BigDecimal；查询视图重新投影字符串。
Pre-flight Task2→Task3: 内部InstrumentView仍用既有instrumentId/code/name/demoOnly/sourceService，公开DTO可扩展但不能误改内部字段。Identity沿现有UUID userId。
Pre-flight Task2/3→Task5: 市场接口和个人引用分开加载；AUTH_REQUIRED需覆盖新增instruments路径，行情故障不得清空个人自选。
Pre-flight Task4→Task6: 旧SQLite载荷截止2026-09-24、获取时间沿原created；在线失败和存量成功分开验收，不能修改旧源版本。
Ruling: Task1中的expectedRevision必须在head切换时核对，初始槽revision=0；未传revision不能默许较旧覆盖较新数据，默认禁止coverageEnd后退。Task1 test discovery: corrected doubled backend path before implementation. Initial Maven command found no requested tests; not counted as RED or passed verification.

Task 1: complete — full clean verify PASS; 95 backend tests (81 existing + 14 added). Formula, immutable publication, MySQL concurrency, unauthorized/oversized bodies and malformed metadata verified. RED logs task1-core-red, task1-guard-observed-red; GREEN task1-final-verified.log.
Ruling: CandidateRequestFilter enforces authentication before JSON decoding and bounds streamed bodies, not just Content-Length; rejection metadata sanitized to avoid secondary SQL errors.
Task 2: in progress; BASE follows Task 1 commit.

Task 2: complete — first real-MySQL run found JDBC DATETIME yields LocalDateTime (not Timestamp); fixed UTC projection; full clean verify PASS, 105 backend tests. RED task2-red.log (missing interfaces); regression task2-green.log; GREEN task2-green2.log. Search excludes DEMO and escapes wildcards, date/range/version checks, full-version indicators, calendar freshness, direct-service session fail-close and gateway whitelist exercised.
Ruling: Initial public query selects explicitly the baostock/RAW head; no automatic provider fallback or cross-source mixing.
Task 3: in progress; BASE follows Task 2 commit.

Task 3: complete — isolated MySQL cross-user, concurrent duplicate add, pagination and service-failure tests; gateway DELETE verb verified; full clean verify PASS (111 tests). RED task3-red.log, GREEN task3-green.log.
Ruling: personal watchlist remains readable/deletable during market outage; only new adds require market identity verification.
Task 4: in progress; BASE follows Task 3 commit.

Task 4: Ruling: pin installed and exercised Baostock 0.9.4 (pip metadata), correcting a mistaken 0.9.40 assumption before image build.

Task 4: Ruling: share pure SQLite preparation in app.market.prepare and keep scripts/ CLI a wrapper so the container and Windows run the same tested logic; Docker image does not copy scripts/.

Task 4: complete — fake-SDK RED missing module, 6 local tests GREEN, 12 container tests PASS (4 existing + 8 new), image build PASS, read-only preparation produced three 421-row candidates dated 2025-01-02..2026-09-24 with original version-hash checks, live Baostock probe returned AVAILABLE/5 rows via authenticated algorithm HTTP. Evidence: task4-python-red2.log, task4-python-green3.log, task4-docker-tests3.log, task4-live-probe.log, ignored runtime/sdd003/candidates/manifest.json. This online probe is not a published update.
Task 5: in progress; BASE follows Task 4 commit.

Task 5: complete — RED task5-red.log (missing chart/store); GREEN task5-core-green.log, typecheck PASS, 14 frontend tests PASS, production build PASS. Home/search, personal watchlist, dated analysis and four linked chart panels implemented; forecast entry explicitly disabled. Bundle warning: analysis async chunk about 551 kB (ECharts), existing full Element Plus index about 1.04 MB; optimization deferred, no functional failure.
Task 6: started; BASE follows Task 5 commit.

Task 6: complete — independent review found four P2 (metadata status, candidate dates, route identity, request ordering). Added failing regression tests, fixed and reran targeted green. Final full backend 115 tests PASS (task6-backend-post-review.log), frontend 18 tests PASS (task6-review-frontend-green2.log), typecheck/build PASS, Python container 12 tests PASS. Rebuilt market/frontend Compose images, repeated market checks (three versions x421, last 2026-09-24), and verified browser session/watchlist persisted after user-service restart; temporary record removed. No model inference or automated latest publication claimed. Git remote absent; CI file not remotely executed.

2026-10-02 close-out: previous gateway commit blocked by approval-review usage limit, resumed without bypass. Docker initially stopped; restored existing Compose services/test MySQL. Full backend clean verify PASS, frontend 18/build PASS, Python 12 PASS, Compose and historical version checks PASS. Remaining local commits completed; no remote push.
