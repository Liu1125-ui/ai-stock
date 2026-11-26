# SDD ledger — plan: docs/superpowers/plans/2026-10-12-sdd007a-comparison-evidence.md
BASE: 76fa52e
用户要求复查后直接执行，保留当前会话主代理方式。
Ruling: 固定E:\aiStock原工程与既有codex功能分支继续；不另建工作树，保持用户本机部署入口、依赖及隔离库脚本路径稳定；仅已有.superpowers未跟踪文件，不触碰其他计划。
Ruling: 技能bash任务脚本在PowerShell环境用同语义持久ledger、任务brief及测试日志替代；每次记录真实RED/GREEN和提交。
Pre-flight Task1->2: Validated输出规范化序列/hash，仓库据此发布；Candidate原始输入指纹需独立记录，确保同键改变内容409。
Pre-flight Task2->3: 发布固定RAW旧版本允许头移动，读取不可变evidenceId；不修改原预测接口。
Ruling: 实施前发现成交量缺失与RAW匹配歧义；Candidate.volumeShares必须与RAW完全一致，缺失只在RAW本身缺失时合法，防把已有真实成交量删掉。
Ruling: 错误status明确：请求/数据校验422、operation冲突409、未知版本404，内部服务故障503；控制器只交统一异常处理。
Task 1: in progress.Task 1: RED compilation missing Candidate/Validator observed; GREEN 14 tests, 0 fail/error/skip, runtime/sdd007a-task1-green.log. Task 1 complete.
Task 2 Ruling: Evidence保存sourcePayloadHash和完整calendar，重叠RAW日历再次核对；隔离fixture先清理新表避免旧测试外键残留。
Task 2 complete: RED missing ComparisonEvidenceService; GREEN 19 tests incl 5 MySQL, no fail/error/skip. runtime/sdd007a-task2-green.log. Production DB untouched.
Task3 Ruling: 本机全局Python的Starlette依赖与项目不匹配（要求httpx2），不改全局包；采用现有algorithm容器已验证依赖，在/tmp/sdd007a复制代码测试，不改运行中/app；先确认真正缺provider的RED。
Task3 Ruling: 现有全局ApiExceptionHandler解析错误统一400，沿用该契约，不为本片修改全系统为422；业务参数校验422。测试发现未知GET参数被Spring忽略，RED 422!=200，显式白名单/单值校验修正。
Task 3 tests: Java targeted21 PASS; Python full89 PASS/one existing warning; backend clean verify all modules BUILD SUCCESS, full counts recorded runtime/sdd007a-backend-counts.json. Script PowerShell syntax PASS. Internal filter already covers new paths; no production gateway route added.
Final review: 2 Important confirmed (stdout/stderr unbounded buffering; mismatched paired trade status); both RED->GREEN.
Final Ruling: JSON numeric RAW window regraded Important because exact decimal string contract; dedicated RawPoint strings after failing isTextual test.
Final Ruling: cache concurrency regraded Important because duplicate provider calls violate bounded budget; exclusive per-KeyPrefix lock, atomic saves, one-attempt reservation. Double lock/attempt tests pass.
Final Ruling: stream helper targets Linux algorithm container; selectors cumulative stdout+stderr limit before buffering, kill/reap on limit/timeout; original forecast unchanged.
Declined-to-judge: provider actual coverage/deployment preservation remain Task4, no success claim until real checks.
Review fix tests: Python targeted18 pass; PowerShell cache tests/syntax pass; Java RED expected true was false recorded, full GREEN next.
Final fix pass complete: backend clean verify557 PASS/0 fail/error/skip; full algorithm94 PASS/one existing warning; cache tests and syntax PASS. No additional external calls. Task 3 complete; Task 4 next.
Task 4 complete: 3 live paired acquisitions, 3 immutable records; 3/6/12 month complete 61/122/243 sessions for each stock; independent daily/hash audit PASS; saved replay3 PASS; real409/404/422 and auth401/gateway404 PASS. No paid AI/mail.
Task4 Ruling: before/after current head equality failed, investigated rather than forced; daily scheduler at20:00 updated all3 RAW heads to Oct12, tasks SUCCEEDED. Retained old3 RAW426 rows AVAILABLE and old6 forecast inputs unchanged; all comparison snapshots unchanged after refresh. Record head movement honestly as legitimate concurrent update.
Task4 deployment: only market/algorithm switched, V11 success, sixJava+algorithm UP, two rollback tags retained; rollback switch not tested.
Finish: keep existing codex branch/local commits and runtime acceptance evidence as authorized stable-directory workflow; no remote/push/merge or unrelated .superpowers cleanup.
