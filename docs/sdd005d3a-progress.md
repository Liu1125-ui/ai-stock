# SDD ledger — plan: docs/superpowers/plans/2026-10-05-sdd005d3a-post-verification-plan.md

## 2026-10-05 当前切片：Task 2纯评价计算

BASE：4898675。用户继续“下一步”，沿用按步骤推进、固定目录、作者执行授权。

Ruling: 先执行Task 2并定义纯计算证据DTO，再实施Task 1/3 — Task 2不依赖网络/数据库，先固定数学与分母可减少后续集成误判；本轮范围是可独立测试的纯计算切片 — 若DTO与MS-02证据不同须显式映射，不能静默混用。主计划没有标全部完成。

Pre-flight: Task 1产生真实配对证据，Task 2消费股票/起点/目标日期/截止日与RAW/后复权实际点，Task 3读取固定成功快照并调用计算器。DTO不是来源可信性证明，版本hash、完整日历及发布租约仍归Task 1/3。禁止将fixture评价写入业务库。沿用当前工作目录与codex分支，不复制密钥或原权重到新worktree。

Task 2: RED — 新纯计算接口缺失造成testCompile失败，日志runtime/sdd005d3-calculator-red.log；随后实现记录、严格校验与BigDecimal计算。
Task 2: GREEN — VerificationCalculatorTest 8项，失败0/错误0，runtime/sdd005d3-calculator-green.log。
Task 2: 回归与最终一次独立审查进行中。审查范围仅纯计算切片，未把后续真实采集/存储/调度/页面缺失冒充本切片已实现。

## 未完成

Task 1真实证据采集发布/版本/回执，Task 3追加验证存储/内部编排未执行。D3B自动计划和本人验证页面未执行；FR-06事后验证未验收。当前真实预测五目标日未到，不产生未来实际行情或宣称准确性提升。

## 唯一最终审查与一次修复

独立reviewer verification_calculator_review：Critical 0、Important 3、Minor 1。三个Important为RMSE平方小量提前舍入、固定原点分母精度丢失、step整数溢出；新增四项测试（含两有效点手算对照），RED阶段12项中3项失败、9项通过，准确复现三个问题。一次修复保留开根前平方精度、归一化使用原始P0、step增加canConvertToInt。最终完整回归结果待本次命令结束记录；不启动第二轮审查。

Deferred Minor: Result.Point未提供单独带符号价格误差字段，目前有固定referencePrice/actualPrice和absoluteError；进入Task 3/页面契约前补signed error并测试，不将其默认为已实现。当前模块不对外发布JSON或用户结果，因此不改现有页面、不声称全部评价输出契约完成。

Task 2: 计算主体完成，最终12项纯计算及预测服务51项全回归（含该12项）失败0/错误0/跳过0，Maven verify打包成功；common2项另通过。见runtime/sdd005d3-calculator-final-green.log及docs/evidence/sdd005d3-calculator-20261005.json。三个Important均RED→GREEN，一Minor延期。当前纯计算未接API，因此未部署或更改运行中真实预测/行情。

## 继续执行Task 1/3

Task 1: 真实证据、V7、短事务回执、固定RAW逐行核对和有界复用采集完成实现；9项新测试通过，随后新增废弃RUNNING租约读取过期回归，RED为预期ATTEMPT_EXPIRED实际null。
Task 3: V4、本库成功快照、追加版本与内部接口完成实现；4项MySQL新测试通过；HTTP缺类RED后身份前置与4KiB请求通过；客户端缺类RED后固定版本读取/错操作身份/redirect/128KiB/停顿正文测试通过。
Task 2延期字段：进入Task 3之前补signedError和十进制字符串输出。新测试RED为预期-2.000000实际缺字段，已最小实现；最终完整回归执行中。
Ruling: 内部当前结果只读增加GET /internal/v1/predictions/{id}/verification — 方便服务内部核验固定当前结果，统一服务身份认证且网关不公开；没有新增用户业务或替代本人权限API — 若消费者误当公开API，D3B须保持用户会话与归属检查。
Ruling: 本轮来源适配未改Python源码，复用现有配对采集协议 — Java新增35秒有界调用与更严格真实证据校验，Python既有采集测试另回归 — 外部源限频/缺失不能靠fixture保证，正式正向需真实调用留证。

完整回归：scripts/check-auth-task1.ps1 clean verify通过，日志runtime/sdd005d3-full-final.log。补充RealizationValidatorTest 2项通过；Python既有采集8项通过。首次完整回归暴露JDBC Timestamp/LocalDateTime驱动差异，原废弃租约测试RED复现，统一SQL截止比较后GREEN；并发同操作仅采集一次、失败保留已有结果测试通过。Task 1/3完成实现与测试，最终一次只读审查及部署证据待执行。

## Task 1/3唯一独立审查与一次修复

reviewer verification_persistence_review：Critical0、Important2、Minor0。确认日历本体未保存及90秒总预算未约束；新增两项隔离MySQL测试RED准确复现（各8项中1失败，其余通过；为一次运行两个模块，RED命令特意maven.test.failure.ignore=true，BUILD SUCCESS不当测试通过）。一次修复保存完整日历并在MS-03复核calendarHash；从请求开始统一90秒截止，将剩余预算用于HTTP、短事务及最终发布检查，MS-02 lease也截到执行截止。原120秒租约不代替90秒预算。两项已GREEN，最终全回归待执行。

补充审查建议的不同operation同内容并发、同截止旧operation晚完成不回退、日历篡改及剩余预算正文停顿回归；这些是附加覆盖，不冒称最初已失败。
Ruling: 将审查未判断的D3B本人权限/页面/调度留到下一切片 — 已在设计中明确分阶段，内部接口保持认证且网关不公开 — 若误称全业务完成会漏验权限与计划，故FR-06继续未完成。
Ruling: 真实未来准确性只在10月8日后有有效目标行情时评价 — 当前不生成假数据或成功率 — 成本是本轮只验SKIPPED_NOT_DUE与历史采集能力，实际完整评价须等待。
Ruling: 原Task 2数学主体沿用此前独立审查和本轮全回归 — 本轮审查新增输出与集成，没有再重复数学审查 — 仍以计算测试和真实来源结果为依据，不能保证预测质量。
Ruling: 部署/迁移/网关边界以随后实际运行证据判定 — 审查源码不能替代运行态验收 — 若部署不通过则保留未完成状态。

修复过程补充：不同operation相同内容并发测试在全回归真实RED（预期AVAILABLE，实际一方FAILED）。重复读事务先查不到内容，另一事务去重插入胜出后，旧一致性快照仍看不到胜出版本；改插入后的固定版本查询为FOR UPDATE当前读。此为同一修复轮新增实证，日志runtime/sdd005d3-review-full-final.log；受影响9项行情MySQL、10项预测MySQL和3项客户端测试已GREEN，日志runtime/sdd005d3-concurrent-fix.log。未启动再次独立审查。

Task 1/3: 最终完整236项clean verify，0失败/错误/跳过，runtime/sdd005d3-final-verified.log；Python采集8项已通过。唯一审查两Important均RED→GREEN，同轮并发新增实证修复，未重复派审。
Task 1/3: 真实部署与内部验收完成：健康UP、V7/V4、权限401/未知回执404/网关内部404；当前用户预测SKIPPED_NOT_DUE/null版本、重放一致、评价版本0、成功快照hash不变。历史9月21日起五开市日证据AVAILABLE、完整日历/内容哈希与保存一致；MS-03 Java真实读取探针1项通过。首轮日期误含9月25日休市，正确排除后修探针日期，无应用代码修改。
Ruling: 实际跨服务探针显式运行RealSourceContractProbe，不纳入默认CI自动匹配 — 该测试依赖本机真实保存样本，只读取而不伪造预测/写库；默认自动测试236项保持可重复 — 成本是他机需先生成脱敏真实样本才可重复实际契约探针。
Ruling: 收尾保留现有codex分支与本机提交，不合并或push — 固定目录、连续自主开发已获授权，项目无remote且用户未要求合并 — 成本是远程托管与正式合并仍未完成，不冒称远程CI。
Final: 无本轮延期Minor；先前signedError延期已补齐。D3B/本人页面及真实未来评价仍待后续，FR-06整体未完成。完整证据docs/evidence/sdd005d3a-20261005.json。

Task 1: complete（e4edff7实现、94a2705审查修复；最终236项完整回归、真实MS-02证据与V7部署通过）。Task 2: complete（7c38c96数学主体，ea48f8a输出契约；当前13项纯计算包含在完整回归）。Task 3: complete（ea48f8a实现、94a2705修复、真实契约手工探针；V4/内部身份/无到期跳过与原快照保留通过）。D3B与FR-06全业务仍未完成。
