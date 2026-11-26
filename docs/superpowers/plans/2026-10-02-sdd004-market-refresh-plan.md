# SDD-004 行情持续更新实施计划
> REQUIRED: executing-plans（本会话顺序实施）。先规格、自查，再TDD。
Spec: docs/superpowers/specs/2026-10-02-sdd004-market-refresh-design.md
Base: 80ae6c4。分支codex/sdd004-market-refresh；继续固定工作目录，不另建重复工程。

## Global Constraints
独立五服务数据库；不改变13项业务；不得丢失旧版本、绕过过期令牌或伪造可用行情。代码目录带README。测试日志存忽略runtime/sdd004；不提交凭证。
## Task 1: Baostock连续日历与采集容量
Files: backend/algorithm/app/market/providers.py, app/main.py, tests/test_market_provider.py, tests/test_market_http.py。
Produces: collect候选calendar连续自然日、闭市布尔值；SOURCE_INVALID/SOURCE_BUSY不带candidate。
Steps: 增加缺失日历、闭市行情、日期越界、错误SDK及有界容量失败测试；运行python pytest看RED；实现query_trade_dates与父进程Semaphore；全12+新增测试GREEN；commit。
Expected: RED针对calendar为空/未拒绝；GREEN全部通过。命令docker compose -f deploy/compose.yaml run --rm --no-deps algorithm python -m pytest tests -q（先build algorithm保证源码更新）。
## Task 2: MS-02可信刷新与截止发布
Files: market/refresh/{RefreshRequest,RefreshResult,RefreshRepository,RefreshService,AlgorithmCollectorClient,RefreshController}.java及README；db/migration/V4__refresh_receipt.sql；ingestion PublicationService.java/CandidateRequestFilter.java；version MarketVersionRepository.java；application.yml；refresh/RefreshMysqlTest.java。
Consumes Task1 candidates; Produces unified refresh response.
Steps: 独立MySQL测试准备；添加成功/相同回执/冲突/不完整日历/源故障旧头不变/过期发布测试RED；实现回执、受控HTTP候选解析、范围完整性、事务发布fence；运行 -pl stock-market-service -am -Pauth-integration,cli-build test GREEN；commit。
Expected: 过期回执不发布；真实头保留，重复完成回执复用。
## Task 3: MS-04持久任务与恢复
Files: scheduler/task/{TaskRepository,TaskWorker,TaskController,TaskSecurityFilter,MarketRefreshClient,DailyMarketPlan}.java及README；migration/V1__market_tasks.sql；pom/application及启动类；task/TaskMysqlTest.java；scripts/start-auth-test-db.ps1/check-auth-task1.ps1；.github/workflows/user-business-ci.yml。
Consumes Task2 response; Produces task states/versionId reference.
Steps: 并发claim唯一/幂等key冲突/超时租约回收/旧结果失效/可重试源故障/失败及排队取消/同日计划去重测试RED；建独立stock_scheduler_test；实现短事务行锁及受控Feign；GREEN；commit。
Expected: 无跨库依赖或网络调用在事务内；所有旧令牌complete不能覆盖新尝试；核对同一尝试的确定成功回执可恢复结果。
## Task 4: 容器联调、真实验收与记录
Files: deploy/compose.yaml；scripts/refresh-market.ps1；docs/verification-sdd004.md, sdd004-execution-ledger.md, implementation-status.md, pending-items.md。
Steps: 全后端clean verify、Python全测试、前端既有测试build；重建market/scheduler/algorithm；健康检查；真实3股票每日更新任务/重复请求检查；失败旧版本与重启持久记录；检查内部路径不暴露；一次独立whole-branch review，Important问题RED→GREEN+全套；文档逐项写实际结果，未验证不报完成；commit。
Expected: 任务终态与发布版本对应；外部失败明确记录，不能改用合成样本充当通过。
## Review Focus
过期线程发布的事务竞态；响应丢失后回执恢复；连续日历与停牌缺行；不完整范围缩短历史；任务重复触发/多worker/重启；远端畸形响应与秘密泄漏；Feign期限超过租约；网关内部隔离；发布成功但调度超时的可观测性。

## 执行结果与审查补充
2026-10-02 Tasks 1—4 已完成。审查新增受认证GET回执、RECONCILING/UNKNOWN、发布与回执原子提交、阻塞写入后期限检查、全历史空返回拒绝及上午补昨日计划。MySQL迁移使用新增V2保留原V1校验，不改已运行历史迁移。
最终140后端/18Python/18前端测试与构建通过；真实三股更新、查询层FRESH、同内容复用、每日计划及重启验收通过。详细证据见../../verification-sdd004.md与../../sdd004-review-and-fixes.md。浏览器当前未登录，未把实际查询层结果写成登录后页面验收。
