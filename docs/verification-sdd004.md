# SDD-004 验证记录

2026-10-02。本阶段已完成已发布股票的行情持续更新闭环，按“计划→自查→实现→验收”推进。五服务、十三项业务范围不变，不代表全系统已完成。

## 真实数据验收

1. 原三只股票 SH600030、SH600276、SZ000002 各有 421 条真实 RAW 日线，截止 2026-09-24。首次连接 Baostock 失败，三个任务共九次尝试遇到 SOURCE_UNAVAILABLE/SOURCE_TIMEOUT；旧版本和原行情头保留，失败可追踪。
2. 当晚来源恢复后，对原失败任务进行显式重试，三项均为 SUCCEEDED。新版本各含 424 条日线，覆盖 2025-01-02—2026-09-30；各含 639 个连续自然日日历记录，覆盖 2025-01-02—2026-10-02，包括休市日。日历闭市不生成价格。
3. 在已部署 MS-02 容器中使用实际应用类及本服务数据库账号，运行只读 MarketQueryService 检查。三股的摘要与默认三个月日线查询均为 FRESH，checkedThrough=2026-09-30，无缺失交易日期、无更新错误，默认范围各返回 64 条数据，指标公式版本 tech-v1。这是实际查询层验收；浏览器当前需要重新登录，未声称完成本轮登录后的页面验收。
4. 开启本机每日计划与工作器后，计划实际生成 2026-10-02 的三项任务，全部 SUCCEEDED。与人工更新相同内容复用同一个行情版本；三个头 revision 均保持 2，版本总数为 6（原三版与新三版），未重复建立数据版本。
5. 重启调度服务后仍是六项任务、十五条尝试记录（首次失败九次、手动重试三次、每日任务三次），没有重复生成同日计划，原失败尝试仍保留。未来每天 20 点后的新任务会继续执行，能否获得新数据仍取决于外部源；本次验收不保证数据源持续可用。
6. 当前版本：SH600030 为 35dbd0a1-8746-474e-9f7b-845f3c0cc29b；SH600276 为 18972be0-a940-49e1-9d37-3b44d98a3643；SZ000002 为 1ef6cd72-8527-4a8d-88f1-b62155a9641c。旧版本仍各为 421 条，未覆盖修改。

## 自动化与容器验证

1. 最终全后端命令：scripts/check-auth-task1.ps1，内部运行 Maven -B -Pauth-integration,cli-build clean verify。结果 140 项测试，失败 0、错误 0、跳过 0；实际 MySQL 测试使用独立临时测试库，不使用现用业务卷。
2. Python 固定容器最终 18 项测试通过，涵盖连续日历、空数据证据、闭市行情拒绝、SDK 错误分类及有界采集。宿主机依赖差异未通过修改全局依赖解决；测试使用工程容器依赖。Starlette 弃用提示不影响通过结果。
3. 前端现有 18 项测试、严格 TypeScript 检查与生产构建通过。本阶段未修改前端。ECharts/Element Plus 包体提示仍列 TODO-03。
4. 最终 Compose 联调（check-compose.ps1 -SkipFault）通过：五服务数据库健康、Nacos 注册、Feign 调用、五库账号隔离、内部身份检查、网关隐藏内部接口、未配置模型时拒绝推理。重启后第一次检查赶在 Nacos 注册前失败，服务注册就绪后重跑全部通过；未把这次短暂状态掩盖成通过。
5. 新回执 GET 未提供身份为 401，经网关为 404；服务凭证未输出。MS-02/MS-04/算法镜像已重建并部署，现用数据卷保留。
6. 一次独立整体审查提出五项 Important，无 Critical；已逐项复现失败、修复并跑最终全套。详细见 [审查及修复](sdd004-review-and-fixes.md)。未再次委托审查，不将测试通过写成审查者复审批准。

## 证据位置

忽略目录 runtime/sdd004 中保存 final-backend.log、final-test-totals.json、review-python-green.log、frontend-tests.log、frontend-build.log、各 RED/GREEN 日志、real-refresh.json、heads-after-failure.tsv、final-receipt-reconciliation.json、recovered-tasks-observation.json、recovered-heads.tsv、live-query-freshness.log、daily-tasks-observation.tsv、daily-tasks-after-restart.tsv、deduplicated-heads-and-originals.tsv、final-enabled-plan.txt、final-compose-verification.log。日志无 Token 和密码，原始行情及运行日志不提交 Git。

## 使用与边界

内部脚本 scripts/refresh-market.ps1：List 查看任务；Submit 为已有真实行情股票提交更新；Get 查看尝试；Retry 仅允许失败任务；Cancel 仅允许排队/等待重试任务。身份只从被忽略的 .env 读取。

可提交的 .env.example 默认关闭自动运行，避免新部署在未核验来源时自行采集。本机忽略的 .env 已显式开启 MARKET_WORKER_ENABLED、MARKET_PLAN_ENABLED，股票列表为 MARKET_PLAN_INSTRUMENTS=SH600030,SH600276,SZ000002。计划按北京时间 20 点计算最新到期日，早于 20 点补前一天，重复触发幂等。停机后只补最新到期日，不自动补齐所有停机日期；完整快照仍覆盖原历史起点。

运行中不强制取消；结果不确定时先查 MS-02 执行回执，保持 RECONCILING，不能在不知是否成功时盲目重试。数据非法或全历史空返回拒绝发布，保留旧行情与错误提示。

管理员任务页面、更多股票基础资料、正式模型、五交易日预测和 MaaS 分析仍待后续阶段。当前本机开发环境不等同生产环境；Git 无远程，未声称已推送或完成远程 CI。CodeArts 无可核实源码修改，本轮代码由 Codex 完成。
