# SDD005E2a 单股AI行情分析实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 在现有单股详情页实现有数据依据、可保存、可重读的AI行情与指标分析。
**Architecture:** MS02通过服务认证提供固定版本指标证据，MS03整理事实、持久化操作与调用MaaS，网关转发本人接口，Vue页面按股票和范围展示。与预测解释共享一个调用槽，不跨库取数。
**Tech Stack:** Spring Boot/MySQL/Flyway/JUnit5、Vue3/TypeScript/Vitest、已有Huawei MaaS客户端。
**Spec:** docs/superpowers/specs/2026-10-07-sdd005e2-stock-ai-analysis-design.md（用户已确认；用户已确认本计划）。
**Execution:** 本会话直接实施，不新建子对话；固定现有项目分支、顺序执行数据库测试，不并行清理测试数据。无remote不push。

## Global Constraints

- 保持13项业务、五业务服务、ADMIN/USER；不替代FR09完整问答。
- E2a分析行情/指标；E2b后续接入本人预测引用，不能把未实现项标为完成。
- 默认三个月；范围最多十二个月；RAW口径、tech-v1、grounded-stock-analysis-v1分别保存。
- 单次MaaS完整截止35秒、响应上限128KiB；网络调用不占数据库事务；租约50秒。
- 仅本人记录、CSRF/Origin、内部令牌认证；密钥只在忽略配置中，不打印/提交。
- 日期、代码、数值与方向来自事实占位符；纯文本展示，核验失败保留旧结果。

## Review Focus

- 切股票/周期与旧响应交错：不得显示前一股票的分析。
- 有RAW日线但指标为空、停牌或新鲜度未知：仍可解释历史，明确不足与日期，不能伪造实时信号。
- 行情版本更新或标记不可用：输入固定版本，不悄悄替换最新版本。
- 云端超时后请求回放：同操作号不能重复收费；重放与租约恢复测试必须通过。
- E1已有占槽或过期未完成：E2不能绕过单全局槽，迟到E1结果不能释放E2的新槽。

## Task 1 固定行情证据和确定性事实

**Files:**
- Create MS02 `src/main/java/com/aistock/market/quotation/AnalysisEvidenceController.java`。
- Modify MS02内部身份过滤器的路径覆盖与`quotation/README.md`。
- Create MS03 `src/main/java/com/aistock/prediction/analysis/{AnalysisMarketClient,HttpAnalysisMarketClient,AnalysisFacts,README}.java`；README实际为README.md。
- Create MS02 `src/test/java/com/aistock/market/quotation/AnalysisEvidenceHttpTest.java`。
- Create MS03 `src/test/java/com/aistock/prediction/analysis/{AnalysisFactsTest,AnalysisMarketClientTest}.java`。

**Interfaces:** `AnalysisMarketClient.evidence(String instrumentId,LocalDate from,LocalDate to,UUID versionId): JsonNode`；`AnalysisFacts.build(JsonNode evidence,String providerModel,ObjectMapper mapper): Facts`；`Facts(Map<String,String> values,JsonNode context,String hash)`。

- [ ] 写RED测试：固定RAW版本日期有序、唯一；字段错股票/错版本、空bars、非法价格、无效UUID拒绝；指标null显示样本不足，不能变0；RSI0/30/70/100合法、越界拒绝。陈旧/未知数据仅产生历史限制事实。
```java
assertEquals("指标样本不足",facts.values().get("RSI_STATE"));
assertTrue(facts.values().get("BASIS_LIMIT").contains("未复权"));
assertThrows(BusinessException.class,()->AnalysisFacts.build(wrongStockEvidence,"test-model",mapper));
```
- [ ] 执行聚焦测试，确认实现缺失而失败，日志保存runtime/e2a-task1-red.log。
- [ ] 内部控制器复用`MarketQueryService.daily(id,from,to,"RAW",versionId)`；路径统一现有内部协议`/internal/v1/market/analysis-evidence/{instrumentId}`，同步设计说明这一命名细化。认证必须先于业务执行；仅GET，请求日期和版本必填。响应使用统一ApiResponse与traceId。
- [ ] HTTP客户端只调用配置MS02、编码路径与参数、X-Service-Token；2秒连接、5秒完整响应截止、512KiB上限、禁止重定向。只接受200/OK结构与正确股票/版本；timeout、大小超限、错误身份和坏JSON转换安全BusinessException。
- [ ] 事实包含代码、实际区间、最后有效日/价格、MA5/10/20、DIF/DEA/MACD/RSI数值、确定性关系、缺失指标说明、数据新鲜度与RAW限制。用BigDecimal比较，不由单点声称交叉事件；上下文保存来源、版本、公式与提示版本。SHA256为排序明确的上下文JSON，生成时间不参与缓存hash。
- [ ] GREEN：局部客户端假HTTP服务器与指标边界测试通过，内部API错误令牌401且调用计数0；同固定数据hash稳定。README说明作用和实现方法，提交Task1文件。

## Task 2 共享调用槽、本人分析记录与MaaS配置

**Files:**
- Create MS03 `src/main/resources/db/migration/V6__stock_analysis.sql`。
- Create MS03 `src/main/java/com/aistock/prediction/maas/{MaasOperationGate,README}.java`（README为.md）。
- Modify `explanation/ExplanationRepository.java`、`explanation/HttpMaasClient.java`，保留现有公开调用与预测解释提示。
- Create `analysis/{AnalysisRepository,AnalysisService,AnalysisConfiguration,AnalysisController}.java`。
- Modify `security/PredictionSecurityConfiguration.java`。
- Create `src/test/java/com/aistock/prediction/analysis/{AnalysisMysqlTest,AnalysisHttpTest,AnalysisServiceTest}.java`，修改既有ExplanationHttpTest加入共享槽回归；测试数据库fixture迁移到V6但不删生产数据。

**Interfaces:**
```java
record AnalysisInput(UUID operationId,String instrumentId,LocalDate from,
                     LocalDate to,UUID marketVersionId) {}
JsonNode AnalysisService.generate(UUID userId,AnalysisInput input);
JsonNode AnalysisService.list(UUID userId,String instrumentId,LocalDate from,LocalDate to,int page);
JsonNode AnalysisService.get(UUID userId,UUID analysisId);
```
公开GET按本人查询结果；POST只接受上述五字段。当前每日范围与最大月份服务端校验，body上限2048字节；错误输入不得占槽或调用MaaS。

- [ ] RED：先测试A用户与ADMIN不能读B记录，匿名401、缺CSRF403、注入userId/price字段400；同op不同股票409；同op重复调用只执行一次。
- [ ] 写V6新增result/operation表，UUID ascii_bin，正文JSON，时间DATETIME(3)，本地result外键可用，市场/用户只REF；result唯一(user_id,context_hash)。operation索引(user_id,instrument_id,range_from,range_to,sequence_id)，保存request_hash与固定context_json，状态RUNNING/SUCCEEDED/FAILED，检查约束。
- [ ] 原`prediction_explanation_slot`追加`operation_kind VARCHAR(32) NULL`，原有非空槽标记PREDICTION_EXPLANATION，新kind STOCK_ANALYSIS。不新建第二个并发槽。MaasOperationGate在调用者短事务内锁定slot，过期时根据受控kind更新对应操作FAILED/AI_INTERRUPTED；类型值不可拼接任意表名。
```sql
ALTER TABLE prediction_explanation_slot ADD COLUMN operation_kind VARCHAR(32) NULL;
UPDATE prediction_explanation_slot SET operation_kind='PREDICTION_EXPLANATION'
WHERE operation_id IS NOT NULL;
```
- [ ] 完成只允许同kind/op持有未过期槽写入；失败保存安全错误码，release条件同kind/op；迟到响应读回原终态而不覆盖新操作。E1仓储调用共享gate，不改变已保存预测或解释。
- [ ] 同op重放优先读取本人已固定请求与上下文，不能因行情不可用而重新取数并重新调用；新op才获取证据、领取槽。已成功相同context按用户缓存。GET读取运行操作显示expired但不发模型请求；恢复通过受控POST同op确认终态，用户再显式新尝试。
- [ ] HttpMaasClient增加构造时固定PromptProfile(PREDICTION_EXPLANATION/STOCK_ANALYSIS)，现有构造默认E1；同一个bounded HTTP实现，E2独立system提示只解释事实、输出overview/readout/risk。模板复用已有数字/引用/形状检查，要求risk包含BASIS_LIMIT和FRESHNESS_LIMIT，缺少引用拒绝。不得由用户传promptProfile。
- [ ] GREEN：数据库隔离测试覆盖并发占槽、跨kind、过期、迟到释放、缓存、op冲突、500/403冻结/429/坏JSON/35秒预算；旧E1模板与接口全部通过。服务HTTP测试模拟MaaS，真实云端不在普通测试中调用。提交Task2文件。

## Task 3 网关与现有行情页交互

**Files:**
- Create gateway `src/main/java/com/aistock/gateway/AnalysisApiProxyController.java`，复用ForecastPublicClient与既有长响应配置。
- Modify `ProxyHeaderPolicy.java`新增精准analysis合法路径/GET/POST/OPTIONS；不放宽其他路由。
- Create gateway `src/test/java/com/aistock/gateway/AnalysisProxyTest.java`。
- Create frontend `src/api/analyses.ts`、`src/pages/market/StockAiAnalysisPanel.vue`及`.test.ts`。
- Modify `src/pages/market/MarketAnalysisPage.vue`及market/README.md。

**Interfaces:**
```ts
interface AnalysisInput { operationId:string; instrumentId:string;
  from:string; to:string; marketVersionId:string }
// POST /stock-analyses -> operation receipt；GET带instrumentId/from/to/page ->本人分页；
// GET /stock-analyses/:analysisId ->固定context、paragraphs、createdAt。
// Panel props: instrumentId、rangeFrom、rangeTo、marketVersionId、hasBars。
```

- [ ] RED：测试初次进入只查询、不发生成；点击一次POST；刷新只GET；股票/范围切换和注销时旧回包丢弃；unknown回包保留原op，确认失败前不可另发新op；冻结提示不影响图表。
```ts
expect(api.generateAnalysis).not.toHaveBeenCalled();
await wrapper.get('[data-testid="generate-analysis"]').trigger('click');
expect(api.generateAnalysis).toHaveBeenCalledTimes(1);
```
- [ ] 网关专门允许股票、日期、分页query白名单，最大POST2048字节；透传既有安全cookie/CSRF/Origin/trace，丢弃X-User-Id/X-Role。非合法路径404。只转发内部MS03，内部MS02接口不经公开网关。
- [ ] Panel在既有图表下展示三段通俗正文，来源截止日期和依据折叠；提供生成、刷新及明确的失败重试。无有效bars/version禁用。显示结果来自哪个范围与版本，版本不一致标旧记录。使用插值纯文本，禁止v-html。
- [ ] 使用与页面同一已加载history.requestedRange和versionId；后端固定该版本，显示数据过期提示。缓存/历史查询与手机号邮箱无关。保留发起预测与自选操作，不新增首页大块文本。
- [ ] GREEN：所有frontend测试、类型检查与生产build，gateway转发测试与E1页面回归；更新README并提交Task3文件。

## Task 4 顺序回归、部署与受控实际验收

**Files:** `docs/verification-sdd005e2a.md`、`docs/sdd005e2a-progress.md`、`docs/implementation-status.md`、`docs/pending-items.md`。

- [ ] 顺序执行`./scripts/check-auth-task1.ps1`完整后端测试；禁止和聚焦MySQL测试同时跑。聚焦脚本选择`*Test`而非`*`，不误跑RealMaasProbe。
- [ ] frontend顺序运行pnpm test、pnpm build。仅取XML tests/failures/errors/skipped根属性汇总，不输出测试XML环境属性或密钥。
- [ ] 通过后构建实际变更服务的JAR/dist及镜像，Compose仅重建MS02/MS03/gateway/frontend，保留数据库卷和用户登录。验证V6迁移、服务健康与匿名API401。
- [ ] 已登录浏览器进入SH600030详情页，选择三个月，核对行情截至日/指标，主动生成一次，核对三段引用与确定性关系；刷新读取保存结果。检查原五日预测与E1解释保留、不能把RAW历史走势拼接为预测收益。
- [ ] 若真实响应不通过核验，保存安全诊断，先定位再修复，不自动连环重试；若需改变业务边界、收费或权限，再请用户取舍。
- [ ] 保存脱敏证据与限制：单次实际成功不代表持续可用或准确率；E2b预测引用/开放问答/比较/持仓未实现项仍列待办。文档提交后保持项目分支与无remote状态。

## 自查

四项任务分别覆盖证据、持久编排、界面和真实验收；所有审查重点有相应测试。内部路径按已有v1前缀统一；股票分析不复用预测专用提示；跨kind共享同一数据库槽并覆盖迁移旧活动槽。设计要求的E2b保留为下一阶段，不标E2a完成整个FR09。

## 执行结束说明

Task1至Task4已按实施台账与核验文件完成；原清单保留计划文字，不把未逐条独立复现的故障形式标为全部实证。最终接口实现与原计划的细化差异：复用既有内部认证过滤器；同一MaasClient bean通过generateAnalysis固定选择E2提示；providerModel/prompt保存在固定context_json。运行发现的Nginx预算遗漏追加RED→GREEN修复。真实旧预测保留以快照哈希核验，未再次付费复测修复后的慢POST。
