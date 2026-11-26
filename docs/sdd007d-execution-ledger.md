# SDD007D 执行交接

计划：docs/superpowers/plans/2026-10-13-sdd007d-comparison-refresh.md。
日期：2026-10-14。固定目录E:/aiStock/项目代码/stock-system；分支codex/sdd004-market-refresh。用户选择Native执行，不逐任务分派。唯一整片独立审查因额度失败；用户选择保存后等额度恢复。

## 任务状态

Task 1 complete：4442217..da022d3；19项新测试及旧比较回归共27项通过，市场全量163项通过。
Task 2 complete：da022d3..2351941；定向28项，市场全量174项通过；V12仅隔离库。
Task 3 complete：2351941..13ad0f4；定向29项，调度全量96项通过；V7仅隔离库。
Task 4 complete：13ad0f4..449c9f3；定向27项，调度全量106项通过；未知状态测试另有RED→GREEN。
Task 5历史进度（2026-10-14）：449c9f3..ea00fac；混合选源/旧结果回归、导出与审计工具、完整后端656项/前端148项/Python18项、旧程序隔离探针、镜像保存及部署基线已完成。独立审查、受控部署、真实首验、页面确认和正式启用未完成。

## Pre-flight

Task1 Request/RawWindow→Task2刷新，固定八字段与Python五字段分开。
Task1 Options→Task4计划，asOf毫秒精度与MS04独立DTO一致。
Task3 Context/Claim→Task4工作器，旧Task/Claim签名保留、attemptLimit固定。
Task2 Result→Task4回执，versionId为比较evidenceId，四字段与旧结果类型一致。
Task4 rollout→Task5首验，新开关false、首验上限1及总9次query，模型与邮件0。

## Rulings

Ruling: 保留已确认的固定目录与当前功能分支，不另建worktree；当前不是main/master。误判代价为隔离不足，但所有产品变更均选择性提交且部署尚未执行。
Ruling: 日历TTL由CalendarService.covering按现有配置控制，Query验证结构/hash/未来时间及重叠日历；避免另一套固定7天TTL。误判代价为就绪时间边界错误，实际服务TTL和陈旧日历测试已覆盖。
Ruling: V7需同步扩展V3的ck_task_type/ck_task_payload；精确增加COMPARISON_UPDATE，旧载荷要求保留。误判代价为无法创建任务或错误载荷被允许，隔离迁移/约束及回滚测试已覆盖。
Ruling: MS04只检查证券标识语法，ACTIVE与真实RAW就绪由MS02检查，避免跨库。误判代价为伪就绪任务，内部身份和客户端READY契约防护。
Ruling: 增加原尝试令牌保护的markComparisonUnknown，在未知POST后立即RECONCILING；明确RUNNING回执和旧任务保持原逻辑。误判代价为错误终止或重复采集，丢响应/404/预算测试覆盖。
Ruling: 混合选源和旧结果读取新增测试直接验证现有行为，不制造人为RED或改正确生产公式。误判代价为遗漏未来回归，新增完整测试已运行。
Ruling: 整片独立审查在部署前进行，真实首验属于Task5未完成部分；不因等待首验而先部署未审查代码。误判代价为流程阻塞，用户已确认等待额度恢复。
Ruling: 旧版本探针额外复制匹配的PredictionTaskClient，仅统一类名；避免旧Claim类型编译冲突。误判代价为探针偏离旧程序，实际旧源码与隔离SQL路径同时保留。

## 修正与证据

初始定向Maven与基线末尾曾短暂重叠，后续严格串行；最终全量通过。执行脚本UTF-16日志grep显示binary，不能以该文字当测试结果，已读取实际日志/XML汇总。首次只读保全SQL误用不存在forecast_result，按实际forecast_request修正后成功，没有数据写入。

测试日志与counts：runtime/sdd007d-backend-final*、runtime/sdd007d-frontend-*、runtime/sdd007d-python-final.log。
原数据基线：runtime/sdd007d-live-before.txt（仅计数和hash，不含账号凭据）。
镜像：runtime/sdd007d-rollback-images.json、runtime/sdd007d-rollback.override.yaml。
旧程序探针：runtime/sdd007d/legacy及runtime/sdd007d-rollback-probe.log。
审查包：.superpowers/sdd/2026-10-13-sdd007d-comparison-refresh/review-4442217..ea00fac.diff。
公开边界记录：docs/verification-sdd007d.md、docs/sdd007d-rollback.md。
保留本计划workspace以便恢复，不清理其他.superpowers目录。

## 当前交付与下一动作

2026-10-16：Task5实施、修复验证、部署、逐股首验、36项指标复核与正常登录页面验收完成；已启用后续正常周期。首验三个任务各1次、上下文上限1保持，新周期上限3。六Java健康、旧比较/解释哈希不变；新增验收三股比较一份，无AI调用。2026-10-17补齐记录。

保留现用分支codex/sdd004-market-refresh和固定目录，不合并、不删除分支；无remote不push。完整旧镜像/Flyway启动、长期连续运行、完整视觉与性能、CodeArts真实使用证据、MyBatis-Plus和自然预警完整邮件仍待办。先核对下一阶段优先级，不自动新增业务或付费调用。

Ruling: I1锁定当前读、I2实际领取周期过滤、I3发布操作序号与三周期实际选源均维持Important并完成一次修复轮次，无第二次独立复审。V12不改写，新增V13。误判将产生伪失败、超预算或伪CURRENT；真实隔离MySQL回归及全量660项验证通过。

Ruling: 首验预算按成功SDK query方法路径计9次，不冒充供应商底层包或账单证明。三个真实任务、操作回执与证据绑定，未人为改时钟/数据、未重试、MaaS/邮件0。正常周期在三股首验与页面通过后启用，依赖本机服务运行。
