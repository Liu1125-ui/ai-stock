# SDD005E3 单股有依据AI问答实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. 按用户既有选择在本会话直接执行，完整分支最后做一次独立只读审查。

**Goal:** 用户围绕本人一份保存分析自由提问，取得自然、可核对、可保存的答案，刷新和恢复不重复调用MaaS。

**Architecture:** MS03的question模块读取AnalysisRepository的本人不可变证据，MaaS只组织白名单事实，QuestionAnswerTemplate核验后保存。独立问答表与原预测解释/行情分析共用MaasOperationGate，前端StockQuestionPanel绑定当前保存analysisId；不增加跨库访问。

**Tech Stack:** 现有Java/Spring Boot/JdbcTemplate/MySQL/Flyway/JUnit5/Mockito，Vue3/TypeScript/Vitest，Nginx/Docker Compose；不新增依赖。

**Spec:** docs/superpowers/specs/2026-10-07-sdd005e3-stock-question-design.md（用户已确认）。

状态：2026-10-09用户批准并实施；Task1—3代码及测试已完成，Task4最终回归与部署验收进行中，未调用真实云模型。历史复核说明保留原时间语境。设计基线8507078，首次计划fd2bd3b，固定已有codex/sdd004-market-refresh分支；执行前重新检查工作区，不重置用户改动。当前无Git远程，完成后本地小步提交，不push。

## Global Constraints

- 保持13业务、五微服务、ADMIN/USER；本轮只单股问答，比较/持仓和连续多轮上下文后续独立扩展。
- 源分析必须属于本人；ADMIN无问答跨用户例外。源证据与已有价格、预测、验证版本保持不变。
- question按复核A的固定空白集合去两端空白并NFC规范化，1—300个Unicode码点，拒绝控制字符与不配对代理项；JSON UTF-8 body不超过2048字节。
- POST仅operationId、analysisId、question；GET列表仅analysisId/page，页码1—10000，每页10条。
- 问答提示grounded-stock-question-v1，固定知识qa-knowledge-v1；原E1/E2提示和结果不改写。
- 三种生成共用全局1槽、50秒租约；HTTP完整响应35秒、响应最多128KiB，TLS地址限制及不重定向不变。
- 问答Nginx读取预算190秒；其他API仍8秒，不扩大通用代理预算。
- 不打印问题原文、用户个人资料、密钥、Cookie或测试XML属性；测试只用假HTTP，不付费调用。
- 真实云验收在本地检查和部署后单独确认调用数量，未经批准不执行或自动重试。
- 所有MySQL测试访问13316独立stock_prediction_test，顺序运行；不清生产数据库或Docker卷。

## Review Focus

1. 重载时恢复RUNNING原问题及analysisId，而不是用编辑后的输入生成另一笔请求；Task3的组件测试钉住。
2. 源分析使用旧验证/行情，问答不能重新读当前头或当前验证冒充更新；Task1证据测试和Task2服务零依赖测试钉住。
3. 跨类型租约到期和迟到回包不得清掉另一种操作的槽；Task2的MySQL并发/到期测试钉住三种类型。
4. Unicode、代理项和控制字符在浏览器码点计数、服务规范化与请求hash中一致；Task1与Task3边界测试钉住。
5. V7外键引入后，既有隔离fixture必须先删问答子表再删分析；Task2完整MySQL回归钉住，禁止生产清表。

## 文件职责和契约

新question目录只承担问答；README说明作用/实现方法。新增QuestionInput、QuestionFacts、QuestionAnswerTemplate、QuestionRepository、QuestionService、QuestionController、QuestionConfiguration。Facts只复制及校验服务器保存证据，Repository只持久化与共享锁，Service编排，Controller严格输入及本人身份，Template处理模型输出。

Task间类型统一：

```java
public record QuestionInput(UUID operationId, UUID analysisId, String question) {
    public static String normalize(String question);
    public QuestionInput normalized();
}
// QuestionFacts
public record Facts(Map<String,String> values, JsonNode context, String hash) {}
public static Facts build(JsonNode ownedAnalysis, String question, String model, ObjectMapper mapper);
// QuestionAnswerTemplate
public record Answer(String status, String topic, JsonNode paragraphs) {}
public static Answer render(JsonNode raw, Map<String,String> facts, ObjectMapper mapper);
// QuestionRepository
public record Claim(boolean execute, JsonNode receipt) {}
public JsonNode replay(UUID user, QuestionInput in, Instant now);
public Claim claim(UUID user, QuestionInput in, QuestionFacts.Facts facts, Instant now);
public JsonNode complete(UUID op, QuestionAnswerTemplate.Answer answer, String error, Instant now);
public JsonNode operation(UUID user, UUID op, Instant now);
public JsonNode get(UUID user, UUID answerId);
public JsonNode list(UUID user, UUID analysisId, int page, Instant now);
// QuestionService
public JsonNode generate(UUID user, QuestionInput input);
public JsonNode operation(UUID user, UUID op);
public JsonNode get(UUID user, UUID answerId);
public JsonNode list(UUID user, UUID analysisId, int page);
// MaasClient
public default JsonNode generateQuestion(String question, Map<String,String> facts);
```

operation/get/list查询均过滤user_id。无权限与不存在404（包括其他用户已用的operationId），非法输入400，本人同op不同请求409 AI_REQUEST_CONFLICT；输出非法AI_OUTPUT_INVALID，依据结构错误QUESTION_EVIDENCE_INVALID。OUT_OF_SCOPE为SUCCEEDED并保存状态，正文由程序确定，不保存模型自创的拒绝正文。

## Task1：问题规范化、冻结事实与输出核验

**Files:** Create backend/stock-prediction-service/src/main/java/com/aistock/prediction/question/{README.md,QuestionInput.java,QuestionFacts.java,QuestionAnswerTemplate.java}；Create同模块src/test/java/com/aistock/prediction/question/{README.md,QuestionInputTest.java,QuestionFactsTest.java,QuestionAnswerTemplateTest.java}。路径大括号是明确的文件清单，不创建字面大括号目录。

**Interfaces:** 实现上节QuestionInput/Facts/Answer签名；输入ownedAnalysis为AnalysisRepository.get返回analysisId、createdAt、contextHash、context、paragraphs结构。不直接接受客户端facts。

- [x] 先写规范化RED：中文/补充平面码点300合法301拒绝；NFC组合字符一致；空白问题、控制字符、不配对代理项拒绝；前后空格规范化相同。示例：

```java
@Test void countCodePointsAndRejectControls() {
    String valid = "😀".repeat(300);
    assertEquals(valid, QuestionInput.normalize(valid));
    assertThrows(BusinessException.class, () -> QuestionInput.normalize("😀".repeat(301)));
    assertThrows(BusinessException.class, () -> QuestionInput.normalize("问题\u0000"));
    assertEquals("é", QuestionInput.normalize(" e\u0301 "));
}
```

- [x] 运行聚焦命令，确认缺类型/行为RED并记录安全摘要：

```powershell
./runtime/focus-d3b3.ps1 -Modules 'stock-prediction-service' -Tests 'QuestionInputTest,QuestionFactsTest,QuestionAnswerTemplateTest' -Log runtime/e3-task1-red.log
```

- [x] 实现输入规范化：先检验输入非null、代理项配对和原始控制字符，再NFC/固定集合trim，codePointCount验证1—300。normalized返回新不可变QuestionInput，不在replay后改变请求文字。
- [x] Facts复制源context且不变更sourceContextHash；校验源analysisId/hash/createdAt、instrumentId、日期、RAW、tech-v1、facts文本类型以及源预测字段一致。新增QA_MA_DEF/QA_MACD_DEF/QA_RSI_DEF等固定定义；context记录sourceAnalysisId/sourceContextHash/sourceCreatedAt、knowledgeVersion、promptVersion、providerModel、question及facts。通过mapper序列化固定顺序结构计算SHA256；hash覆盖问题和模型，禁止只以analysisId缓存。
- [ ] 事实RED样例用JSON写死服务器分析，至少STOCK/AS_OF/CLOSE/MA5_STATE/MACD_STATE/RSI_STATE/BASIS_LIMIT/FRESHNESS_LIMIT；加入纯行情、预测零样本、既有验证三份。断言不会增加不存在PRED_*或误差事实，source context序列化前后相同，换问题/model改变hash。该测试不访问市场或预测Repository。
- [ ] 写输出RED：根对象未知字段、错误status/topic、三段缺项/重复、未知refs、独立数字/方向、交易建议、金叉、HTML及注入均拒绝；OUT_OF_SCOPE必须空paragraphs；INDICATOR必须QA定义与状态，PREDICTION/VERIFICATION缺PRED_*时合法OUT_OF_SCOPE返回固定资料不足提示，错误ANSWERED拒绝。所有ANSWERED须AS_OF和来源限制，预测主题须口径事实。

```java
@Test void scopeRefusalUsesNoModelProse() throws Exception {
    JsonNode raw = new ObjectMapper().readTree("""
      {"status":"OUT_OF_SCOPE","topic":"LIMITATION","paragraphs":[]}
      """);
    var answer = QuestionAnswerTemplate.render(raw, Map.of(), new ObjectMapper());
    assertEquals("OUT_OF_SCOPE", answer.status());
    assertTrue(answer.paragraphs().isEmpty());
}
```

- [x] 输出先核验严格根字段、枚举与paragraphs数组类型。OUT_OF_SCOPE必须LIMITATION和空段；ANSWERED再核验三段对象字段及文本/refs类型。ANSWERED若主题所需预测事实缺失，直接拒绝模型正文为AI_OUTPUT_INVALID，不将坏JSON、未知引用或模型错误分类转换为成功范围提示；模型应对该问题返回合法OUT_OF_SCOPE，页面使用固定缺依据说明。具备所需事实时再送ExplanationTemplate核验占位符与主题refs。输出拒绝只打印固定reason code，不能打印模型原文或question。
- [x] 同聚焦命令记录runtime/e3-task1-green.log，确认退出0；精确git add以上7类文件和两README，小步commit“feat: 问答输入与事实核验”。

## Task2：MS03问答记录、本人接口与共享付费槽

**Files:** Create backend/stock-prediction-service/src/main/resources/db/migration/V7__stock_questions.sql；question/{QuestionRepository.java,QuestionService.java,QuestionController.java,QuestionConfiguration.java}；测试question/QuestionServiceTest.java及modelcatalog/{QuestionMysqlTest.java,QuestionHttpTest.java}。Modify modelcatalog/ModelDatabaseFixture.java、security/PredictionSecurityConfiguration.java、explanation/{MaasClient.java,HttpMaasClient.java,ExplanationTemplate.java,README.md}、maas/{MaasOperationGate.java,README.md}；测试explanation/MaasClientTest.java。MySQL测试置modelcatalog以复用现有package-private fixture，不修改其可见性。

**Interfaces:** 实现总契约的Repository/Service方法；QuestionConfiguration构造QuestionRepository(JdbcTemplate,ObjectMapper)与QuestionService(QuestionRepository,AnalysisRepository,MaasClient,ObjectMapper,Clock)。禁止用户从接口传model/版本/事实。

- [ ] 先写MySQL RED：领取RUNNING、唯一成功记录、同op重放、问题/分析变更409、另一user404；缓存只同user同context；失败留历史、不会保存非法正文、GET无云执行；50秒到期三类型争槽和旧回包；实际SQL事务并发测试两个领取只能一方执行。
- [x] 使用fixture通过AnalysisRepository.claim/complete种一份属于user的analysis，不能给问答FK伪造不存在analysis_id。V7迁移后ModelDatabaseFixture在stock_analysis_*删除前依次删stock_question_operation、stock_question_result；现用业务库不执行这些清理。
- [ ] RED服务测试用Mockito确保replay先于client.enabled和analysis.get；跨用户analysis.get失败则client从未调用；原源分析不访问market.evidence或VerificationQueryRepository；非法输出传complete(null,error)，成功/范围提示分开。

```java
@Test void replayNeverCallsProviderOrReadsNewEvidence() {
    var repo = mock(QuestionRepository.class);
    var source = mock(AnalysisRepository.class);
    var client = mock(MaasClient.class);
    var mapper = new ObjectMapper();
    UUID user = UUID.randomUUID();
    var in = new QuestionInput(UUID.randomUUID(), UUID.randomUUID(), "有哪些风险？");
    when(repo.replay(eq(user), eq(in), any())).thenReturn(mapper.createObjectNode().put("state","SUCCEEDED"));
    var service = new QuestionService(repo,source,client,mapper,Clock.systemUTC());
    assertEquals("SUCCEEDED",service.generate(user,in).path("state").asText());
    verifyNoInteractions(source,client);
}
```

- [x] 运行QuestionMysqlTest,QuestionHttpTest,QuestionServiceTest,MaasClientTest聚焦RED；HTTP假服务支持捕获request，检验question仅在用户消息、system不包含用户原文；facts JSON只有白名单股票事实，无question复制到事实/邮箱/userId。覆盖35秒完整响应、128KiB、跳转拒绝和坏JSON，保留E1/E2回归。
- [x] 建表采用现有ascii_bin UUID/utf8mb4_bin业务JSON。result包含spec全部字段，status ANSWERED/OUT_OF_SCOPE CHECK；operation state RUNNING/SUCCEEDED/FAILED CHECK且终态answer_id/error_code一致。user+context_hash唯一，本库analysis_id FK；operation有answer_id可空FK。问题最多1200 UTF-8字节范围内，VARCHAR(1200)并由代码限制码点。
- [x] 请求hash使用规范化问题和analysisId的JSON序列化SHA256，不用含分隔符歧义的直接拼接。claim事务锁共享槽，先重放，再缓存，未命中才领取STOCK_QUESTION并插RUNNING；唯一缓存冲突以同用户读现有结果处理，不覆盖。
- [x] complete事务先gate.lock(now)，仅自己的未过期STOCK_QUESTION槽可提交；ANSWERED存核验后段，OUT_OF_SCOPE存空段及固定topic，失败只安全code；已终态直接回执。到期恢复追加失败状态AI_INTERRUPTED，不能修改另一种槽。
- [x] GET operation只读取本人状态，不自动占槽或触发云。到期RUNNING返回expired=true；页面提供“结束过期请求”，用户明确点击后POST原operationId/analysisId/question，事务恢复原操作为FAILED/AI_INTERRUPTED且不调用模型，再解锁新问题。不能让用户依赖另一请求碰巧清槽。GET404不代表先前POST不会稍后落库：保留原输入及op，显示“恢复原请求”，用户明确点击仅POST同原三字段；先前已领取则回放，否则至多领取执行一次。列表和GET按本人过滤，latestAttempt含原question、analysisId、op、leaseUntil、expired。未知、超时、503或401均不自动新建op。
- [x] MaasClient default generateQuestion默认受控AI_NOT_CONFIGURED，旧实现兼容；HttpMaasClient抽出统一有界传输重载send(facts,system,question)，旧两入口用无question版本，不改变旧payload。新系统提示包含完整ANSWERED/OUT_OF_SCOPE JSON范例、固定枚举、事实占位符、主题必引和不执行用户指令，用户消息JSON含question和facts。
- [x] Controller从Actor取userId，readNBytes(2049)，根仅三字段、严格UUID/text，服务规范化；GET UUID与page有效，ApiResponse及traceId沿用。security authenticated列表增加根与/**，不permitAll。
- [x] 同聚焦命令GREEN，再顺序运行AnalysisMysqlTest,AnalysisHttpTest,AnalysisServiceTest,MaasClientTest与Question*Test；禁止通配*匹配手工RealMaasProbe。记录runtime/e3-task2-green.log，精确暂存该任务路径，commit“feat: 本人问答接口与不可变记录”。

## Task3：网关、前端提问与原请求恢复

**Files:** Create backend/stock-gateway/src/main/java/com/aistock/gateway/QuestionApiProxyController.java及src/test/java/com/aistock/gateway/QuestionProxyTest.java；Modify ProxyHeaderPolicy.java及测试README；Create frontend/src/api/questions.ts、frontend/src/pages/market/StockQuestionPanel.vue与StockQuestionPanel.test.ts；Modify StockAiAnalysisPanel.vue与.test.ts、frontend/src/api/auth.ts与auth.test.ts、frontend/src/stores/auth.ts与auth.test.ts；Create frontend/src/stores/questionRecovery.ts与questionRecovery.test.ts；Modify frontend/src/api/README.md、pages/market/README.md、frontend/nginx.conf及nginx.test.js。

**Interfaces:**

```typescript
export interface QuestionInput { operationId:string; analysisId:string; question:string }
export interface QuestionAttempt extends QuestionInput {
 state:'RUNNING'|'SUCCEEDED'|'FAILED'; answerId:string|null;
 errorCode:string|null; leaseUntil:string; expired:boolean
}
export interface QuestionAnswer {
 answerId:string; analysisId:string; question:string; createdAt:string;
 status:'ANSWERED'|'OUT_OF_SCOPE'; topic:string; contextHash:string;
 context:Record<string,unknown>;
 paragraphs:Array<{kind:string;text:string;refs:string[]}>
}
export interface QuestionList {items:QuestionAnswer[];total:number;page:number;latestAttempt:QuestionAttempt|null;configured:boolean}
export function askQuestion(input:QuestionInput):Promise<QuestionAttempt>;
export function questions(analysisId:string,page=1):Promise<QuestionList>;
export function question(answerId:string):Promise<QuestionAnswer>;
export function questionOperation(operationId:string):Promise<QuestionAttempt>;
// StockQuestionPanel props: analysisId:string, instrumentId:string
```

- [x] 网关RED覆盖根GET/POST、answerUUID GET、operations/UUID GET与OPTIONS；错误方法/任意内部路径404；query只analysisId/page；超2048字节413；假user/role头不传，403与安全4xx保持，503受控。
- [x] 前端RED覆盖建议按钮只填输入、不调用；发送唯一POST；记录刷新仅GET；没有analysis不发送；state未知保留原question/analysisId/op，编辑不能改原请求；重载从latestAttempt恢复后GET原operation；page切换不丢pending；stock/analysis/user切换旧响应不显示。

```typescript
it('a suggestion fills input without generating',async()=>{
 const wrapper=mount(StockQuestionPanel,{props:{analysisId:'11111111-1111-4111-8111-111111111111',instrumentId:'SH600030'}})
 await flushPromises()
 await wrapper.get('[data-testid="question-suggestion-risk"]').trigger('click')
 expect(wrapper.get<HTMLTextAreaElement>('[data-testid="question-input"]').element.value).toBe('这份分析有哪些风险？')
 expect(askQuestion).not.toHaveBeenCalled()
})
```

Mock api/questions所有四函数及auth.store，按当前StockAiAnalysisPanel.test的模拟已登录测试方式初始化，不能测试生产账号。另写OUT_OF_SCOPE明确显示、FAILED保留旧回答、非法答案不渲染HTML、300/301补充码点、服务器身份变化使旧回答失效。

- [x] 运行前端聚焦RED和QuestionProxyTest聚焦RED，记录安全摘要。组件采用script setup，API统一调用现有request封装，禁止另造Cookie或跳过CSRF。
- [x] QuestionPanel独立维护input、pending、latestAttempt、page、selectedAnswer与generation；旧pending冻结，用GET操作查询恢复。选择当前分析由父面板传result.analysisId，有result才挂载；无result在父面板明确显示先生成或选择分析提示。切行情版本不修改已经保存的analysisId问答依据。预测选择变但未生成新分析时问答仍明确绑定保存分析。
- [x] 成功/范围提示显示问题和三段或固定拒绝，数据截止和查看依据单独展开；原问题用文本渲染，refs与元数据不混入正文。auth.ts的401受保护路径识别补入/stock-questions，并测试会话过期触发aistock:auth-required和清空旧身份；不能仅新增后端security路径。历史列表分页不重发POST。只有终态或确定未领取错误才能清pending；网络未知不能创建新op。
- [x] 网关按现有Analysis代理实现安全透传，但使用问答独立错误说明；新增安全方法白名单，绝不匹配任意/**写路径。Nginx长预算正则精确修改为：

```nginx
location ~ ^/api/v1/(?:predictions|stock-analyses|stock-questions)(?:/|$) {
    proxy_intercept_errors on;
    proxy_connect_timeout 2s;
    proxy_send_timeout 10s;
    proxy_read_timeout 190s;
    proxy_pass http://stock-gateway:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Trace-Id $http_x_trace_id;
    proxy_set_header X-Real-IP $remote_addr;
}
```

其余location保留，pnpm --dir frontend exec vitest run nginx.test.js增加问答路由及普通API8秒断言。

- [x] 顺序GREEN：pnpm --dir frontend test、pnpm --dir frontend build、pnpm --dir frontend exec vitest run nginx.test.js，网关QuestionProxyTest/AnalysisProxyTest聚焦。记录runtime/e3-task3-ui.log/build.log/gateway.log，精确暂存该任务文件，commit“feat: 股票详情页有依据问答”。

## Task4：最终检查、部署和真实验收

**Files:** Create docs/sdd005e3-progress.md、docs/sdd005e3-review-and-fixes.md、docs/verification-sdd005e3.md、docs/evidence/sdd005e3-<实际验收日期YYYYMMDD>.json；Modify docs/implementation-status.md与pending-items.md；spec状态同步但不冒称总FR09完成。

- [ ] 用tracked ledger记录每项RED/GREEN、实际提交、变动原因，用户已批准当前会话直接执行。完整分支只做一次独立只读审查，重点覆盖五条Review Focus、输出语义与数据归属；发现实际缺陷先RED复现再集中修复，不循环复审。
- [ ] 顺序执行完整回归：

```powershell
./scripts/check-auth-task1.ps1 *> runtime/e3-final-backend.log
pnpm --dir frontend test *> runtime/e3-final-ui.log
pnpm --dir frontend build *> runtime/e3-final-build.log
pnpm --dir frontend exec vitest run nginx.test.js
```

每项立即检查LASTEXITCODE，失败先定位修复，不能以最后一个命令成功遮盖前面的失败。XML只读tests/failures/errors/skipped根属性，禁打印properties。部署不含研究训练和真实云调用测试。

- [ ] 保存现有预测19ddcb7c-3fba-4df3-ba4f-f8eebfeb3f14快照/model hash，E2a b97e45d7-815d-4450-bf43-f2fd5dbbab22及E2b 60f636a8-bba3-4bd4-9e29-5c9e519b3a37 context/paragraphs hash；只安全SQL字段，不输出用户或密码。
- [ ] 完成当前后端打包后只重建MS03/gateway/frontend；使用现有部署流程核对compose env-file路径，不猜或读取密钥正文。不使用down -v。验V7、六服务UP、nginx -t、匿名问答401、前后原哈希不变；旧分析/预测页面可查看。
- [ ] 真实浏览器测试只填常用问题，未批准前不发送MaaS。页面无新分析时提示合理、按钮不自动扣费、旧记录读取正常。就完整可审阅页面向用户请求一次真实调用授权，说明目的和会消耗额度。
- [ ] 获准后仅执行一次真实问答，推荐“为什么预测走势向上，但部分价格低于原点收盘价？”，绑定E2b源分析。核对两种走势含义、五日值、日期、尚无有效样本和口径说明；保存后刷新记录及整页reload，SQL确认只有一笔新生成操作。若失败保留safe code与旧记录、定位原因；再付费需追加明确授权。
- [ ] 语义评价记录是否回答问题、是否混淆序列走势/原点、是否臆造准确率、是否把旧验证称为最新。单次云成功不是持续可用/预测准确证明；不足案例记录局限，不写虚构评价。
- [ ] 记录全部实际检查与仍待范围，代码由Codex完成、CodeArts未核实、无远程未push。精确暂存文档，commit“docs: 单股问答验收与后续范围”。

## 自查与执行入口

已覆盖设计十节：Task1输入/事实/输出，Task2私有存储/本人接口/共享槽/传输，Task3页面/代理/190秒与恢复，Task4权限/回归/实际生成/保存/刷新/不变快照。已补spec文件清单未明写的QuestionConfiguration、Spring Security新路由、fixture清理顺序及Nginx路由，这些是实现已确认行为必要接入，无业务新增。

QuestionInput/Facts/Answer/Claim类型在各任务一致；状态未知优先GET恢复；GET404保留原op并允许用户明确恢复同原POST，租约过期通过原POST只做终态清理，不自动新生成。无词语过滤可保证任意问题语义安全的承诺；严格数据过滤与人工语义评估分别记录。真实MaaS额外付费未获本轮授权，留最终具体页面审批。

用户审阅计划确认后，读取executing-plans按本会话直接执行；执行时先复核工作区与隔离需求，不创建未经请求的新聊天，不套用E:\TianMao电商规则。

## 2026-10-09复核补充：执行时必须遵循

### A. 规范化、JSON与跨用户请求号

Java与浏览器使用同一明确规则：检查原文是否有Unicode Cc类别控制字符和不配对代理项，NFC后去除两端Unicode White_Space，再按码点计数。两端空格集合固定为U+0009—000D/U+0020/U+0085/U+00A0/U+1680/U+2000—200A/U+2028/U+2029/U+202F/U+205F/U+3000，其中Cc字符先拒绝，即便处于两端也不接受。不能依赖Java strip与JS trim默认集合一致。纯空格与NBSP、BOM、组合字符、补充平面和控制字符分别测试；BOM不在trim集合，明确作为输入字符处理，不悄悄删除。

root严格字段还需开启新问答局部JSON解析器STRICT_DUPLICATE_DETECTION及尾随token拒绝；不要全局改变E1/E2解析器。重复question/analysisId、尾随对象、非文本UUID、非整数page均400。2048字节限制优先于字符数，文档说明JSON转义过多也可能超过体积限制；不得承诺任意序列化的300字均可接受。

请求号属于其他用户必须404；本人相同op变参数409。先完成认证和operation归属检查再触发共享槽的恢复，不能以变更参数错误泄露另一用户的op是否存在。

### B. 刷新、未落库和过期恢复

Task3增加frontend/src/stores/questionRecovery.ts及questionRecovery.test.ts，并修改stores/auth.ts和auth.test.ts在forget/signOut/身份切换时调用清理函数；仅负责同标签页sessionStorage的恢复快照。发送前保存userId、operationId、analysisId、规范化question，不保存Cookie/凭证/完整分析或回答。key包含当前用户与分析归属，写入异常则阻止新发送并给可理解提示，不能偷偷继续导致刷新丢op。终态、退出或身份切换移除对应数据，其他用户不得读出旧问题；服务端每次仍独立鉴权。源文本仅用于恢复用户主动提交的问题，禁止打印日志。这是同源JavaScript可读的页面恢复数据，不是加密凭证存储；限制只留未确认操作，不缓存所有聊天历史。成功/失败终态立即删除，标签页关闭由sessionStorage生命周期清理。

刷新优先恢复本地固定op并GET操作状态；服务器latestAttempt可作为无本地快照时的恢复依据，但不能覆盖不同本地pending。同一分析先恢复原请求再发送新问题。GET404不是清pending依据，用户点击恢复时POST同原op，不自动轮询POST。GET到期expired仅显示结束过期请求按钮，明确POST原op进行失败终态清理，不发送新MaaS调用。已领取同op先回放；尚未领取同op先完成本人源分析核对再领取，全局锁串行保证至多一个实际执行者。

补测试：POST在claim前延迟、GET暂404、同op恢复与旧POST并发；刷新/重挂组件保留op；用户切换清除本地问题；storage quota拒绝发送；expired不永久锁按钮；恢复失败不新建op。跨标签页同时提交属于两个独立操作，共享全局槽防并发，不宣称能消除用户主动新建的所有重复请求。

### C. 白名单知识、配套引用及输出错误

不能把context.facts任意字段全部发给模型。QuestionFacts白名单与当前AnalysisFacts/ExplanationFacts逐一对照列出：STOCK/RANGE_FROM/RANGE_TO/AS_OF/CLOSE/MA5/MA10/MA20及对应STATE、DIF/DEA/MACD/RSI14及对应STATE、BASIS_LIMIT/FRESHNESS_LIMIT；预测仅使用实际存在的PRED_ORIGIN_DATE/PRED_ORIGIN_CLOSE/PRED_TREND/PRED_RELATIVE_ORIGIN/PRED_VERIFICATION/PRED_LIMIT/PRED_CONTEXT_RELATION、PRED_D1—D5/PRED_P1—P5及PRED_METRICS（仅源中实际存在时）。PRED_MODEL/PRED_TRAINING_CUTOFF与PRED_R1—R5本阶段默认不发送，元数据仅在本人依据面板展示；扩展须明确白名单与测试。未定义key不得自动编造；未知源key默认忽略，有效必需key缺失拒绝生成。固定QA定义由程序常量提供，不接受源JSON或用户覆盖QA_*。

INDICATOR需匹配同一指标的定义与状态：MA定义配MA5/10/20_STATE，MACD定义配MACD_STATE，RSI定义配RSI_STATE，不能用MA定义和RSI数值冒充回答。预测主题按设计必引；任一段出现PRED_*引用，即使topic声称RISK/INDICATOR，也必须整体含预测原点、验证状态、预测限制与PRED_CONTEXT_RELATION，防止借主题分类绕过口径要求。每个价格PRED_Pn出现时对应PRED_Dn和PRED_ORIGIN_DATE必须出现，目标日期与价格不能错配。三段所有ANSWERED必须AS_OF/BASIS_LIMIT/FRESHNESS_LIMIT。

合法OUT_OF_SCOPE应topic=LIMITATION、paragraphs=[]；固定说明区分一般不支持与分析未引用预测，后者只根据服务器facts是否存在说明，不冒称模型提供准确分类。缺预测时的错误ANSWERED一律AI_OUTPUT_INVALID；模型分类、贴题和完整语义仍需人工案例评价，不能承诺靠上述校验消除幻觉。

QuestionFacts保存源contextHash作为来源标识，但不对MySQL返回的JSON字面序列直接重新hash比对原Java hash（MySQL会规范化对象键顺序）；问答自己的context使用确定性排序结构计算hash。保存源分析副本或所需版本化元数据，历史问答显示其生成时验证版本，不另查新验证头。

### D. 缓存与操作的原子性

questionContextHash必须包含sourceAnalysisId、sourceContextHash、规范化question、knowledgeVersion、questionPromptVersion和实际providerModel。两个analysisId即使源事实相同也不混为一份问答，否则缓存answerId会挂在错误分析下。user在唯一键与所有查询内独立过滤。

单一共享槽行锁覆盖replay/cache/claim及终态提交；SQL事务中的验证失败必须回滚。缓存命中也创建本次操作SUCCEEDED回执，但无云调用；操作与答案analysis_id/user_id必须一致。租约到期原操作FAILED且迟到complete返回既有失败回执，不插答案、不释放后来操作的槽。补同源同问题并发、不同源analysis相同facts、失败重试、cache命中时另一类型占槽的测试。

### E. 实际测试命令与验收日期

frontend/nginx.test.js导入vitest且readFileSync相对frontend目录，直接node运行错误。统一使用pnpm --dir frontend exec vitest run nginx.test.js；完整pnpm --dir frontend test也包含该测试。命令执行后立即校验LASTEXITCODE，不执行未知路径脚本。

Task1文本块示例闭合须三引号；接口段是签名约定，不作为可直接编译的record源码复制。Task2运行聚焦命令应明确列出QuestionMysqlTest,QuestionHttpTest,QuestionServiceTest,MaasClientTest；日志日期按实际执行日。最终证据文件以实际验收日期命名，不能沿用设计日伪称2026-10-07验收。

本次只审核修订计划/设计措辞并运行现有Nginx回归验证命令可用（1测试通过），未运行新增问答测试、未写问答实现、未迁移部署、未调用MaaS。执行前先检查Docker/隔离测试库健康，不把历史311/65结果当本轮通过。

### F. 补充检查的明确执行方式

Task2安全输出日志要求覆盖复用ExplanationTemplate：删除unsafe literal token原文日志，仅保留固定UNSAFE_LITERAL reason；原E1/E2的核验行为保持，回归ExplanationTemplateTest。不能仅约束QuestionService而遗漏共享渲染器的日志输出。

Task3恢复存储的接口明确为savePending(userId,input)、readPending(userId,analysisId)、removePending(userId,analysisId)、clearQuestionRecovery(userId)；其实现放stores/questionRecovery.ts，避免auth store引用pages组件。save失败抛受控前端错误。read校验JSON结构、UUID和当前用户，不信任浏览器存储能赋予权限；损坏记录移除并说明，不自动构造新付费请求。profile初始null仅等待账号确认，不清除同用户刷新快照；显式forget/退出/身份切换才清旧用户快照。测试覆盖初始null到同用户正常恢复，不能在初始化时误删。

计划中的验收证据名称采用实际日期替换规则是文件命名规范，不是缺失实现内容。计划文档使用设计日文件名便于既有链接，复核日期记录在正文。

## 执行记录说明
2026-10-09 Task1—3核心实现和自动回归完成，完整328项后端、77项前端通过；详细测试矩阵中未逐条独立断言的扩展样例保持未勾选，不能把核心回归等同计划每一种样例都已覆盖。唯一审查的两项Important均已修复；其他矩阵扩展记为后续质量补强。部署已完成，真实云调用仍待授权。
