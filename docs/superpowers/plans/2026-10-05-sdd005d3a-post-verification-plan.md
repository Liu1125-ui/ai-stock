# SDD-005D3A 真实验证证据与内部评价实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 对固定成功预测形成可追溯、不可覆盖的真实事后验证版本，不调用模型重新预测。

**Architecture:** MS-02拥有同源RAW/后复权配对证据和操作回执；MS-03读取证据、计算评价、保存追加版本及回执。采集复用Python ForecastSource，无新Python调度器。MS-04自动计划与本人页面由D3B独立接通，本计划完成后不宣称FR-06全业务完成。

**Tech Stack:** Spring Boot、JdbcTemplate/TransactionTemplate、MySQL/Flyway、Java BigDecimal、现有Python Baostock采集器、JUnit5及真实隔离MySQL。

**Spec:** docs/superpowers/specs/2026-10-05-sdd005d3-post-verification-design.md

## Global Constraints

- 五交易日固定预测、13业务、五业务服务、ADMIN/USER；股票工程不套用电商金额Integer规则。
- 原预测/input/model/目标日期不可覆盖；验证记录追加版本；每个服务只写自己数据库。
- 所有内部接口SERVICE_TOKEN在JSON解析前验证，网关不公开；真实密钥不提交。
- priceBasis=ORIGIN_SHARE_BASIS，源定义baostock-backward-close-over-raw-v1，评价规则origin-share-close-v1。
- 价格字符串；除法18位HALF_UP，实际价/比较6位HALF_UP；方向上/下/平，0有效点指标null。
- 配对采集35秒、证据读取10秒、执行预算90秒、租约120秒；完整响应128KiB、请求4KiB、禁重定向。
- 当前20点发布截止配置，目标日期不到期不采集实际价格，不创建假评价。
- 不自动发布模型，不改变原权重；代码来源Codex，实际真实正向验收需管理员发布及目标行情到期。
- 每个新自建代码目录README说明职责、实现方法与验证状态。

## Review Focus

1. 起点旧因子与目标新因子混用导致除权被算成预测错误：Task 1配对校验、Task 2拆股与整体因子等比缩放测试。
2. 日期已到但未过发布截止、停牌行有数字、数据缺失被算正确：Task 1日历状态、Task 2分母/空值、Task 3无到期跳过测试。
3. 新RAW头与独立采集RAW值不一致却发布证据：Task 1逐行匹配、不可变引用和失败保留测试。
4. 请求超时/重启/并发使相同点重复累计或旧操作回退当前验证：Task 3回执、唯一键、租约及指针测试。
5. 模型下线/个人隐藏后原成功预测无法验证或越权读取：Task 3历史资格测试，D3B另做本人读取与页面测试。

## Task 1：MS-02独立真实验证证据

Files:
- Create: backend/stock-market-service/src/main/java/com/aistock/market/realization/{RealizationRecords,RealizationValidator,RealizationRepository,RealizationService,RealizationController,RealizationCollector}.java
- Create: backend/stock-market-service/src/main/java/com/aistock/market/realization/README.md
- Create: backend/stock-market-service/src/main/resources/db/migration/V7__realization_evidence.sql
- Test: backend/stock-market-service/src/test/java/com/aistock/market/realization/{RealizationValidatorTest,RealizationMysqlTest,RealizationHttpTest}.java 及README.md
- Modify: MS-02配置、既有认证路径规则（只在确需时）；不修改V5/V6历史迁移和原forecast输入定义。

Interfaces: POST /internal/v1/market/realizations/prepare；GET固定version；GET operations回执，字段以spec为准。复用ForecastSource已有operationId/instrumentId/rawVersionId/start/end/calendarThrough协议；新服务校验实际证据，不走60行ForecastInputValidator。

- [x] 写RED：RAW配对身份/日期/数值不一致、缺起点、零因子、未来值、停牌/休市、日历不完整、不同stock/hash均拒绝或准确分点。
- [x] 写隔离MySQL RED：同操作重放/参数冲突、同内容去重、不可变旧证据、过期不发布、源失败保留旧版本。
- [x] 运行测试确认预期失败；记录命令及失败原因，不因测试库连接错误算RED。
- [x] 最小实现schema/校验/有界采集/事务发布回执；网络不在事务内，数据内聚归属MS-02。
- [x] 对未知源字段、超大正文、redirect、响应体停顿、未知operation、认证前坏JSON做HTTP边界测试。
- [x] 跑受影响模块和Python既有采集测试（如果修改采集代码则完整算法测试）；明确fixture不是实际外部源。
- [x] 自查变更和秘密排除，仅git add本任务文件并commit。

## Task 2：MS-03纯评价计算

Files:
- Create: backend/stock-prediction-service/src/main/java/com/aistock/prediction/verification/{VerificationRecords,VerificationCalculator,VerificationValidator}.java 及README.md
- Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/verification/VerificationCalculatorTest.java 及README.md

Interfaces: 输入固定成功D2快照、MS-02固定realization证据；输出五点状态及原点归一化/价格误差、方向计数和独立朴素基线。该计算器不读数据库、不发HTTP、不调用Transformer；规则标记origin-share-close-v1。

- [x] 写RED：不复权/拆股/同一批因子等比缩放/起点RAW被后续修订等数学例子。
- [x] 写RED：五点完整、部分到期、全部缺失、停牌有价格、平盘、零分母样本、基线零误差、6位精度边界、输入身份错配。
- [x] 手算对照：P0=100、Rt=50、q0=1、qt=2得到At=100；P0=100、Pt=105、At=102得到比例绝对误差0.03和基线0.02。
- [x] 运行RED后实现确定性BigDecimal计算；只有VERIFIED参与分母，不显示伪“准确率=1-MAE”。
- [x] 跑纯计算GREEN；检查五点顺序/单位/空值/规则版本及无未来输入使用。
- [x] 仅提交本任务文件。

## Task 3：MS-03内部不可变验证流程

Files:
- Create: backend/stock-prediction-service/src/main/java/com/aistock/prediction/verification/{VerificationRepository,VerificationService,VerificationController,VerificationMarketClient}.java
- Create: backend/stock-prediction-service/src/main/resources/db/migration/V4__forecast_verification.sql
- Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/verification/{VerificationMysqlTest,VerificationHttpTest,VerificationDependencyTest}.java
- Modify: MS-03配置、verification/README.md、相关migration/README.md；必要时抽取既有BoundedForecastHttp有限传输，保留D2回归。

Interfaces: POST /internal/v1/predictions/{id}/verify；GET /internal/v1/prediction-verifications/operations/{operationId}。只接受operationId/dueThrough/leaseUntil；预测及价格由本库成功快照取得，客户端不提交实际价格、userId、ruleVersion或模型发布标记。

- [x] 隔离库RED：非成功拒绝；已下线模型/隐藏预测允许验证；同操作唯一执行、参数冲突、同证据不重复计数。
- [x] RED：新证据追加旧记录保留；旧dueThrough/旧租约不能回退指针；重启查询回执；源超时保留上次结果；没有目标到期返回SKIPPED_NOT_DUE、null版本、不采集。
- [x] HTTP RED：未授权/伪身份/坏JSON先认证、超大请求、错股票/日期/源/哈希、deadline正文停顿、redirect、外部失败不得产生成功评价。
- [x] 实现短事务claim/finalize与固定证据读取、数学调用；网络在事务外；不能对原forecast_request.snapshot执行UPDATE。
- [x] 运行后端受影响模块回归；完成后scripts/check-auth-task1.ps1完整核验，确保D2权限/幂等及MS-02旧行情未退化。
- [x] 一次新独立只读最终审查：executing-plans要求的reviewer；Critical/Important只做一次TDD修复，不为文档或每个类反复派代理。
- [x] 构建部署先验证jar再重建容器，实际健康/迁移/认证拒绝/内部网关404；模拟成功只放测试库，不向业务库伪造预测。
- [x] 如管理员已发布，用实际登录用户生成预测核验D2；目标日未到只验跳过/待验证，未来价格不能造。若尚无真实预测，明确保留该验收未完成。
- [x] 保存verification-sdd005d3a.md及证据JSON、implementation-status/pending-items更新，逐功能提交；无remote不push，不声称远程CI。

## 完成定义与D3B交接

D3A测试与实际内部边界通过后才算内部验证实现完成。仍待D3B：MS-04任务类型/唯一键/租约回执/每日计划、本人验证API、历史详情逐日实际曲线及指标、账号切换与乱序响应保护。两个阶段均完成且真实目标日有有效来源数据，才能声称FR-06事后验证真实闭环验收。

## 执行状态

2026-10-05 Task1/2/3完成内部实现、一次独立审查与一次修复、236项完整后端回归和部署。Python既有采集8项、实际MS-02→MS-03只读契约探针1项通过。当前用户预测无到期点，真实调用SKIPPED_NOT_DUE且无评价版本；历史五开市日只用于真实证据采集/读取，不伪造用户预测或未来准确度。

Ruling及RED/GREEN日志见docs/sdd005d3a-progress.md，正式证据见docs/verification-sdd005d3a.md。测试类命名按实际职责使用VerificationMarketClientTest；未修改Python，回归其既有采集器。D3B及FR-06全业务仍待实施与到期验收。
