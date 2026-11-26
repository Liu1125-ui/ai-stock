# SDD-008B 持仓估值与分析 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 沿用Native方式，整片一次独立审查。

**Goal:** 在已上线的本人持仓维护上，生成并保存可复算的市值、浮动盈亏、占比和集中度提示，透明显示日期、缺失与旧版本。

**Architecture:** 用户服务返回经实际会话验证的本人一致性持仓快照；行情服务批量只读固定已发布RAW价格；预测服务核对持仓版本、计算并保存不可变结果。只新增预测库V10，不跨库查询，不增加业务微服务，不调用模型或供应商。

**Tech Stack:** 现有Java17/Spring Boot3.2.5、用户服务MyBatis-Plus3.5.6、预测/行情服务JdbcTemplate与TransactionTemplate、MySQL8/Flyway、Vue3/TypeScript/Element Plus、JUnit5/Vitest；不引入新依赖。

**Spec:** `docs/superpowers/specs/2026-10-18-sdd008-holdings-design.md` 第5—9节。008A已部署，用户确认保存刷新正常；本计划基线2e668c9，尚未开始008B编码/测试/部署。

## Global Constraints

- 工作目录E:/aiStock/项目代码/stock-system，固定现有codex/sdd004-market-refresh分支；13业务/五服务/USER和ADMIN不变；不套用TianMao约束。新增代码目录随建README，按功能选择性提交。
- 原始持仓仅在用户库；结果与分析证据仅在预测库；行情仅在行情库。跨服务只用已授权HTTP，不建立跨库FK/SQL，不将结果副本变成另一套可编辑主数据。
- 所有输入身份由真实会话裁定，ADMIN无他人持仓权限。不得相信客户端userId、数量、成本、价格、bookRevision或阈值。不得读取聊天密码/真实密钥、伪造会话或在真人账号代填持仓。
- 本片业务使用每人最多100条完整持仓；超出100明确拒绝，不截断后做组合。未来扩大用户服务上限须同步调整本片策略/边界测试。
- 金额用BigDecimal/HALF_UP，价格及平均成本最多6位小数，数量整数股；外部金额/数量/版本为字符串。每行市值和成本各舍入2位，浮盈为二者差，合计直接累加显示金额。
- 非零总市值且所有有效价格同日才能计算占比；集中度默认50%，严格超过触发，等于不触发；只表示单股占比提示。零成本收益率为空，不是0%。不计算已实现收益，不自动处理送转/分红或给出买卖结论。
- COMPLETE/PARTIAL/MIXED_DATES/EMPTY与FRESH/STALE/UNKNOWN分开表示；缺价不是零。缺价优先状态PARTIAL并另带mixedDates布尔值；全部有价但日期不同才状态MIXED_DATES。PARTIAL/MIXED_DATES只显示可估值部分小计，完整总计、权重和集中度结论为空。
- 只读取已发布的baostock/RAW版本；不混用复权数据或其他供应商。报价日期不得晚于服务端asOfDate；保留实际价格日、版本/hash/来源/读取时刻，不能把日线称为实时价。
- 页面初次加载/刷新仅GET本人持仓及已保存分析；新增分析必须点击生成。不会采集行情、运行Transformer、调用MaaS或发送邮件；对应人工触发预算均0，既有后台任务保持。
- API统一{code,message,data,traceId}，Origin/CSRF照旧；错误不泄露持仓正文、会话/SQL/密钥。日志/本机证据不记录用户真实数量和成本，备份只在ignored runtime。
- 旧持仓、预测、对比、模型和AI依据不可覆盖。远程CI无执行证据不宣称通过。CodeArts真实证据仍待补，不伪造生成来源。

## Review Focus

1. 生成中持仓被修改或账号会话被撤销：Task1/4实际核验同一会话的owner/bookRevision，不混算、不继续使用过期身份。
2. 周末、节假日、停牌、日历过期：Task2区分已保存实际价格日与新鲜度；未知不能判成FRESH，停牌不能造出当天成交。
3. 丢失响应或进程重启：Task3原requestId不重新读取行情生成另一结果，过期在途请求终止，旧worker不能补写成功。
4. 分项四舍五入、零成本及50%边界：Task3固定人工算例/微小金额/精确阈值比较，显示合计一致，缺失部分不冒充全组合。
5. 旧分析覆盖新分析、跨账号晚返回、代理先超时：Task4/5 owner代际、明确选定结果、受控原请求恢复，Task5实际Nginx慢响应探针。

---

## 文件与协议地图

用户服务现有position包新增PositionSnapshotController.java、PositionSnapshotRecords.java，修改README；复用PositionService.list及PositionRepository一致性事务。新增PositionSnapshotControllerTest.java与对应README说明。用户库无新增迁移。

行情服务新增`src/main/java/com/aistock/market/valuation/README.md`、ValuationQuoteRecords.java、ValuationQuoteRepository.java、ValuationQuoteService.java、ValuationQuoteController.java；对应同路径测试包README及ValuationQuoteMysqlTest.java、ValuationQuoteControllerTest.java。复用现有市场内部服务身份过滤器、MarketVersionRepository数据和CalendarService.covering，不调用CalendarService.refresh。

预测服务新增`src/main/java/com/aistock/prediction/portfolioanalysis/README.md`、PortfolioRecords.java、PortfolioCalculator.java、PortfolioSourceVerifier.java、PortfolioDependencyHttp.java、PortfolioSources.java、PortfolioRepository.java、PortfolioService.java、PortfolioController.java、PortfolioConfiguration.java；新增本服务V10__portfolio_analyses.sql；对应同路径测试包README及CalculatorTest、SourceVerifierTest、RepositoryMysqlTest、ServiceTest、ControllerTest、DependencyHttpTest（类名均以Portfolio开头）。修改PredictionSecurityConfiguration及application.yml、compose模板的本片URL/阈值配置。

网关新增PortfolioApiProxyController.java及PortfolioProxyHttpTest.java，修改ProxyHeaderPolicy.java；前端修改nginx.conf，新增api/portfolioAnalyses.ts及测试、pages/portfolio/PortfolioAnalysisPanel.vue、PortfolioAnalysisPanel.test.ts，接入PortfolioPage.vue，更新其README与页面测试。现有持仓编辑器逻辑保留。

协议固定：
```java
record SnapshotRequest(String token) { public String toString(){return "SnapshotRequest[redacted]";} }
record PositionSnapshot(UUID ownerId,String sourceService,String bookRevision,List<PositionItem> items) {}
record PositionItem(UUID positionId,String instrumentId,String quantity,String averageCost,String revision) {}
record QuoteRequest(LocalDate asOfDate,List<String> instrumentIds) {}
record Quote(String instrumentId,String status,String reason,String close,LocalDate priceDate,
 UUID versionId,String contentHash,String provider,String priceBasis,String freshness,
 UUID calendarVersionId,String calendarHash,LocalDate checkedThrough,boolean retainedPrice) {}
record QuoteBatch(LocalDate asOfDate,Instant capturedAt,List<Quote> items) {}
record Request(UUID requestId) {}
record Operation(UUID requestId,String state,UUID analysisId,String errorCode,Instant acceptedAt,Instant expiresAt) {}
record Creation(Operation operation,boolean replayed) {}
record Claim(boolean execute,UUID generation,Operation operation,boolean replayed) {}
record MoneyLine(UUID positionId,String instrumentId,String quantity,String averageCost,String positionRevision,
 Quote quote,String costAmount,String marketValue,String floatingProfit,String returnPercent,
 String weightPercent,Boolean concentrationTriggered) {}
```
结果Calculation包含formulaVersion=`portfolio-valuation-v1`、bookRevision、asOfDate、capturedAt、state、freshness、mixedDates、positionCount/valuedCount、lines、subtotal（已估值部分成本/市值/浮盈）、totals（仅COMPLETE）、concentrationThresholdPercent、sourceHash；完整结构由PortfolioRecords.java一次定义并供前后端同名接口使用，不把空金额用字符串"null"代替。

## Task 1:经本人会话授权的持仓快照读取

**Files:** 用户position/PositionSnapshotController.java、PositionSnapshotRecords.java、README及PositionSnapshotControllerTest.java。

**Interfaces:** POST `/internal/v1/positions/snapshot`与`/internal/v1/positions/revision`，请求均恰含token，内部X-Service-Token必需。snapshot响应PositionSnapshot；revision响应ownerId/sourceService/bookRevision三字段；两者都在用户服务通过SessionService.resolve(token)决定owner，不能接受userId。

- [x] Step1：先写真实隔离库+会话测试：USER读取自己且不读ADMIN、ADMIN读取自己且不读USER；伪userId字段400、仅服务token而缺会话401、会话撤销/过期401、错误服务token先于正文解析401、缺内部认证配置503。先看到端点/行为缺失的失败。
```java
var result=snapshot(validSessionOfA,validServiceToken);
assertEquals(a,result.ownerId());
assertFalse(result.items().stream().anyMatch(x->x.positionId().equals(positionOfB)));
revoke(sessionOfA);
assertEquals(401,snapshotStatus(sessionOfA,validServiceToken));
```
测试辅助函数在PositionSnapshotControllerTest通过真实MockMvc与SessionService建立，凭据只用合成隔离fixture。
- [x] Step2：内部服务认证常量时间比较，先校验服务身份再严格读取JSON（1024字节、仅token、token格式与SessionService一致）。DTO.toString脱敏。快照复用现有一致性list事务；revision通过PositionService.list取得当前版本也可接受当前100条开销，不另开不一致读取逻辑。
- [x] Step3：证明GET公共持仓/既有CRUD不变，内部接口不能经Nginx或网关路由访问；不修改会话主表和用户库迁移。运行新增测试与用户服务回归，提交`feat: 提供本人授权的持仓快照接口`。

## Task 2:批量未复权收盘价证据

**Files:** 市场valuation目录上述5个文件及2个测试，复用已发布RAW表/交易日历；不新增行情表。

**Interfaces:** POST `/internal/v1/market/valuation-quotes`，请求QuoteRequest，最多100只、去重、证券代码规范，体积4096字节；返回QuoteBatch。服务内部验证X-Service-Token；客户端不得指定版本/价格/用户持仓正文。

- [x] Step1：固定Clock写隔离MySQL测试：只能用RAW/AVAILABLE/baostock；不得用STAGING或其他证券/复权价格；不得取晚于asOfDate的bar；重复代码拒绝；缺失明确NO_DATA/NO_VALID_CLOSE；100只完整返回且无截断。记录源Collector mock调用数为0。
```java
assertEquals("RAW",quote.priceBasis());
assertFalse(quote.priceDate().isAfter(request.asOfDate()));
assertNull(missing.close());
verifyNoInteractions(collector);
```
- [x] Step2：一个只读REPEATABLE_READ事务读取完整股票集合的head/version和价格，事务超时5秒；查询owner不存在，此接口只含公共证券数据。不在事务内做HTTP。每只固定当前已发布head版本，读取其`trade_status=1 AND trade_date<=asOfDate`的最后有效close，保留versionId/contentHash。最近一条trade_date<=asOfDate的已保存bar停牌时只沿用最近实际交易日价格并标记retainedPrice；无有效实际交易日不借用复权价、不造0元或当日成交。
- [x] Step3：asOfDate上界为北京时间每日20点前前一自然日、20点后当日；预测服务也使用相同策略，跨午夜旧请求仍可查询其原固定日期（合法历史日期不会被改成今天）。不把asOfDate解释为“当时已知全部信息”的历史回测知识时点；capturedAt单独表示这次读取时刻。
- [x] Step4：使用CalendarService.covering(priceDate,asOfDate)只读日历，记录日历版本/hash/expected最新开市日；有效日历覆盖且priceDate等于expected才FRESH，早于expected为STALE，日历缺失/过TTL/不连续为UNKNOWN；有效日历明确表明priceDate非开市日或价格日超过expected，则该行CALENDAR_CONFLICT不可估值，不能冒充FRESH。价格本身有效时未知日历不删除价格。测试周末/假期、停牌、旧日历、未来fetchedAt和不同日期。
- [x] Step5：每只返回一项，所有请求ID与返回ID集合完全一致；证券不存在也给明确不可估值行，不能静默略过。扫描大版本只取索引最后一条，不全量加载每只多年K线。测试事务快照遇并发head切换不拼接；运行市场回归后提交`feat: 提供只读批量持仓估值行情`。

## Task 3:确定性计算及不可变分析仓储

**Files:** 预测portfolioanalysis/PortfolioRecords.java、PortfolioCalculator.java、PortfolioSourceVerifier.java、PortfolioRepository.java、V10迁移及对应测试。

**Interfaces:** `PortfolioCalculator.calculate(PositionSnapshot,QuoteBatch,BigDecimal threshold)`→Calculation；`PortfolioSourceVerifier.verify(snapshot,batch,expectedOwner,asOf)`拒绝身份/集合/hash/格式不符。Repository方法`claim(owner,requestId,now,asOf,threshold)`、`complete(owner,requestId,generation,calculation,snapshot,quotes,Clock)`、`fail(owner,requestId,generation,errorCode,Clock)`、`operation(owner,requestId,now)`、`get(owner,id)`、`list(owner,before,upper)`。

- [x] Step1：先写纯计算测试，固定设计两股人工样例，校验5000市值、1000浮盈、50%不触发；甲价格30则3000/5500严格触发。加入零成本、正但舍入为0成本、单行半分、合计舍入、0总市值、空持仓、全部缺价/部分缺价、日期混合、日期混合同时缺价、100条。重复同输入结果值和sourceHash必须一致，不用当前系统时间参与公式。
```java
assertEquals("5000.00",result.totals().marketValue());
assertEquals("1000.00",result.totals().floatingProfit());
assertFalse(result.lines().get(0).concentrationTriggered());
assertNull(partial.totals());
assertNull(partial.lines().get(0).weightPercent());
```
- [x] Step2：Calculator先校验owner/sourceService/bookRevision、positionId/证券唯一、完整条数、整数/定点字符串、RAW/来源/版本/hash及价格日。缺价必须有明确原因；响应遗漏一只或重复一只属于协议错误，不能自动补成合法NO_DATA。close为正且最多6位小数；无效价格应由行情行明确不可估值，伪造/矛盾响应整次拒绝。sourceHash以固定排序的owner/持仓版本/原始数量成本/报价证据/规则/阈值计算，不包含会话token。
- [x] Step3：金额先各行HALF_UP到2位，再加总；浮盈是已舍入市值减已舍入成本。收益率=浮盈/成本×100，成本为0为空；占比以已舍入行市值/已舍入总市值计算，仅显示百分比舍入2位。阈值用`lineValue*100 > totalValue*threshold`精确比较，不先舍入比例。UNKNOWN/STALE按行透明保留，COMPLETE只代表覆盖与同日，不代表新鲜或能获利。
- [x] Step4：V10新增portfolio_analysis_operation及portfolio_analysis_result，所有行带owner_id；(owner_id,request_id)唯一、结果独立UUID、历史自增序号用于固定上界分页20条。operation固定acceptedAt/asOfDate/threshold/formula/lease_generation/lease_until；result保存固定snapshot_json/quotes_json/result_json/source_hash/created_at。仅预测库建表，不FK用户或行情库。
- [x] Step5：真实MySQL测试同requestId并发只一个执行者、成功后重放不重算、FAILED原请求只读重放、事务回滚不留孤立结果、跨owner结果404和分页固定上界。操作期45秒，过期RUNNING变FAILED/PORTFOLIO_OPERATION_EXPIRED，不接管同请求重新读取新价格；原worker仅在generation匹配、state=RUNNING且租约未过期时可完成。重启/超时后要生成新分析须用户明确使用新requestId，不自动重试或删除旧结果。claim或operation查询仅按本人原请求条件终结已过期状态，不扫描他人数据、不生成新结果。complete/fail注入Clock，取得行锁后及最终条件更新前重新读真实时钟，不能用等锁前传入的旧Instant通过租约；增加锁等待跨过30秒deadline/45秒租约的真实MySQL回归。
- [x] Step6：客户端POST仅requestId，不携带可变快照，入库时固定配置；操作claim后才读取持仓/行情。JSON不含token，事务超时5秒，持仓/报价/结果JSON分别256KiB/256KiB/512KiB上限。验证旧V9到V10升级保留对比/预测且重复迁移安全；提交`feat: 计算并保存不可变持仓估值结果`。

## Task 4:跨服务编排、身份重查及公开接口

**Files:** PortfolioDependencyHttp/Sources/Service/Controller/Configuration、相关预测安全配置/URL配置、PortfolioServiceTest、PortfolioDependencyHttpTest、PortfolioControllerTest。

**Interfaces:** POST `/api/v1/portfolio-analyses`只含requestId（1KiB上限）；GET列表仅before/upper，GET `/{id}`读取固定结果，GET `/operations/{requestId}`读取原操作。`PortfolioService.create(Actor,SessionToken,Request)`→Creation，其他GET只需Actor；内部SnapshotRequest.token只在本次调用内存流转。

- [x] Step1：模拟依赖前先写编排测试：Actor=A但快照owner=B拒绝；合法session创建快照后被撤销，revision重查401则不保存结果；相同owner但bookRevision改变返回409/PORTFOLIO_CHANGED；获取报价后修改平均成本不得混算；失败仍能读取原有分析。
```java
when(user.revision(session)).thenReturn(revisionOf(owner,"2"));
assertEquals("PORTFOLIO_CHANGED",createFailure(owner,session,request));
assertEquals(0,resultCount(request.requestId()));
assertEquals(oldResult,repository.get(owner,oldResultId));
```
- [x] Step2：按PredictionSessionFilter同样的Cookie选择语义提取本次有效AISTOCK_SESSION，不从JSON/查询参数接受token/userId；新SessionToken.toString始终脱敏。内部用户接口解析会话后返回owner，消费者同时比较已认证Actor；不持久化/打印token。测试重复同名Cookie与身份错配，不能因两个解析位置选择不同cookie产生跨用户读取。
- [x] Step3：编排顺序claim→本人snapshot→固定asOfDate报价batch→计算→本人revision重查→完成写入。revision接口再次验证会话有效及owner匹配；无HTTP跨库事务。第二次检查之后持仓再改变，结果仍属于旧bookRevision，前端明确旧版本，不覆盖历史。
- [x] Step4：PortfolioDependencyHttp复用既有BoundedComparisonHttp的流式限长思路但独立错误码/边界：连接2秒、snapshot5秒/quote8秒/revision3秒、响应256KiB、重定向禁止、无自动重试；超限/错误JSON受控失败。URL仅服务端配置，开发默认127.0.0.1:18081/18082，compose对应容器DNS；不能采纳客户端baseURL。市场调用纳入现有MarketDependencyGuard，熔断时返回不可用，不补造基线价格。
- [x] Step5：本进程并发上限默认2，可配1—8；已存在请求先读回执，容量满429不生成额外操作或收费。设置30秒编排deadline，每阶段前后及最终提交前验证，同时45秒租约防晚写；不声称这些检查构成整个分布式网络严格墙钟证明。BodySubscriber/future在超时后取消，响应一旦失败不继续读取。
- [x] Step6：公开Controller严格JSON/UUID/query及CSRF/Origin，401/404/409/413/422/429/503均返回统一失败结构。RUNNING202、首次成功201、重放200、终态失败在operation可查；不从浏览器POST导入价格或“更正”系统结果。源回执404与用户会话401保留语义，普通异常映射503，不暴露body。真实本地HTTP覆盖慢分块、重定向、大响应、缺服务身份及断连接。运行预测服务回归后提交`feat: 编排本人持仓估值与权限接口`。

## Task 5:网关、页面与固定结果恢复

**Files:** 网关PortfolioApiProxyController/PortfolioProxyHttpTest、ProxyHeaderPolicy；frontend/nginx.conf、portfolioAnalyses.ts/测试、PortfolioAnalysisPanel.vue/测试、PortfolioPage.vue/测试、README；auth.ts的401识别路径。

**Interfaces:** 前端`createPortfolioAnalysis(requestId)`、`portfolioAnalysisOperation(requestId)`、`portfolioAnalysis(id)`、`listPortfolioAnalyses(before?,upper?)`；数值为字符串。面板接收当前bookRevision，显示选定analysisId的固定证据，不能把刷新后的当前数量成本重新套进旧结果。

- [x] Step1：先测试POST体只带requestId，网页GET/刷新不生成分析；owner切换及晚返回不显示旧结果；网络未知先查原操作，不换编号自动重发；历史选中结果只显示其自身的数量、成本、日期与原始bookRevision。
- [x] Step2：面板支持明确生成、状态查询、本人历史选择；只在生成前写当前owner键的requestId到sessionStorage，不保存股票/数量/成本/token。刷新恢复该原请求状态，SUCCEEDED读取原analysisId、FAILED显示失败且保留先前选中结果，RUNNING仅查询状态；新生成必须由用户另按按钮取新UUID。切账号清内存并使用另一owner键，未完成请求不串账号。
- [x] Step3：展示行市值/成本/浮盈/收益率；结果状态、覆盖条数、实际日期/新鲜度、来源/版本和公式版本可查看。PARTIAL/MIXED_DATES醒目显示“可估值部分合计”，完整总计/占比/集中度不展示为0。COMPLETE才显示持仓占比；零成本收益率显示无法计算。旧bookRevision提示“持仓已修改，当前分析基于旧版本”；查询当前版本失败显示“暂无法核对当前持仓版本”，不能默认“当前”。
- [x] Step4：持仓新增/修改/删除并完成list加载后，面板对比bookRevision但不自动生成分析。空持仓生成固定EMPTY结果可供追溯（positionCount/valuedCount=0，小计三金额均0.00，完整totals为空，权重与收益率为空）；所有行缺价保留缺失列表和空小计金额（不能用0成本/0市值暗示全组合为零）。测试同日有旧价、不同日期、全缺、空集合、等于阈值和舍入提示。
- [x] Step5：精确放行新公开路径，转发Cookie/Origin/XSRF/TraceId且no-store，不开放内部snapshot。新增独立Nginx持仓分析路由read_timeout60秒，网关该客户端连接2秒/read50秒；普通其他API保持8秒，不复用190秒AI预算。采用真实本地9秒服务探针验证不被默认8秒提前截断，探针不连接供应商/AI。
- [x] Step6：完整前端测试/严格构建、网关实际HTTP错误状态、基础键盘和当前窄屏检查；金额从后端展示，不在JS里重新浮点计算；验证持仓CRUD/原预测入口仍正常。提交`feat: 展示可追溯持仓估值与历史结果`。

## Task 6:完整回归、备份、审查与部署验收

**Files:** docs/verification-sdd008b.md、docs/evidence/sdd008b.json、pending-items/implementation-status；相关tests/scripts README。

- [x] Step1：专用13316 MySQL标签/tmpfs核对后启动，构造合成持仓与行情，不读取真实用户持仓作为测试fixture。完整Maven只串行，五库URL必须与现有严格fixture一致；定向后执行`mvn -B -Pauth-integration,cli-build clean verify`，前端`pnpm --dir frontend test`及build。新计数从实际日志/XML读取，不沿用690/170旧结果。
- [x] Step2：一次整片独立审查，重点身份双重核对、同日/缺失语义、不可变回执/过期晚写、保存原输入、旧结果展示和代理超时；Critical/Important修复后验证受影响功能，不能把静态审查当运行证据。
- [x] Step3：备份预测库（stock_prediction专用账号），只在已标记测试容器的独立恢复库验证导入；用户和行情服务本片无新表，保留原镜像/配置。敏感备份被Git忽略，文档只保留元数据，不提交持仓正文或密钥。新增V10只含本库对象，旧结果不修改。
- [x] Step4：备份和审查通过后只构建/部署用户、行情、预测、网关、前端；先后端健康/V10/内部鉴权及匿名401再前端。调度、消息、algorithm、MySQL容器不重建；已有后台开关不改。服务URL使用compose内部DNS，启动探针验证不错误访问容器内127.0.0.1。
- [x] Step5：正常会话通过用户自行点击“生成估值分析”，然后刷新/历史选择；从已保存证据在本机独立定点复算，报告仅记录金额一致/不一致结论，不暴露数量成本。用户已有持仓可用于其本人验收，代理不代填或交易；没有可用会话则请用户自行登录，不伪造cookie。
- [x] Step6：记录实际服务健康、版本、真实页面范围、调用预算与待办。失败回退本片旧镜像/关闭新入口，保留V10及已生成记录，不自动DROP表。008B不代表AI持仓问答完成；FR-09及原模型实验等仍保留。选择性提交文档并检查无遗漏。

## 自查与交接

设计第5节报价/状态在Task2/3/5，第6节舍入/阈值在Task3，第7节保存/并发在Task3/4/5，第8节鉴权接口在Task1/4/5，第9节验收在Task1—6；没有删除008A后续双角色浏览器待办或FR-09问答。五类Review Focus均有对应实际测试。

本片不持久化新行情头，来源快照保存至预测库结果；capturedAt与asOfDate不同，防止把当前修订数据当历史当时已知。生成的持仓信息只在本系统服务间流转，市场服务仅接收公开证券ID，不接收私人数量/成本。设计中的完整性状态优先级、超限拒绝、原请求过期不重新取数和代理预算已在本计划固定。

自查同时关闭了两处协议歧义：Creation/Claim显式携带replayed用于201/200状态；持久完成在取得锁后重新读Clock，防止旧时间绕过超时。

本轮仅编写/自查计划，未实现008B、未执行上述测试、未调用供应商/AI。请用户审核后沿用当前Native执行，不重复询问执行方式。重大业务取舍变化再修订设计，常规代码细节按既定规则推进。

## 2026-10-19 实施进展

Task1—4代码及各服务回归已完成；Task5代码已接入并通过前端187项、构建及真实网关/隔离Nginx探针。浏览器自动验收因无法可靠确认当前URL被工具停止，尚未通过窄屏/键盘及真人业务验收。Task5第6步保留未勾选；先继续不依赖浏览器的整片审查、完整回归和部署准备，不声称已上线或全部验收通过。

## 2026-10-19 最终验收

用户确认生成/刷新及历史选择/窄屏/键盘正常；745项后端、187项前端/构建、独立审查、备份隔离恢复、部署健康与本人保存结果15项独立复算均通过。页面依据为用户人工验收，不冒称自动化浏览器验证。仅历史按钮区分度minor延期；FR-09等原待办保留。
