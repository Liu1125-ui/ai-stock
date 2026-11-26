# SDD008C 持仓规则说明与AI问答 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. 沿用用户已选本对话Native执行，不为实现任务派发子代理；结束时按技能执行一次独立整片审查。

**Goal:** 在本人固定估值下提供免费规则说明及显式发送的有依据AI问答，保存并恢复本人历史。

**Architecture:** 预测服务portfolioquestion拥有规则/事实/问答/操作记录，读取本服务PortfolioRepository本人估值。复用MaaS HTTP传输和统一单槽，新建持仓输出协议，不改变旧问答协议。网关精确代理，前端由当前估值挂载问答面板。

**Tech Stack:** Java17、Spring Boot3、JdbcTemplate/MySQL8/Flyway、Vue3/TypeScript/Vitest。

**Spec:** docs/superpowers/specs/2026-10-19-sdd008c-portfolio-question-design.md（用户2026-10-19回复“实施”确认书面设计）。

## Global Constraints

- 固定E:/aiStock/项目代码/stock-system、现有codex/sdd004-market-refresh分支，小步选择性提交；不改变五服务和十三业务范围。
- USER/ADMIN只读本人已保存估值；不访问新行情、不重算估值、不跨库联查，客户端不得指定owner/facts/model。
- 规则明确不调用AI；自由问题显式发送才调用，所有GET/刷新/常用按钮均不得调用模型。
- 问题trim/NFC后1—300 Unicode码点，控制字符拒绝；JSON体4096字节，重复/额外字段及尾随JSON拒绝。
- MaaS不发送quantity/averageCost/身份凭据/原始快照；汇总金额仍为私人信息，告知确认仅绑定本次选中估值并刷新重置。
- 100持仓、1800事实、事实JSON96KiB、提示预算配置校验；拒绝超限，不静默截断。默认提示总字符预算65536，计算完整system+user编码前文本字符数；它是本地容量保护，不声称等于token计数或模型上下文上限。
- 输出1—2段、每段500字符；严格引用；固定范围/日期/缺失/过期警示来自程序；数字检查不等于语义全正确。
- 连接2秒、MaaS请求35秒、业务deadline45秒、物理租约50秒、事务5秒、网关读取65秒、Nginx190秒；取得锁后重新读Clock，迟到不保存。
- 全局统一槽新增PORTFOLIO_QUESTION；原接口、后台开关和超时不改。
- 云端真实验收当前授权0次；先测试/审查/备份部署，真实云调用上限另行取得授权。

## Review Focus

1. PARTIAL且mixedDates=true或UNKNOWN同时存在：全部限制保留，不能只显示一种；Task1/2测试。
2. 规则入口可免费但其他账号analysisId不可读，浏览器勾选绕不过来源权限；Task1/4测试。
3. 云返回时等待数据库锁跨过45秒：原请求失败且无成功答案，其他MaaS操作槽不被误释放；Task3测试。
4. 切换估值或账号时旧答案迟到：不可显示、告知不可沿用，UUID恢复不能重复收费；Task5测试。
5. 恶意问题要求输出未传的数量/成本或改变事实：源数据白名单/占位符/主题校验保留；语义贴题仍单独人工评估；Task2/4测试。

## 文件与协议分解

以下主源码目录均为backend/stock-prediction-service/src/main/java/com/aistock/prediction/portfolioquestion/，测试目录为同模块src/test/java/com/aistock/prediction/portfolioquestion/；逐项列出的文件全部位于这两个确切目录。

新增主源码：PortfolioQuestionRecords.java（DTO）、PortfolioQuestionInput.java（严格输入）、PortfolioQuestionFacts.java（白名单与hash）、PortfolioRuleService.java（规则）、PortfolioAnswerTemplate.java（输出）、PortfolioQuestionPrompt.java（专用提示）、PortfolioQuestionRepository.java（本库状态/缓存/游标）、PortfolioQuestionService.java（编排）、PortfolioQuestionController.java（公开接口）、PortfolioQuestionConfiguration.java（装配）及README.md。

新增测试：PortfolioRuleServiceTest.java、PortfolioQuestionFactsTest.java、PortfolioQuestionInputTest.java、PortfolioAnswerTemplateTest.java、PortfolioQuestionRepositoryTest.java、PortfolioQuestionMigrationTest.java、PortfolioQuestionServiceTest.java、PortfolioQuestionControllerTest.java。

统一records签名：

```java
record Request(UUID operationId, UUID analysisId, String question, boolean disclosureAccepted) {}
record Facts(SortedMap<String,String> values, JsonNode localContext, String contextHash,
             String promptVersion, String providerModel) {}
record Answer(String status, String topic, JsonNode paragraphs) {}
record Receipt(UUID operationId, UUID analysisId, String question, String state,
               UUID answerId, String errorCode, Instant deadlineAt) {}
record Claim(boolean execute, Receipt receipt) {}
record Page(List<JsonNode> items, String upper, String nextBefore, Receipt latestAttempt,
            boolean configured) {}
```

来源PortfolioRepository.get(UUID owner,UUID analysisId)返回JsonNode，含analysisId/createdAt/sourceHash/bookRevision/asOfDate/state/freshness/mixedDates/lines/subtotal/totals。任何Facts构造只信已校验来源，不接受前端拼装。

## Task 1: 本人规则说明与最小事实集

**Files:** 新增上述Records/Input/Facts/RuleService及README；测试Input/RuleService/Facts。不改旧QuestionFacts。

**Interfaces:** PortfolioQuestionInput.normalize(String)->String及parse(byte[])->Request；PortfolioQuestionFacts.build(JsonNode saved,String question,String model,ObjectMapper)->Facts；PortfolioRuleService.describe(UUID owner,UUID analysis,String topic)->JsonNode，构造器接PortfolioRepository。facts使用稳定短ID如AS_OF、COVERAGE_LIMIT、S0001_CODE、S0001_PROFIT，字典序排序，context本机保留原analysisId/sourceHash、版本和模型，但发云只values。

- [x] Step1：编写固定合成估值测试；成本/市值直接来自saved，不自行再算；原数量成本字段作为敏感哨兵，不出现在values。覆盖PARTIAL+mixedDates+UNKNOWN限制并存、无totals/weights不补造、EMPTY/全缺AI不合格、零成本和STALE。规则topic非法400，跨人来源404。测试关键断言：
```java
assertThat(facts.values().values()).noneMatch(v -> v.contains("PRIVATE_QUANTITY_SENTINEL"));
assertThat(facts.values()).containsKeys("AS_OF", "COVERAGE_LIMIT", "FRESHNESS_LIMIT");
assertThat(ruleService.describe(owner, partialId, "COVERAGE_LIMIT").path("source").asText())
    .isEqualTo("RULE");
verifyNoInteractions(maasClient);
```
- [x] Step2：先运行定向测试确认缺接口/实际行为RED；Maven在backend目录，使用下方隔离测试命令，记录日志。
- [x] Step3：实现输入校验与完整来源结构校验，owner由调用层强制验证；可读事实含服务端生成正负/排名/合法占比提示、原日期和缺失原因，规则文本版本portfolio-rules-v1。sourceHash/analysisId/模型/问题NFC/提示及输出版本共同构成cache上下文hash；排序确定性。限制字段固定为同一渲染模型供前后端使用。
- [x] Step4：通过定向测试，加入100股/1800事实/96KiB和配置预算边界；无效或超限在云前拒绝，不生成半份事实。保留完整本地原证据，日志禁止打印。
- [x] Step5：只提交本任务文件，feat: 提供本人持仓规则说明与问答事实。

## Task 2: 持仓专用AI输出与安全提示

**Files:** 新增PortfolioAnswerTemplate.java、PortfolioQuestionPrompt.java及TemplateTest；修改backend/stock-prediction-service/src/main/java/com/aistock/prediction/explanation/MaasClient.java、HttpMaasClient.java；扩充该模块原HttpMaasClient测试。

**Interfaces:** MaasClient新增default JsonNode generatePortfolioQuestion(String question,Map<String,String> facts)；默认受控AI_NOT_CONFIGURED。PortfolioAnswerTemplate.render(JsonNode raw,Facts facts,ObjectMapper)->Answer。单股/对比方法签名和默认行为不变。

- [x] Step1：写输出契约RED：1/2段合法、0/3段非法；裸数字/未知占位符/refs遗漏/重复字段/尾随JSON/HTML/未经引用的方向断言/交易指令拒绝；OUT_OF_SCOPE空段落合法并由程序给固定提示。缺日期/范围/主题引用失败，不用前端警告掩盖错误。示例：
```java
assertThrows(BusinessException.class, () -> render(answerWithLiteral("收益为123元"), facts));
assertThat(render(validTwoParagraphs, facts).status()).isEqualTo("ANSWERED");
assertThrows(BusinessException.class, () -> render(concentrationWithoutThreshold, facts));
```
- [x] Step2：运行TemplateTest/HttpMaasClient相关测试记录RED。
- [x] Step3：增加独立持仓提示portfolio-question-v1、知识portfolio-knowledge-v1、输出portfolio-answer-v1。构造固定system及独立user消息，服务端values不得由问题覆盖；实现严格外层/内层JSON与8k正文/原HTTP响应体限额。只复用安全HTTP传输，禁止改变旧三段模板。总提示字符预算覆盖system+user完整序列化内容，超限受控拒绝。
- [x] Step4：本地HTTP stub确认35秒上限、取消/大小、错误映射及无自动重试；核验请求不含quantity/averageCost/身份/内部hash。旧问答/解释/比较回归通过。
- [x] Step5：feat: 增加持仓问答专用提示与引用核验。

## Task 3: 持久问答、去重与统一调用槽

**Files:** 新增PortfolioQuestionRepository.java；新增backend/stock-prediction-service/src/main/resources/db/migration/V11__portfolio_questions.sql；修改同目录README.md及maas/MaasOperationGate.java；RepositoryTest/MigrationTest及原MaaS槽测试。

**Interfaces:** replay(UUID owner,Request request,Clock clock)->Receipt或null；claim(UUID owner,Request request,Facts facts,Clock clock)->Claim；complete(UUID owner,UUID op,Answer answer,String safeError,Clock clock)->Receipt；operation(UUID owner,UUID op,Clock clock)->Receipt；get(UUID owner,UUID answer)->JsonNode；list(UUID owner,UUID analysis,String before,String upper,boolean configured,Clock clock)->Page。锁顺序固定统一槽→本操作，所有需要同槽操作遵守此序，防止反向死锁。

- [x] Step1：真实隔离MySQL写RED：同op并发仅执行一次、同owner不同参数409、跨owner404、同context成功缓存、失败不冒充缓存、同analysis页固定upper、V10旧估值迁移后不变。合成fixture与生产数据完全分开。
- [x] Step2：建V11：result(answer_id PK,history_seq UNIQUE AUTO_INCREMENT,owner_id,analysis_id,question,context_hash,context_json,status,topic,paragraphs_json,created_at)，UNIQUE(owner_id,analysis_id,context_hash)，本库(owner_id,analysis_id)FK引用portfolio_analysis_result；operation(operation_id PK,history_seq UNIQUE AUTO_INCREMENT,owner_id,analysis_id,question,request_hash,context_hash,context_json,state,answer_id,error_code,accepted_at,deadline_at,lease_until,generation,finished_at)，结果用复合owner/answer FK禁止挂错人；状态CHECK RUNNING/FAILED/SUCCEEDED及answer/error一致。context JSON不含凭据，大小<=256KiB，正文<=16KiB。
- [x] Step3：扩展统一槽PORTFOLIO_QUESTION的支持/过期映射；过期持仓操作按deadline45秒或lease50秒终结，旧kind仍原策略。不能误释放另一个operation的槽。claim后释放数据库事务再调用HTTP，缓存命中不抢占槽。
- [x] Step4：运行持久并发/过期RED→GREEN，加入实际行锁等待并通过可控Clock跨45/50秒：complete取锁后读时钟，晚到无答案、原请求FAILED、槽正确释放。任何插入或最终update失败事务回滚不留孤立答案。跨kind全局互斥测试必须包含原四类。
```java
assertThat(repository.complete(owner, op, answer, null, advancedClock).state()).isEqualTo("FAILED");
assertThat(countResultsFor(op)).isZero();
assertThat(otherKindStillOwnsSlot()).isTrue();
```
- [x] Step5：通过V10→11合成估值/预测/比较保留与重复迁移测试；feat: 保存持仓问答并统一去重和调用预算。

## Task 4: 本人问答编排与公开接口

**Files:** 新增PortfolioQuestionService/Controller/Configuration及测试；修改预测安全路径配置、application.yml无密钥模板与README。默认aistock.portfolio-question.enabled=false。

**Interfaces:** Service.generate(UUID owner,Request request)->Receipt；rules/get/operation/list对应Task1/3。Controller消费现有Actor和会话校验，不接受客户端owner。严格路由如设计第6节；解析Request与Query局部严格验证。

- [x] Step1：写控制器/服务RED：匿名401、CSRF/Origin拒绝、其他人404、合法ADMIN仅本人、空/全缺AI拒绝、disclosureAccepted=false拒绝、原op状态先查、缓存不调用、云失败保留、无取数/训练/邮件。GET只读不会为新问题创建操作。
- [x] Step2：实现先replay→本人来源→告知/能力/预算→facts→claim→一次MaaS→严格render→complete；未知异常转换AI_UNAVAILABLE，日志只分类。规则能力不依赖AI开关。对先前合法原操作GET恢复不要求重新告知，新的POST或缓存请求仍必须告知。
- [x] Step3：显示配置状态来自服务端，接口关闭明确AI_NOT_CONFIGURED，不泄露模型密钥；错误统一格式/traceId。恶意question不可改变数据来源、服务地址或提示。
- [x] Step4：服务/控制器/全部预测服务回归通过；feat: 开放本人持仓规则与问答接口。

## Task 5: 网关、前端及刷新恢复

**Files:** 新增backend/stock-gateway/src/main/java/com/aistock/gateway/PortfolioQuestionPublicClient.java、PortfolioQuestionClientConfiguration.java、PortfolioQuestionApiProxyController.java及对应同路径测试；修改网关SecurityConfiguration与README。新增frontend/src/api/portfolioQuestions.ts、frontend/src/pages/portfolio/PortfolioQuestionPanel.vue及各.test.ts；修改PortfolioAnalysisPanel.vue、frontend/src/api/auth.ts、frontend/nginx.conf；现有目录README更新。

**Interfaces:** createQuestion(Request)->Promise<Receipt>；getQuestionOperation(UUID)、getQuestionAnswer(UUID)、listQuestions(analysisId,before?,upper?)、getPortfolioRule(analysisId,topic)。Panel props analysis:PortfolioAnalysis/currentBookRevision:string|null，挂载于已选估值内，key=analysisId；后端返回完整固定限制，前端不计算金额。

- [x] Step1：前端RED：免费规则不POST、常用按钮只填文本；告知未勾不可发、无估值/全缺/空禁止；切账号/估值清告知与旧答案、迟到不串；刷新仅GET恢复；规则在AI关闭可用；失败保留估值和旧回答。sessionStorage只UUID/analysisId，存储失败阻止新发送以免刷新丢失后误重复。
- [x] Step2：实现严格typed API和独立面板，当前请求发送后冻结问题/id，pending显示查询原请求；404保留未知状态，不自动换新ID。用户明确恢复只能原POST；终态用户明确再发才新ID。规则与AI源标签、固定警示、依据日期、来源可展开；无v-html。
- [x] Step3：网关精确白名单路径/查询，转发Cookie/Origin/XSRF/Trace，不透传伪造服务头；connect2/read65/retryNever；Nginx AI正则新增portfolio-questions，普通8秒与portfolio-analyses60秒不变。匿名401/内部404/真实9秒隔离HTTP探针验证代理不提前中断。
- [ ] Step4：pnpm --dir frontend exec vitest run --maxWorkers=2及build，网关全回归；实际浏览器或用户手动验收窄屏/键盘/切换/刷新，未经真实检查保留pending。feat: 展示持仓问答与只读恢复。

## Task 6: 整片验证、备份和部署交接

**Files:** docs/verification-sdd008c.md、docs/evidence/sdd008c.json、docs/implementation-status.md、docs/pending-items.md；本片测试说明。

- [x] Step1：核对专用测试容器label/tmpfs及五个严格_test库URL，完整Maven串行 clean verify；前端全套和build。统计当次XML实际计数，不沿用745/187。
- [x] Step2：沿Native一次独立整片审查（使用requesting-code-review），Critical/Important实际复现修复；minor如延期写明。不让审查者读密钥或私人正文。
- [x] Step3：预测库专属账号V10一致性备份（私有runtime被Git忽略），隔离tmpfs独立库恢复，元数据核验；保存预测/网关/前端旧镜像。无DB生产删除。仅部署受影响服务，V11及健康/匿名401/内部404/真实资源校验；原后台开关不改。
- [ ] Step4：本片默认关闭部署自查，授权开启后先免费规则/本人历史/告知验收，确认无模型调用；真实AI必须另询调用上限，初次建议最多1次，失败不自动追加；每次生成/保存/刷新核对数字、refs和日期，同时人工评价自然与贴题，不因结构通过宣称完全可信。
- [ ] Step5：记录实际云调用数、用户页面结果、原估值未变、备份/迁移/镜像和剩余待办。回退旧镜像或关闭入口，保留V11和问答记录，不DROP；完整旧镜像回退未实跑则直说。临时测试DB在全部复核后停止，仅操作已标记fixture，不碰生产卷。
- [ ] Step6：选择性提交文档，检查git diff --check和工作树，保留FR-09其他范围、模型实验/长期运行/CodeArts真实证据等原待办。不把规则已完成当AI真实验收成功。

## 测试执行方法

沿用现有scripts/start-auth-test-db.ps1核验标签和tmpfs，秘密只读本机runtime/auth-test-db-secret.txt不输出。测试URL分别stock_user_test、stock_market_test、stock_prediction_test、stock_scheduler_test、stock_notification_test，地址127.0.0.1:13316，密码仅注入子进程环境；使用此前008B安全runner模板复制到本片忽略workspace后核对路径，禁止改指生产库。

在backend目录运行：
```text
mvn -B -Pauth-integration,cli-build -pl stock-prediction-service -am "-Dtest=PortfolioQuestion*Test,PortfolioRule*Test,PortfolioAnswer*Test" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -B -Pauth-integration,cli-build clean verify
```
前端：
```text
pnpm --dir frontend exec vitest run --maxWorkers=2
pnpm --dir frontend build
```
每任务先失败后实现和GREEN，定向命令可以按任务收窄测试类；Maven不可并行抢五库。全套成功后不无故重复扩大测试。

## 自查

规格1—4节由Task1/4/5覆盖，输出第5节Task2，API/库归属第6节Task3/4/5，恢复与预算第7节Task2/3/5，验证第8节Task6；五个Review Focus均分配实际测试。共享Request/Facts/Receipt/Page签名已统一。额外明确当前提示预算65536字符是保护上限，非模型token保证；告知只绑定选中估值；不做关键词免费分流。未实现产品代码、未进行云调用。

计划交用户审核后，沿用Native执行，每任务小步提交；不重复询问执行方式。

## 2026-10-20 代码检查点

Task1—4已完成；Task5代码已接入，前端203项及严格构建、网关真实HTTP探针通过。当前Docker desktop-linux与default均未见原aistock-sdd001容器或数据卷，用户确认未主动重置；原数据位置未确认，不创建空库替代，不声称部署/真实页面验收完成。Task5第4步页面门和Task6部署门保持未完成；先继续独立审查及完整隔离回归。

## 2026-10-20 审查与恢复交接

完整后端791/前端205及构建通过；独立审查Critical0 Important1 Minor0，输入拒绝恢复陷阱已复现修复并通过完整前端回归。Nginx真实隔离探针通过。发现D:/DockerData/DockerDesktopWSL/disk/docker_data.vhdx候选原磁盘，当前Docker配置指C盘；全局切换需明确确认，恢复方案已保存。Task5页面门、Task6生产备份/部署/云端验收继续未完成，执行workspace保留供继续。

## 恢复后检查点

原数据恢复、V10备份隔离恢复、三个旧镜像保留、V11部署及健康/鉴权/资产/旧估值哈希核验通过。实际页面和真实云端仍待验收；完整旧镜像回退未实跑，默认AI关闭。见verification-sdd008c.md最新结果。

## 单次真实复验结果

持仓V4适配及诊断修复e080f78经245项预测服务、206项前端和构建验证部署。用户追加一次授权后通过同编号恢复，2.737秒SUCCEEDED，1段5引用，来源关联和实际浏览器刷新保留通过，累计人工模型尝试2。开关关闭。Task5实际页面门、Task6 Step4真实样例门已核验；完整镜像回退仍未实跑，原待办保留，不宣称覆盖所有语义。
