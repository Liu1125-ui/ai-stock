# SDD007C 多股有依据AI解释 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 用户明确点击后，解释本人已保存的股票对比，保存可追溯文字，确保引用数值与原结果一致。

**Architecture:** MS03从自己的ComparisonRepository读取本人固定结果，构建确定事实和关系，使用现有MaaS传输调用独立比较提示词，验证后保存解释。新增独立公开前缀及Feign子上下文，避免改变确定性比较30秒超时。沿用全MaaS数据库共享限额，不新增行情或算法调用。

**Tech Stack:** Spring Boot、JdbcTemplate事务、MySQL/Flyway、Jackson、Java BigDecimal、现有HttpMaasClient、Vue3/TypeScript、JUnit/Vitest。

**Spec:** docs/superpowers/specs/2026-10-12-sdd007-stock-comparison-design.md，AI解释边界及C切片。B片已验收，见docs/verification-sdd007b.md。

## 状态与意图

2026-10-13，V1.2：已完成针对现有代码的计划审核并修订，C片尚未实现。审核记录见docs/sdd007c-plan-review.md。执行方式保留本会话直接逐任务实施，不重新选择。用户希望AI自然解释多股走势与风险差异，数字必须与确定性结果一致；不增加交易、推荐排名、新闻、问答或训练模型。本计划先模拟测试；真实MaaS验收单独申请1次调用授权，先前额度不复用。

## Global Constraints

- 仅本人已经保存的comparisonId；ADMIN不自动读取他人结果。
- 股票2—3只、请求/实际区间、后复权口径、公式版本、四指标、缺失/停牌均来自固定结果。
- 不重新预测、不重新计算收益/风险指标、不调用行情供应商，不将旧历史称为最新。
- AI不写业务数值。数字、日期、代码、方向和比较关系通过事实占位符，由后端替换；严格校验refs。该机制保证引用数值来源一致，不声称消除所有自然语言逻辑错误。
- 只在用户点击时调用；挂载、刷新、历史切换、GET operation均零付费调用。
- 网络未知状态保留原operationId，先只读查询；同键不自动重复付费。过期RUNNING不重放供应商调用。服务器宕机/供应商已接受但结果未保存时，无法保证供应商绝不计费；不自动重试这种未知请求。
- 模型不可用、非法返回、限额繁忙时保留原指标和已有解释；不把固定模板假称模型成功。
- 新业务仍使用{code,message,data,traceId}，沿用会话/Origin/CSRF，不提交密钥。
- 五服务、十三业务、两角色、任务书顺序不变；未接入MyBatis-Plus不写成已接入，CodeArts真实证据仍后补。
- 所有自建代码目录附README，选择性小步提交。固定工作目录，不切换TianMao。

## Review Focus

1. 比较结果已切换/账号已注销，迟到AI响应不能覆盖新结果；Task5交错响应和账号清理测试。
2. 同一请求超时、刷新再点击，绝不自动第二次付费；Task3/4验证同键重放、全局过期槽和返回未知，Task5验证恢复只读。
3. 一股缺量、另一股停牌或没有共同端点，文字不补0、不给缺失指标排序；Task1缺失事实及模板拒绝测试。
4. 多股解释占用全局MaaS槽时，单股分析/问答/预测解释也必须受限；Task3真实MySQL跨类型互斥、过期处理和旧工作器完成测试。
5. 提示词或模型配置改变，历史解释仍指向原context，不以新标签显示旧正文；Task3缓存hash、Task5版本显示测试。

## 代码核对与设计裁定

已核对：预测库现末迁移V8；MaasOperationGate只识别PREDICTION_EXPLANATION/STOCK_ANALYSIS/STOCK_QUESTION，租约50秒；HttpMaasClient读超时35秒、响应体128KiB、正文8000字符、max_tokens=1200；ComparisonClientConfiguration读超时30秒。C必须增加槽类型COMPARISON_EXPLANATION与独立65秒网关超时，不能直接用B客户端。

MaasClient新增默认generateComparison方法，旧实现仍兼容。HttpMaasClient共享现有URL白名单、认证、冻结/限频分类和有界读取；C独立提示词及严格重复字段/尾随内容解析，旧调用行为不悄悄改变。

不把旧ExplanationPanel的自动生成行为复制到C；新组件仅GET。保留已存在单股流程，不借本片全面重构。

## 公共契约（四个路由）

- POST /api/v1/comparison-explanations：正文严格只有{operationId,comparisonId}，均UUID。未知/重复字段、尾随JSON、query拒绝，正文上限512字节。返回Operation。
- GET /api/v1/comparison-explanations?comparisonId=UUID：只读View，不新建操作，不过期回收槽。
- GET /api/v1/comparison-explanations/operations/{operationId}：本人只读Operation。
- GET /api/v1/comparison-explanations/{explanationId}：本人不可变完整Explanation。

```ts
interface ComparisonAiOperation {
  operationId:string;comparisonId:string;
  state:'RUNNING'|'SUCCEEDED'|'FAILED';explanationId:string|null;
  errorCode:string|null;leaseUntil:string;expired:boolean
}
interface ComparisonAiExplanation {
  explanationId:string;comparisonId:string;createdAt:string;
  contextHash:string;context:{comparisonId:string;sourceHash:string;formulaVersion:string;
    promptVersion:string;providerModel:string;facts:Record<string,string>};
  paragraphs:{kind:'overview'|'readout'|'risk';text:string;refs:string[]}[]
}
interface ComparisonAiView {
  configured:boolean;latest:ComparisonAiExplanation|null;latestAttempt:ComparisonAiOperation|null
}
```

首次执行成功201，成功缓存/同键重放200，进行中202；已落库的失败以200返回Operation且明确state=FAILED，不将失败显示成完成。鉴权401、跨用户/未知404、同键改comparisonId409、格式422/过大413、未配置503、全局繁忙503 AI_BUSY。供应商连接/超时/返回非法且FAILED已落库时，仍按上述200/FAILED回执返回；浏览器→网关→MS03断连或完成落库失败导致回执未知时，才返回受控503（或浏览器网络错误），保留原键先查状态，不假称已保存FAILED。

相同operationId终态不可再次执行；同键绑定comparisonId。过期RUNNING的GET返回expired=true且不写库；原键POST仅查询已存在回执、不再次请求模型。用户明确新建operationId时才可再次尝试，说明可能另产生调用费用。共享槽被下一次POST回收时，旧操作标FAILED/AI_INTERRUPTED；旧工作器不能发布结果或释放新槽。

## Task 1: 固定比较事实、自然提示词与一致性渲染

**Files:**
- Create: backend/stock-prediction-service/src/main/java/com/aistock/prediction/comparisonai/README.md
- Create: 同目录ComparisonAiFacts.java、ComparisonAiPrompt.java、ComparisonAiTemplate.java
- Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/comparisonai/ComparisonAiFactsTest.java、ComparisonAiTemplateTest.java、ComparisonAiSamples.java、README.md

**Interfaces:**
```java
public record Facts(Map<String,String> values, JsonNode context, String hash) {}
public static Facts build(JsonNode savedComparison,String model,ObjectMapper mapper);
public static String buildPrompt(Map<String,String> facts);
public static ArrayNode render(JsonNode raw,Facts facts,ObjectMapper mapper);
```
ComparisonAiFacts返回不可变排序Map；context ISO字符串，SHA256包含comparisonId/sourceHash/公式/提示词grounded-comparison-v1/providerModel/全部facts。只从已保存结果读取，调用方先做owner校验。不给模型全量原始日线或来源密钥。

- [x] RED：三股与输入顺序交换后，同股事实和值一致；请求/实际区间分别引用；无共同端点时所有价格指标说明不可用；一股缺量另一股停牌不混淆。
```java
var facts=ComparisonAiFacts.build(saved,"fixture-model",mapper);
// ComparisonAiSamples.saved()按instrumentId升序生成两股固定样本。
assertTrue(facts.values().get("S1_RETURN").contains(saved.path("stocks").get(0).path("intervalReturn").path("value").asText()));
var missingFacts=ComparisonAiFacts.build(ComparisonAiSamples.missing(),"fixture-model",mapper);
assertTrue(missingFacts.values().get("S1_VOLUME").contains("数据缺失"));
// 独立zero()样本：状态AVAILABLE且值0.0000必须解释为真实零成交量。
```
- [x] Run: backend中mvn -B -Pcli-build -pl stock-prediction-service -am '-Dtest=ComparisonAiFactsTest,ComparisonAiTemplateTest' '-Dsurefire.failIfNoSpecifiedTests=false' test；Expected: 新类不存在，FAIL。
- [x] 实现STOCKS、REQUEST_RANGE、ACTUAL_RANGE、BASIS_LIMIT、FRESHNESS_LIMIT、DATA_LIMIT、HISTORY_LIMIT、S1/S2/S3_RETURN/VOLATILITY/DRAWDOWN/VOLUME，以及RETURN_RELATIONS/VOL_RELATIONS/DRAWDOWN_RELATIONS/VOLUME_RELATIONS。
```java
// 排序使用固定instrumentId。关系仅比较已保存展示值，不重算原指标。
int comparison = new BigDecimal(left.value()).compareTo(new BigDecimal(right.value()));
// 相等写“在当前展示精度下相同”；任一UNAVAILABLE时不给关系/排名。
```
ComparisonAiSamples提供saved()、missing()、zero()和rawWithLiteral(String)测试工厂：由既有ComparisonTestEvidence+实际ComparisonCalculator生成两股结果，再添加固定comparisonId/createdAt；missing()构造一股缺量和另一股中间SUSPENDED并rehash；zero()保留AVAILABLE的零波动/零回撤/零成交量；rawWithLiteral生成三段合法占位符基础上在overview插入指定字面量。

每个指标事实包含所属证券、数值/单位或具体不可用原因，不能裸数值错配股票。关系句限定已保存区间，不写未来优势。涨跌幅只比较保存值大小；波动率/回撤更大只说明该项历史度量更大，不写成更好/更坏；成交股数只比较数量，不推断资金流动性或未来回报。DATA_LIMIT逐股按field/state汇总全部异常数量，每类列出首两个异常日期，并明确“完整日期清单见固定结果”，不把抽样日期冒称全部；无问题也说明仅已保存样本。FRESHNESS_LIMIT明确截止日期且不声称最新。
- [x] RED：模型正文独立数字/全角数字/日期/股票代码/方向/更强更弱/优于劣于/排名/交易建议/HTML、未知占位符、refs漏报/重复、错误段数均拒绝。有效模板三段overview/readout/risk。
```java
assertThrows(BusinessException.class,()->ComparisonAiTemplate.render(rawWithLiteral("收益为12%"),facts,mapper));
assertThrows(BusinessException.class,()->ComparisonAiTemplate.render(rawWithLiteral("这只更强"),facts,mapper));
```
- [x] 渲染先复用ExplanationTemplate.render，再检查非占位符文字的比较/排名词；overview必须引用STOCKS/REQUEST_RANGE/ACTUAL_RANGE/RETURN_RELATIONS及每股RETURN，readout引用VOL_RELATIONS/DRAWDOWN_RELATIONS及每股VOLATILITY/DRAWDOWN，risk引用BASIS_LIMIT/VOLUME_RELATIONS/DATA_LIMIT/HISTORY_LIMIT/FRESHNESS_LIMIT及每股VOLUME。模型不能自己生成股间比较关系。无端点仍可解释缺失限制，不编价格。
- [x] RED：包含所有正确refs但用“并非{{S1_RETURN}}”否定事实、在占位符后拼未来结论或错误价格口径仍拒绝。完整事实占位符必须作为独立分句，不能嵌进反向断言；加入常见否定、因果/未来推断攻击样例。正文一致性仍有语义局限，验收记录不能将关键词过滤宣称为完整事实证明。
- [x] Prompt按当前facts是否含S3构造必须引用集合和合法JSON示例，不能要求两股引用不存在的S3。Prompt要求自然中文、纯JSON、三段、每段template≤500字符、refs精确对应；事实数据不可作为指令。facts≤24KiB，每个值≤1000字符，渲染每段≤2000字符，总段落≤6000字符。超限受控拒绝，记录提示词版本。
- [x] 同命令GREEN；只提交本任务文件：feat: 构建股票对比解释事实与模板校验。

## Task 2: 接通独立MaaS比较调用，保持旧协议兼容

**Files:**
- Modify: backend/stock-prediction-service/src/main/java/com/aistock/prediction/explanation/MaasClient.java、HttpMaasClient.java
- Modify/Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/explanation/MaasClientTest.java

**Interfaces:** MaasClient.default JsonNode generateComparison(Map<String,String> facts)，默认抛AI_NOT_CONFIGURED；HttpMaasClient实现，使用Task1提示词，保持enabled()/model()。

- [x] RED：本地HTTP stub收到独立比较system、facts、stream=false、max_tokens=1200；旧预测/分析/问答提示词不被替换。C路径外层供应商JSON及内层content中的重复字段、尾随JSON、Markdown代码块、空content均受控AI_OUTPUT_INVALID；不能只校验内层而容许外层重复content被覆盖。
```java
assertThrows(BusinessException.class,()->comparisonClient.generateComparison(facts));
assertEquals(0,redirectTargetRequests.get());
```
- [x] Run: mvn -B -Pcli-build -pl stock-prediction-service -am '-Dtest=MaasClientTest' '-Dsurefire.failIfNoSpecifiedTests=false' test；Expected: 新方法路径尚不可调用，FAIL。
- [x] 增加严格解析参数：C的envelope和content均严格；既有question继续仅严格解析content，旧generate/generateAnalysis行为保留。不要在共享ObjectMapper上全局enable，采用每路径copy，保持旧三种调用的回归契约。本片不全面放宽输出校验，也不增新SDK/配置密钥。
```java
public JsonNode generateComparison(Map<String,String> facts) {
  return send(facts,ComparisonAiPrompt.buildPrompt(facts),null,true);
}
// 三参send继续按question!=null决定strict，四参send共享原有传输。
```
- [x] 覆盖冻结、401/403、429、5xx、35秒预算的短时测试替身、超128KiB/正文8000字符和中断；不给错误日志输出响应正文/Key。Expected: 全部现有MaasClientTest+新增测试GREEN。
- [x] 选择性提交 feat: 接入受控多股MaaS解释调用。

## Task 3: 解释持久化、缓存与全局MaaS互斥

**Files:**
- Create: backend/stock-prediction-service/src/main/resources/db/migration/V9__comparison_explanations.sql
- Create: comparisonai/ComparisonAiInput.java、ComparisonAiRepository.java
- Modify: backend/stock-prediction-service/src/main/java/com/aistock/prediction/maas/MaasOperationGate.java
- Modify: backend/stock-prediction-service/src/test/java/com/aistock/prediction/modelcatalog/ModelDatabaseFixture.java
- Test: 同目录ComparisonAiMysqlTest.java

**Interfaces:** Input(UUID operationId,UUID comparisonId)；Repository.replay(owner,input,now)、claim(owner,input,facts,now)、complete(owner,operationId,paragraphs,error,now)、operation(owner,id,now)、view(owner,comparisonId,configured,now)、get(owner,explanationId)。Claim(boolean execute,JsonNode receipt,boolean replayed,boolean cached)。Repository构造器显式接收JdbcTemplate、ObjectMapper、Clock。短事务5秒，网络不在事务中；完成时获得锁后重新读取Clock，不能用入事务前过时的now通过租约检查。

两表（同MS03库）：comparison_ai_operation保存operation_id PK、sequence_id BIGINT AUTO_INCREMENT UNIQUE、user_id、comparison_id FK本库stock_comparison_result、request_hash、context_hash/context_json、state、explanation_id nullable、error_code、created_at/lease_until/finished_at；comparison_ai_result保存explanation_id PK、sequence_id BIGINT、user_id、comparison_id本库FK、context_hash/context_json、paragraphs_json、created_at，唯一(user_id,comparison_id,context_hash)。解释结果只新增，不覆盖。两个表state/正文JSON约束和user_id/comparison_id/sequence_id索引明确；latest/latestAttempt按sequence_id DESC，而非毫秒时间戳。先建result表再建operation表，operation.explanation_id可外键引用本库result；两者均仅引用本库比较表。

**V9建表契约：**
```sql
CREATE TABLE comparison_ai_result (
 explanation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 comparison_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_json JSON NOT NULL, paragraphs_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_comparison_ai_context(user_id,comparison_id,context_hash),
 KEY ix_comparison_ai_result_owner(user_id,comparison_id,sequence_id),
 FOREIGN KEY(comparison_id) REFERENCES stock_comparison_result(comparison_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE comparison_ai_operation (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 comparison_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_json JSON NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 explanation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at DATETIME(3) NOT NULL,lease_until DATETIME(3) NOT NULL,
 finished_at DATETIME(3) NULL,
 KEY ix_comparison_ai_op_owner(user_id,comparison_id,sequence_id),
 FOREIGN KEY(comparison_id) REFERENCES stock_comparison_result(comparison_id),
 FOREIGN KEY(explanation_id) REFERENCES comparison_ai_result(explanation_id),
 CHECK((state='RUNNING' AND explanation_id IS NULL AND error_code IS NULL AND finished_at IS NULL)
 OR (state='SUCCEEDED' AND explanation_id IS NOT NULL AND error_code IS NULL AND finished_at IS NOT NULL)
 OR (state='FAILED' AND explanation_id IS NULL AND error_code IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```
request_hash只绑定comparisonId（不含当前可变模型配置）；context_hash含提示词/模型/事实，原operationId在模型配置改变后仍返回原回执而非409。context/paragraphs字节分别≤32KiB/24KiB；保存前校验，数据库JSON类型校验语法。两个owner字段来自会话，claim与缓存查询必须同时按owner和comparisonId匹配，不信任JSON内owner。

- [x] RED：同本人同键重放；同键改comparisonId409；他人operationId/解释/比较404；同context不同键缓存复用，模型/提示词/hash变化生成不同context；失败不覆盖旧结果。
```java
assertFalse(repository.claim(owner,newOperationSameContext,facts,now).execute());
assertEquals(first.path("explanationId").asText(),repository.view(owner,comparisonId,true,now).path("latest").path("explanationId").asText());
```
- [x] 隔离库环境同scripts/check-auth-task1.ps1，在backend运行mvn -B '-Pcli-build,auth-integration' -pl stock-prediction-service -am '-Dtest=ComparisonAiMysqlTest' '-Dsurefire.failIfNoSpecifiedTests=false' test；Expected: V9/新类缺失，FAIL。
- [x] 实现全局槽COMPARISON_EXPLANATION，白名单和过期表映射同时增加；旧三种类型及50秒租约不变。严格锁顺序先gate再operation；claim缓存命中不占付费槽，已有终态回执查询不因AI_BUSY受阻；超时原键仅返回已有回执，不能借gate回收自动再次执行。
```java
case "COMPARISON_EXPLANATION" -> "comparison_ai_operation";
// complete先锁共享槽，再锁本操作；freshNow=clock.instant()必须在锁取得后读取。
// 必须同时owns(kind,operationId)、state=RUNNING、freshNow<leaseUntil；否则不插入。
```
- [x] RED→GREEN：并发两请求仅一个execute；新C占槽时三种旧业务拒绝，旧业务占槽时C拒绝；过期回收更新正确旧表，旧工作器不能发布/释放新槽；同operationId过期重放不再execute；完成事务失败回滚，不出现孤立解释；补正好到期、锁等待跨到期及同毫秒新旧操作排序测试。缓存命中与既有回执返回时也必须报告正确cached/replayed，不能根据state猜是否调用了供应商。
- [x] ModelDatabaseFixture按依赖先清C操作/结果再比较操作/结果，避免污染既有测试；不改生产数据清理路径。GET view/operation无gate.lock、无过期写入。
- [x] GREEN后选择性提交 feat: 保存本人比较解释与调用恢复记录。

## Task 4: 服务编排、公开接口与独立网关超时

**Files:**
- Create: comparisonai/ComparisonAiService.java、ComparisonAiController.java、ComparisonAiConfiguration.java
- Modify: backend/stock-prediction-service/src/main/resources/application.yml、deploy/compose.yaml、.env.example（仅配置模板，不提交.env或密钥）
- Create: backend/stock-gateway/src/main/java/com/aistock/gateway/ComparisonAiPublicClient.java、ComparisonAiClientConfiguration.java、ComparisonAiApiProxyController.java
- Modify: backend/stock-prediction-service/src/main/java/com/aistock/prediction/security/PredictionSecurityConfiguration.java
- Modify: backend/stock-gateway/src/main/java/com/aistock/gateway/ProxyHeaderPolicy.java
- Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/modelcatalog/ComparisonAiHttpTest.java、comparisonai/ComparisonAiServiceTest.java
- Test: backend/stock-gateway/src/test/java/com/aistock/gateway/ComparisonAiProxyTest.java

**Interfaces:** Service.generate(owner,Input)返回Generation(JsonNode operation,boolean replayed,boolean cached)；view(owner,comparisonId)、operation(owner,id)、get(owner,id)返回JsonNode。使用ComparisonRepository.get(owner,comparisonId)、Task1事实、Task3持久化、MaasClient.generateComparison。POST前先replay，再校验本人源，再检查配置/构建facts/claim，网络调用结束验证render后complete；将Task3的replayed/cached标志带到Generation，不重新猜测。Controller按state与两个标志精确选201/200/202。

- [x] RED：未登录401、错误Origin/缺CSRF403、普通用户与ADMIN都只读本人404；query重复/未知/非法UUID/owner字段/重复JSON/尾随JSON及>512字节拒绝；未知内部/矩阵路径不泄露。
- [x] RED：浏览器提交comparisonId后不能传指标/facts/model覆盖服务端；B结果中间源变更不影响解释来源；原比较缺失仍可生成缺失说明，真实依据畸形受控422。
```java
verify(client,times(1)).generateComparison(facts.values());
service.generate(owner,sameInput);
verify(client,times(1)).generateComparison(facts.values());
```
- [x] RED：模型失败/非法输出保存FAILED且原指标和旧解释可读；同键回执无需配置可重放；并发AI_BUSY不创建新操作；GET只读零模型调用。
- [x] 新增C独立发布开关aistock.comparison-ai.enabled=${COMPARISON_AI_ENABLED:false}，模板默认false；View.configured = 开关开启且client.enabled()，不能将真实连通性冒称configured。开关关闭时GET和既有同键回执仍可读，新操作返回503/AI_NOT_CONFIGURED；不开启自动调用。测试关闭/开启及已保存解释可读。
- [x] RED：首次新成功201、成功缓存/既有终态200、已保存失败200/FAILED、RUNNING202；MaaS失败已保存与网关回执未知503分别验证。
- [x] 实现上述四路由，网关新Feign contextId=comparison-ai-public，connect2秒/read65秒、不重试、不重定向。该配置不加全局@Configuration，不能覆盖B的30秒或旧预测185秒。
```java
new feign.Request.Options(2000,TimeUnit.MILLISECONDS,65000,TimeUnit.MILLISECONDS,false);
// 独立Controller只映射 /api/v1/comparison-explanations 与其子路径。
```
- [x] 白名单只增加四种精确公开路由和OPTIONS；GET根只允许comparisonId一个query，其他query禁止；转发既有安全头策略。服务故障503说明查询原操作，4xx按现有安全响应清洗，不能暴露内部地址/异常。
- [x] 定向测试GREEN，旧ComparisonProxyTest/ComparisonHttpTest及MaasClientTest同时GREEN；选择性提交 feat: 开放本人多股AI解释接口。

## Task 5: 比较页面的AI解释、恢复和依据查看

**Files:**
- Create: frontend/src/api/comparisonAi.ts、frontend/src/pages/comparison/ComparisonAiPanel.vue、comparisonAiState.ts
- Modify: frontend/src/pages/comparison/ComparisonPage.vue、README.md
- Modify: frontend/src/api/auth.ts、frontend/src/stores/auth.ts
- Test: ComparisonAiPanel.test.ts、comparisonAiState.test.ts；更新ComparisonPage.test.ts、auth.test.ts

**Interfaces:** API使用上文四路由和三个TypeScript接口。组件props comparisonId:string，不读页面新筛选值构建facts。sessionStorage键aistock:comparison-ai-pending:<owner>:<comparisonId>，只存{operationId,comparisonId}，发送前成功保存才POST。

- [x] RED：挂载/刷新/切换结果/历史无自动generate；未保存结果不出现按钮。点击生成只一次，busy防连点；数据指标始终保留。
```ts
expect(createComparisonAi).not.toHaveBeenCalled()
await wrapper.get('[data-generate]').trigger('click')
expect(createComparisonAi).toHaveBeenCalledTimes(1)
```
- [x] RED：超时保留原键，刷新先GET operation；404允许明确重试原键，RUNNING未过期只刷新，过期显示不自动续费，FAILED允许明确新操作；已有成功缓存读取不调用模型。sessionStorage禁止/损坏发送前报错，不泄露到另一账号。面板有本地待确认键时优先查该operation，不能仅因View.latestAttempt属于另一标签页就清除本地原键；终态确认后才清。
- [x] RED：旧比较A解释迟到，用户已打开B/注销，不更新新面板；失败保留旧正文和指标，来源/模型/提示词标签来自保存context而不是当前配置。
```ts
oldRequest.resolve(oldExplanation)
await flushPromises()
expect(wrapper.text()).not.toContain(oldExplanation.explanationId)
```
- [x] 实现三段自然说明、模型/生成时间/解释编号/事实展开、历史截至日期、独立“刷新解释记录”和“生成AI解释”；无自动定时请求，无未接入问答按钮。明确是历史分析而非未来股票推荐。
- [x] 首次仅GET View，configured=false仍可显示已有解释；新operation说明“可能调用模型并产生费用，已有相同依据解释可复用”；成功缓存不假称刚重新生成，恢复原operation只查已知状态，不自动更换ID。错误码显示自然中文，折叠诊断码；数值显示从服务端文本，不重新计算。
- [x] /comparison-explanations加入既有AUTH_REQUIRED路径，注销清本人的前缀；用key=comparisonId隔离组件并共享账号generation，保留B迟到响应保护。
- [x] Run: pnpm test --run、pnpm build；Expected: 全前端GREEN/严格类型构建成功。选择性提交 feat: 提供多股有依据AI解释页面。

## Task 6: 完整回归、一次独立审核与受控验收

**Files:**
- Create: docs/verification-sdd007c.md、docs/evidence/sdd007c-comparison-ai-checks.json、docs/sdd007c-execution-ledger.md
- Modify: docs/implementation-status.md、docs/pending-items.md、设计spec

- [x] 后端用scripts/check-auth-task1.ps1全量隔离clean verify，前端完整test/build；Maven不并发。保留失败与通过日志，检查单股AI回归和全局槽互斥测试真实执行。
- [x] 一次最终独立审查整片，重评后一次具体问题修复并RED→GREEN；不擅自声称第二次独立复审。
- [x] 部署前保留预测/网关/前端旧镜像与回退命令，保存原账号、自选、模型/预测/解释/问答、规则/触发与B比较结果关键计数/hash；V9前向迁移，不删除旧表。邮件工作器仍false。先以COMPARISON_AI_ENABLED=false部署，确认新版MS03支持新槽类型、迁移及网关健康后再受控开启；真实1次验收授权到位前不为自动验收开启调用。
- [x] 回退专用检查：旧MaasOperationGate不能识别COMPARISON_EXPLANATION。回退前暂停网关新请求，保留新版MS03等待在途操作结束；不能仅换回三个旧镜像。若C槽到期仍存在，用stock_prediction本库事务锁定slot_id=1，仅在kind为COMPARISON_EXPLANATION且lease_until<=当前UTC时，把其对应RUNNING操作标FAILED/AI_INTERRUPTED并条件清槽；不能清其他种类或有效租约。确认无新种类槽后恢复旧镜像与旧入口，V9数据保留、不降库。隔离测试模拟旧gate读取清理后的槽可继续旧业务；SQL条件/回退步骤随验收文档保存，未演练不得声称已演练。
- [x] 真实本人会话验证页面挂载、刷新与原指标保留，不自动调用MaaS。浏览器内核故障时记录限制，用户操作与自动测试分别标注。
- [x] 真实MaaS验收另申请明确1次调用。未获授权不调用；获授权后围绕用户既有两股比较生成1次，读取保存正文/refs/context和原结果核对，刷新与重放零新增调用，失败如实记录、不擅自追加预算。没有额度时完成模拟/部署与只读验收，真实调用标待验收。
- [x] 选择性提交验收文档，保留当前分支；无Git远程不声称推送或远程CI。

## 计划自审

- 来源与数字一致：Task1；旧MaaS兼容/严格解析：Task2；缓存/付费防重/过期隔离：Task3；权限/独立超时：Task4；明确点击/本人恢复/迟到隔离：Task5；真实验收与保全：Task6。
- 未改变B固定结果和数据证据、不重复采集、不增加问答/新闻等业务；只接已确认C片。
- 四个公开路由、两个新表、一种共享槽类型；V9先复核末版本，示例与实际Input均两字段。
- 所有GET只读，过期操作不是自动重发模型的依据；原键不能被改成新参数；失败保留旧成功解释。
- 共享槽50秒、MaaS35秒、网关65秒关系清楚；不复用B30秒客户端。
- 关系只基于已保存指标展示值，等值明确展示精度，不借AI重算；数据缺失不排名，不提供买卖结论。
- 本计划V1.2已完成代码对照审核并修订；该审核不是代码实现验收，C尚未编写或调用模型。实施仍以用户对新计划的确认范围为准；已有执行方式保留，不再次询问分工。

## 自审修订记录

已纠正动态第三股：Prompt显式接收facts，按实际两股/三股选择占位符，避免要求不存在的S3。DATA_LIMIT对所有异常计数、日期示例明确截取且引导完整固定清单，避免一年缺口让单一事实突破大小上限。测试工厂来源明确使用实际计算器，旧MaaS协议保持，四个公开路由与前端输入两字段一致。此处仅修改计划，未启动C产品代码或付费调用。

## 本轮审核结论（2026-10-13，V1.2）

方案保留六任务、两个新表和四个公开路由。修订点：共享槽旧版本回退、精确生成状态标志、错误层级、零值测试、外层/内层严格解析、锁后租约时间、稳定最新排序、请求区间/口径必引、常见反义断言及跨标签恢复。新增C发布开关只为受控启用与回退，不新增业务。详细证据和剩余实现验证项见docs/sdd007c-plan-review.md。没有运行C功能测试或真实MaaS；本轮只改计划与审核文档。

## 当前执行状态（2026-10-13）
Task1–5完成并提交；Task6回归、唯一独立审查修复、关闭部署、保全及隔离旧gate回退检查通过。用户随后明确授权最多1次真实调用，C已开启，等待用户正常会话单次生成及刷新；未勾选的真实验收项不得视为完成。

### 真实验收运行缺陷处理
用户单次操作已执行FAILED/AI_OUTPUT_INVALID，额度用完。补Nginx comparison-explanations长路由，测试RED→GREEN并用隔离9秒服务验证；补有界分类诊断，不保留或输出模型原文/密钥。该用户运行缺陷修复不冒称第二次独立审查。真实成功与刷新核对仍不勾选；原操作只读可查看。

后续：用户明确追加最多1次诊断（累计2次），修复部署后已受控开启，等待本人单次点击；不自动续费，不提前标真实成功。

## 最终执行状态
Task1–6已执行；最终606/148及构建通过，单次独立审查修复与用户运行缺陷修复均有RED→GREEN。真实累计两次分别授权：第一次失败保留，第二次成功，19引用/8指标核验一致，用户刷新保持且操作2/结果1未增。真实同键POST重放未另测，其零供应商调用由自动化覆盖；完整旧镜像/Flyway启动及独立视觉矩阵仍未验收。C已启用，本轮无更多调用，现有分支保留，无远程推送。
