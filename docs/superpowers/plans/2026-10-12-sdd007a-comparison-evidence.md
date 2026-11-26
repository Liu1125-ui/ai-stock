# SDD007A 股票对比历史证据 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 为股票对比提供与固定RAW逐日核验的独立后复权历史证据。

**Architecture:** MS-02拥有历史证据与迁移；Python仅产候选，无业务库密码。内部接口受SERVICE_TOKEN保护；不改变预测60日协议。B片比较计算/历史页面及C片AI分别制定后续计划。

**Tech Stack:** Spring Boot、JdbcTemplate、MySQL、Flyway、JUnit5；FastAPI、Decimal、pytest；现有Docker Compose。

**Spec:** docs/superpowers/specs/2026-10-12-sdd007-stock-comparison-design.md

**Audit:** docs/sdd007-data-audit-20261012.md

**Status:** 已获用户批准并执行，Task1—4完成，含一次独立审查修复；验收见docs/verification-sdd007a.md。沿用当前会话主代理执行。

## Global Constraints
- 13业务、五服务、两角色不变，不调整任务书排期。
- 2—3只股票，最多一年，Asia/Shanghai自然月/年回推。
- 同baostock、BACKWARD口径；RAW不可改标签或拼接旧复权快照。
- 固定RAW版本、完整日历、内容哈希；不跨服务读库。
- 数值字符串传输，十进制核对，缺失不是0。
- 新代码目录README说明作用和实现方法。
- 不调用MaaS、不发送邮件、不启用后台邮件开关。
- 实际采集最多3股各一次成对采集，每股RAW/BACKWARD/日历共3次SDK查询；失败停止，不无限重试。
- 每任务选择性提交，不提交.env/runtime；无remote不push。
- 本片实现不等于完整股票对比完成；CodeArts实际证据后补，不能虚称已使用。

## Review Focus
1. RAW头采集中变化：仍匹配指定不可变RAW，不偷换版本；Task2。
2. 缺失/停牌被填充：保留逐日状态，不造数据；Task1。
3. 同操作键改内容：409且不覆盖；Task2。
4. 认证/矩阵路径/body上限绕过：内部接口始终保护，网关不公开；Task3。
5. 年边界、未来日期、异常精度及无界响应：严格日期及数值校验、有界解析；Task1/3。

## Task 1：候选契约与逐日验证
**Files**
- Create backend/stock-market-service/src/main/java/com/aistock/market/comparison/ComparisonEvidenceRecords.java
- Create同目录ComparisonEvidenceValidator.java、README.md
- Test backend/stock-market-service/src/test/java/com/aistock/market/comparison/ComparisonEvidenceValidatorTest.java、README.md

**Interfaces**
- Candidate(String operationId,String instrumentId,UUID rawVersionId,LocalDate from,LocalDate to,String provider,String definition,Instant fetchedAt,String sourcePayloadHash,List<Point> points,List<TradingDay> calendar)
- Point(LocalDate date,BigDecimal rawClose,BigDecimal backwardClose,BigDecimal volumeShares,int tradeStatus)
- DayEvidence(LocalDate date,String priceState,String backwardClose,String volumeState,String volumeShares)
- Validated(Candidate candidate,List<DayEvidence> days,String calendarHash,String contentHash)
- ComparisonEvidenceValidator(Clock); Validated validate(Candidate,List<RawBar>)
- definition=baostock-comparison-backward-close-v1，priceState和volumeState分别VERIFIED/MISSING/SUSPENDED。
- 枚举全部开市日，缺候选保留MISSING；停牌SUSPENDED。成交量缺失只影响volumeState。
- 候选RAW收盘、成交量、状态逐项与指定RAW比对；RAW本身无对应日却存在候选则拒绝。compareTo而非展示舍入。
- 序列不重复、有序，日历连续/from至to完整；闭市日无行情。最多370点、367日历日，to不得未来，from不得早于to.minusYears(1)。
- 后复权正数且精度28,12，成交量非负或null；哈希使用规范化Decimal/日期/完整日历/RAW引用，不信任客户端内容哈希。

- [x] RED：固定Clock和手工fixture写测试，不以真实行情作期望值：
~~~java
assertEquals("MISSING", validate(withMissingVolume()).days().get(0).volumeState());
assertEquals("VERIFIED", validate(withMissingVolume()).days().get(0).priceState());
assertThrows(BusinessException.class, () -> validate(withDifferentRawClose()));
~~~
另测停牌、开市日缺失、重复乱序、闰日年边界、未来日期、负数/异常精度、日历缺口。
- [x] 运行定向测试确认RED：
~~~powershell
& 'C:\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd' -B -f backend/pom.xml -pl stock-market-service -am '-Dtest=ComparisonEvidenceValidatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
~~~
- [x] 最小实现TreeMap每日索引、BigDecimal比对、SHA256，失败BusinessException受控返回。
- [x] 同命令GREEN，检查无前向填充、无缺失转0。
- [x] 仅records/validator/tests/README提交：feat: validate immutable comparison history evidence。

## Task 2：不可变仓库与版本服务
**Files**
- Create backend/stock-market-service/src/main/resources/db/migration/V11__comparison_evidence.sql
- Create comparison/ComparisonEvidenceRepository.java、ComparisonEvidenceService.java
- Test comparison/ComparisonEvidenceMysqlTest.java
- Modify migration/README.md、comparison/README.md

**Interfaces**
- Publication(UUID evidenceId,String contentHash,boolean replayed)
- Evidence(UUID evidenceId,String instrumentId,UUID rawVersionId,String rawContentHash,String provider,String basis,LocalDate from,LocalDate to,String calendarHash,String contentHash,Instant capturedAt,List<DayEvidence> days)
- service.publish(Candidate):Publication；service.version(UUID):Evidence
- comparison_evidence_operation(operation_id VARCHAR128 PK,request_hash CHAR64,evidence_id CHAR36,created_at DATETIME3)
- comparison_evidence_version(evidence_id CHAR36 PK,instrument_id VARCHAR64,raw_version_id CHAR36,content_hash CHAR64 UNIQUE,evidence_json JSON,created_at DATETIME3)
- 外键仅本库RAW。完整序列保存JSON供独立复核，限一年。
- 同键同内容replayed，不同内容409。事务锁operation，核对RAW为AVAILABLE/baostock/RAW/同股；验证或写入失败全回滚。
- canonical哈希包括RAW引用、范围、provider/definition、完整点与日历，防跨股去重。
- 固定旧RAW若采集时头移动仍可验证读取；不悄悄切到新头。返回实际版本/截止信息。

- [x] RED：隔离MySQL测试幂等、409、并发重复、故障回滚、头移动、未知版本404、旧预测输入不变。
~~~java
var first = service.publish(candidate);
assertEquals(first.evidenceId(), service.publish(candidate).evidenceId());
assertThrows(BusinessException.class, () -> service.publish(changedSameOperation));
~~~
- [x] 通过scripts/check-auth-task1.ps1注入隔离库执行；不可生产库跑测试。
- [x] 最小迁移/事务/服务实现，JSON坏记录受控失败，迁移编号实现前再核对。
- [x] GREEN后仅本任务文件提交：feat: persist versioned comparison evidence。

## Task 3：受保护接口与有界采集
**Files**
- Create comparison/ComparisonEvidenceController.java、test目录ComparisonEvidenceHttpTest.java
- 检查现有market/ingestion/CandidateRequestFilter.java，若已统一覆盖无需重复改认证，仅补测试
- Create backend/algorithm/app/comparison/provider.py、worker.py、README.md
- Create backend/algorithm/tests/test_comparison_provider.py
- Modify backend/algorithm/app/main.py、backend/algorithm/README.md
- Create scripts/prepare-comparison-evidence.ps1；Modify scripts/README.md

**Interfaces**
- GET /internal/v1/market/comparison-raw-window?instrumentId&from&to：固定rawVersionId/rawHash/provider/RAW/bars/calendar。
- POST /internal/v1/market/comparison-evidence：Candidate -> Publication。
- GET /internal/v1/market/comparison-evidence/{id}：Evidence。
- Python POST /internal/v1/market/comparison-collect：operationId/instrumentId/rawVersionId/start/end -> state/candidate/errorCode。
- ComparisonSource.collect(request)独立协议；可复用ForecastSource读取SDK字段/锁，不修改原forecast契约。
- 同次RAW adjustflag=3、BACKWARD adjustflag=1、独立日历start至end；最多3次SDK查询/股。
- 共享_COLLECT_SLOT、25秒子进程、AISTOCK_COMPARISON输出标记；输入/输出2MiB、370点/367日历上限。
- 脚本读取SERVICE_TOKEN不打印，先固定RAW后采集再发布，最多3股顺序，无自动重试。保存无密钥候选方便恢复，重复发布不重复采集。
- 原始行情单位成交股数，算法不得自行换手/千股，Java逐项对照RAW验证。

- [x] RED：HTTP未认证401、超body413、非法/未知参数422、未知版本404、matrix路径认证、网关不公开internal。
- [x] RED：fakeSDK测试3/1配对、代码/日期不一致拒绝、空数据/超行拒绝、busy/timeout不发布、forecast旧测试通过。
~~~python
result = ComparisonSource(sdk=fake_sdk).collect(request)
assert result["candidate"]["definition"] == "baostock-comparison-backward-close-v1"
assert fake_sdk.history_flags == ["3", "1"]
~~~
- [x] 实现controller/worker/脚本：构造器注入、认证先于采集，受控错误不泄露供应商响应。
- [x] GREEN：顺序运行scripts/check-auth-task1.ps1及现有Python解释器python -m pytest -q backend/algorithm/tests；先核对环境，不临时改全局依赖，不并发Maven。
- [x] 选择性提交：feat: expose protected comparison evidence collection。

## Task 4：真实核验与保全
**Files**
- Create docs/verification-sdd007a.md、docs/evidence/sdd007a-comparison-checks.json
- Modify docs/implementation-status.md、docs/pending-items.md

- [x] 保存部署前RAW头、预测输入版本与服务健康到runtime，不读账户或输出密码。
- [x] 保留当前market/algorithm镜像回滚标签，只部署受影响服务，不启用AI或邮件后台开关。
- [x] 固定2025-10-09至2026-10-09三股各一次成对真实采集，最多9次SDK查询；失败停止，fixture不算真实成功。
- [x] 按证据ID回读逐日核对RAW/复权/日历/缺失/哈希，分别审查3/6/12个月覆盖；端点存在不等于完整。
- [x] 同operation仅重复发布，409篡改测试；未授权请求拒绝。
- [x] 旧RAW头、预测输入、模型/用户偏好不修改，服务健康；本片无页面，不声称UI验收。
- [x] 文档写明通过/失败/未验，完整对比仍待B/C；自然预警邮件仍未端到端验收。
- [x] 仅文档提交；无remote不push。

## B/C后续衔接
B片独立计划定义MS-03本人快照与分页历史、共同t0/t1、公式版本/容差，每指标status/reason/observationCount；默认3个月/6个月/1年，加载防重复、错误保留选择、部分不足逐股显示、曲线断线。仅成交量缺失不禁止价格指标，价格缺口按严格规则禁止风险指标。手工复算/owner隔离/刷新不可变测试。
C片独立计划定义多股AI事实、解释生成和操作恢复、失败保留；点击才调用，关键数字一致，真实付费验收另按次数授权。
B/C不提前冻结迁移号，不假设A所有数据均可用，不新增业务。

## 自审
A片覆盖真实历史审查、版本日历固定、逐日验证、不可变存储、认证、供应商异常、有界真实验证及回滚保全。历史入口/页面状态/独立指标缺失由B交付，AI由C交付；没有删减总体需求。实现前审核本计划，执行后不得只凭代码测试宣称完整业务完成。
## 执行结论补充
A完成；复核与取舍见docs/sdd007a-execution-ledger.md。RAW头未保持静止：已有每日调度在20:00之后把三股更新至10月12日；原RAW和比较证据完整保留，既有预测输入未变。该结果取代Task4中笼统“头不修改”的前后对比预期，本片本身未写RAW头。B/C尚未实现。