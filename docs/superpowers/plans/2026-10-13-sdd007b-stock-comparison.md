# SDD007B 股票对比指标、本人记录与页面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 让已登录用户对2—3只股票进行同口径历史比较，保存可复核结果并查看本人历史。

**Architecture:** MS02发现并读取已有固定比较证据；MS03校验证据、计算指标并保存本人快照；网关精确开放公开接口，Vue页面只展示。网络读取发生在数据库事务之外，已完成结果不依赖行情服务在线。

**Tech Stack:** 现有Spring Boot、Java BigDecimal、JdbcTemplate事务、MySQL/Flyway、Vue3/TypeScript、Element Plus、ECharts、JUnit、Vitest。沿用现有持久层，不将MyBatis-Plus规划冒称当前接入。

**Spec:** docs/superpowers/specs/2026-10-12-sdd007-stock-comparison-design.md（用户确认补充优先于早期笼统缺失规则）。

## 状态与执行方式

2026-10-13：计划V1.1已由用户批准并实施；任务1—6完成，用户确认正常，具体证据见docs/verification-sdd007b.md。保留此前由本会话直接实施的方式；不重新选择执行方式。A片已验收，不重复采集外部行情，不调用MaaS、不发送邮件。13项业务、五服务、两角色和任务书排期不变。

## Global Constraints

- 2—3只不同的受支持股票；默认最近三个月，可切换半年或一年。
- 同一供应商baostock、BACKWARD后复权收盘价；成交量为真实股数。
- 实际开始日和结束日必须对所有股票同时有有效可比价格。
- 缺失日期、停牌和数据陈旧分开说明；图表对缺口断线，不前向填充。
- 成交量缺失仅影响日均成交量；价格缺失或停牌按严格规则阻断风险指标。
- 每个指标输出status/reason/observationCount；金融数值后端计算，以字符串传输。
- 不因ADMIN身份自动读取别人记录；跨服务不直接读库、不建跨库外键。
- 刷新读取固定结果；重新对比是新操作；默认不调用付费AI。
- 所有业务响应遵循现有{code,message,data,traceId}；权限、Origin、CSRF不绕过。
- 每个新自建代码目录附README；选择性提交，不提交runtime或凭据。

## Review Focus

1. 可用比较证据比当前RAW头旧：展示真实截止日和历史提示，不冒称最新；任务1/5测试。
2. 创建超时后刷新或重复提交：同本人同请求恢复，不能重复保存或切换证据；任务3/5测试。
3. 一股缺量、另一股停牌：逐股逐指标判定，不静默剔除股票或把缺失显示成0；任务2/5测试。
4. 新增历史记录时翻页：固定分页上界，无重复、无跨用户读取；任务3/4测试。
5. 一年证据超过64KiB、连接中断或错误哈希：读取有界，失败保留用户选择，不保存半成品；任务1/3/5测试。

## 核对结论与数据选择

已实查MS03最后迁移为V7，MS02为V11；执行时先复核编号。现有BoundedAnalysisHttp上限实际为512KiB，且包内私有；不错误地宣称它只有64KiB，不直接跨包复用单股错误语义。独立比较客户端设每响应512KiB、总操作最多3份、每份370点/367日历、连接2秒、单次读取5秒、总依赖读取20秒，禁止重定向和无界缓冲。

当前已验收三股比较证据截止2026-10-09，后台RAW头已更新至2026-10-12。B片只发现已经发布的比较证据；不触发供应商查询。截止落后明确提示，可用证据不足则显示具体股票原因。

可用性查询先按每股已发布证据的最大to确定共同anchorDate=min(max(to))，不晚于北京时间当天；from=anchorDate.minusMonths(3/6)或minusYears(1)。再为每股筛选from<=请求from且to>=anchorDate、供应商和口径符合要求的证据，从合格集合按created_at DESC,evidence_id DESC确定一份。不能让新发布的短窗口证据遮蔽同一截止日仍有效的长窗口证据；若采用非最新发布的一份，options明确展示所选版本和采集时间，不标成最新版本。不存在完整覆盖时具体报告INSUFFICIENT_COVERAGE，不自动降低anchorDate或缩短一年。所有选择在一次MS02只读一致性事务中完成，固定2—3份ID。区间内逐日交易日历须一致，冲突受控失败。显示请求范围、共同有效实际范围、证据截止与采集时间；options由MS02汇总各股当前RAW头日期，作为“选源查询时状态”展示；POST不信任浏览器提交的新鲜度标签。保存结果仅记录可信来源截止、capturedAt及本次createdAt，不额外查询可变RAW头来改写固定来源。历史页展示“历史快照”，不伪装成实时状态；未核对交易日时不把自然日间隔解释为交易日延迟。

用户点击前，GET options固定并显示来源ID与范围；POST携带这些ID和日期，后台只读指定ID。选股或周期改变就重新查询options，但迟到响应不能覆盖新选择。无法获得完整来源时不允许开始，不静默删股。

## 稳定接口与值对象

公开接口：
~~~text
GET /api/v1/comparisons/options?ids=SH600030,SH600276&period=3M
POST /api/v1/comparisons
  {requestId:UUID,instrumentIds:string[2..3],from:ISO_DATE,to:ISO_DATE,evidenceIds:UUID[2..3]}
GET /api/v1/comparisons/operations/{requestId}
GET /api/v1/comparisons/{comparisonId}
GET /api/v1/comparisons?before=positive_sequence&upper=positive_sequence
~~~

options仅允许ids、period；period为3M/6M/1Y。POST只允许上述五字段，无重复JSON键；日期必须为to减3月/6月/1年之一且to不晚于当天，证据与股票一一对应。所有UUID严格格式。历史每页固定10条，首次返回upper与nextBefore，后页固定upper；无nextBefore时结束，不承诺不稳定total。序号游标按十进制字符串传输，不转JavaScript Number；空列表upper/nextBefore均null，首次不带两参数，后续同时提供且before<=upper。服务端按owner过滤，不依赖游标保密。HistoryItem只含id、日期、股票、实际区间及摘要指标；详情才加载曲线，完整来源不通过历史列表发送。

~~~java
// ComparisonRecords.java；JSON日期/时间ISO，金额/指标值字符串
record Input(UUID requestId,List<String> instrumentIds,LocalDate from,
             LocalDate to,List<UUID> evidenceIds) {}
record Metric(String status,String reason,int observationCount,String value,String unit) {}
// status AVAILABLE/UNAVAILABLE；可用reason=null；不可用value=null；不可用原因使用固定代码
record ChartPoint(LocalDate date,String normalizedClose) {}
// 缺口normalizedClose=null；connectNulls=false
record StockResult(String instrumentId,Metric intervalReturn,Metric annualizedVolatility,
                   Metric maxDrawdown,Metric meanVolume,List<ChartPoint> chart) {}
// Result保存comparisonId、createdAt、requestedRange、actualRange(nullable)、formulaVersion、
// provider/basis/annualizationDays=252、stocks、sourceRefs、sourceHash、来源采集时间与历史快照说明；完整sourceSnapshots仅在后端保存。
~~~

所有数值保留未舍入来源供复核。数值运算MathContext.DECIMAL128，平方根BigDecimal.sqrt(DECIMAL128)；最终百分比、归一化曲线6位小数，成交股数均值4位，HALF_UP。显示可进一步缩短但不得反向用于计算。sourceHash对按instrumentId排序的完整来源快照进行规范序列化SHA256；计算前验证A片calendarHash/contentHash（按其既有规范，不自创不兼容算法）。显示顺序按本次选择保存；请求指纹用股票与证据配对排序、日期和formulaVersion，不含requestId。

## Task 1: 发现并固定可用来源

**Files:**
- Modify: backend/stock-market-service/src/main/java/com/aistock/market/comparison/ComparisonEvidenceRepository.java
- Modify: 同目录ComparisonEvidenceService.java、ComparisonEvidenceController.java、README.md
- Create: 同目录ComparisonEvidenceOptions.java
- Create: backend/stock-prediction-service/src/main/java/com/aistock/prediction/comparison/ComparisonMarketClient.java、HttpComparisonMarketClient.java、ComparisonRecords.java、README.md
- Test: backend/stock-market-service/src/test/java/com/aistock/market/comparison/ComparisonEvidenceOptionsTest.java
- Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/comparison/ComparisonMarketClientTest.java

**Interfaces:** MS02受保护GET /internal/v1/market/comparison-options?ids=...&period=...返回固定ID、范围、每股状态及RAW头日期；已有GET evidence/{id}不改变。MS03 ComparisonMarketClient.options(ids,period)和evidence(UUID)；不接受浏览器提供内部地址。

- [x] 写RED：两股最大证据to分别10-09/10-08时anchor=10-08；同截止新短窗不遮蔽旧长窗；筛选合格版本后展示其实际采集时间；1Y覆盖不足指出股票；同日期日历冲突；未知/非ACTIVE/演示股票；候选ID更新不能改变已返回来源。
~~~java
assertEquals(LocalDate.of(2026,10,8), options.anchorDate());
assertEquals(LocalDate.of(2025,10,8), options.from());
assertEquals("INSUFFICIENT_COVERAGE", unavailable.reason());
~~~
- [x] RED：无内部令牌401、矩阵路径不能绕过、未知/重复query拒绝；70KiB正常、512KiB+1拒绝、慢流/重定向/坏哈希拒绝。
- [x] 运行定向JUnit，确认测试因尚未实现的契约失败。
- [x] 实现最新证据选择与固定范围；MS02只读自身库，SQL参数化；对每股明确不可用原因，整体不部分成功。客户端边接收边限长，解析前限制结构规模。
- [x] 运行定向JUnit和相关Mysql/HTTP测试，GREEN；选择性提交 feat: 提供股票对比可用来源查询。

## Task 2: 十进制指标计算与缺失语义

**Files:**
- Create: backend/stock-prediction-service/src/main/java/com/aistock/prediction/comparison/ComparisonCalculator.java、ComparisonEvidenceVerifier.java
- Modify: 同目录ComparisonRecords.java、README.md
- Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/comparison/ComparisonCalculatorTest.java、ComparisonEvidenceVerifierTest.java

**Interfaces:** ComparisonCalculator.calculate(Input,List<EvidenceSnapshot>)返回Calculation（请求/实际范围、股票指标、曲线、固定公式）；EvidenceSnapshot是A片Evidence等价只读类型，由ComparisonRecords声明；ComparisonEvidenceVerifier.verify(snapshot)验证内容与状态。

- [x] 写RED：三日价格100/110/99，成交量10/20/30。下列人工值为独立断言：
~~~java
assertEquals("-1.000000", result.intervalReturn().value());
assertEquals("10.000000", result.maxDrawdown().value());
assertEquals("20.0000", result.meanVolume().value());
// 日收益0.1/-0.1，样本方差0.02，年化波动率sqrt(5.04)*100
assertEquals("224.499443", result.annualizedVolatility().value());
~~~
- [x] RED：单调涨/跌、创新高后回撤、零方差、价格按10倍缩放、选股顺序变化；单共同点涨跌不可用；只有一个日收益时波动不可用但回撤可算。
- [x] RED：中间缺价/停牌阻断波动与回撤，端点收益仍可算、曲线null断线；缺量仅阻断meanVolume；价格缺失但量完整仍按真实成交量判断，不联动归零。无共同端点所有依赖共同区间的指标不可用。
- [x] RED：同股错误ID、伪造hash、非正价格、额外/重复/非交易日期、未知状态拒绝；有效数据不覆盖已有来源状态。
- [x] 运行定向测试，确认RED；实现共同有效端点和公式，不删除缺失行。volume只检查其所需字段；observationCount为真实使用的有效观测数，波动率计收益条数。
- [x] GREEN：DECIMAL128内部计算，最终舍入容差绝对不超过0.0000005个百分点（股数0.00005）；选择性提交 feat: 计算同口径股票对比指标。

## Task 3: 本人幂等创建、固定结果与历史分页

**Files:**
- Create: backend/stock-prediction-service/src/main/resources/db/migration/V8__stock_comparisons.sql（执行前复核编号）
- Create: backend/stock-prediction-service/src/main/java/com/aistock/prediction/comparison/ComparisonRepository.java、ComparisonService.java、ComparisonConfiguration.java
- Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/modelcatalog/ComparisonMysqlTest.java
- Modify: 该测试目录已有数据库清理fixture、迁移README及comparison/README.md

**Interfaces:** service.create(owner,Input)、operation(owner,requestId)、get(owner,id)、list(owner,before,upper)。create返回Operation(state,comparisonId,errorCode,expired)，不是伪装为立即成功；get返回固定Result。

表stock_comparison_operation：主键(owner_id,request_id)，fingerprint、input_json、RUNNING/SUCCEEDED/FAILED、lease_until、lease_generation、result_id、error_code、created_at。stock_comparison_result：comparison_id主键、sequence_id自增唯一、owner_id、result_json、source_snapshots_json、source_hash、created_at，索引(owner_id,sequence_id)。仅本库operation.result_id外键；不引用用户库/行情库。

- [x] RED：同本人同键同配对参数复用；同键改参数409；两人用相同requestId互不干扰；ADMIN查看别人结果/operation与不存在统一404。
- [x] RED：并发双创建只保存一份；失败不保存result；崩溃租约过期可显式以原请求恢复；旧工作者不得完成新租约。用lease generation UUID作fencing，完整状态转换需条件更新。
~~~sql
UPDATE stock_comparison_operation SET state='SUCCEEDED',result_id=?
 WHERE owner_id=? AND request_id=? AND state='RUNNING'
 AND lease_generation=? AND lease_until>?;
~~~
- [x] RED：行情头更新不改变固定ID/指标；市场断线仍能GET已有结果；历史插入新条目后固定upper继续翻页无重复。
- [x] 定向Mysql测试确认RED；实现短事务领取45秒租约，事务外最多20秒读来源、计算，再短事务原子插入结果并完成operation。超时回收只读取原input固定来源，不自动换版本；只有RUNNING租约过期允许原POST重新领取。FAILED原键返回已保存失败，新计算新键；GET operation始终只读，不回收租约。complete条件更新必须恰好影响1行，否则整个完成事务（含result插入）回滚；失败写入也必须检查lease_generation，旧工作者不得覆盖新结果。该确定性任务不占MaaS全局slot。
- [x] GREEN：数据快照/结果各最多2MiB存储JSON，超限受控失败；失败记录不暴露内部原始错误。result_json只保存轻量结果和sourceRefs，不重复内嵌source_snapshots_json；完整来源后端独立保留供离线复核。历史查询投影摘要列/字段，禁止每页加载10份全量快照。选择性提交 feat: 保存本人股票对比与恢复记录。

## Task 4: 公共API与网关安全边界

**Files:**
- Create: backend/stock-prediction-service/src/main/java/com/aistock/prediction/comparison/ComparisonController.java
- Modify: backend/stock-prediction-service/src/main/java/com/aistock/prediction/security/PredictionSecurityConfiguration.java（精确增加/comparisons及/comparisons/**认证路径）；ModelRequestFilter只处理internal，已核对无需修改
- Create: backend/stock-gateway/src/main/java/com/aistock/gateway/ComparisonApiProxyController.java、ComparisonPublicClient.java、ComparisonClientConfiguration.java
- Modify: backend/stock-gateway/src/main/java/com/aistock/gateway/ProxyHeaderPolicy.java
- Test: backend/stock-prediction-service/src/test/java/com/aistock/prediction/modelcatalog/ComparisonHttpTest.java
- Test: backend/stock-gateway/src/test/java/com/aistock/gateway/ComparisonProxyTest.java

**Interfaces:** 使用上文五个公开路由；新增ComparisonPublicClient带query传递，独立connectTimeout=2秒/readTimeout=30秒，禁自动重试、禁重定向，不沿用预测客户端185秒等待；服务身份从现有Actor解析，禁止接受owner字段。

- [x] RED：未登录401、写入缺CSRF/错误Origin403、账号服务故障503；普通用户与ADMIN均只访问本人；比较专用Feign超时为30秒且无自动POST重试；不改现有预测/分析超时。
- [x] RED：1/4股、重复股票、重复evidenceID、非法UUID/date、未知/重复字段/query、2KiB+1请求拒绝；ids与证据映射冲突422；同键改参数409。
- [x] RED：网关只透传已声明路由与query，未知query必须拒绝而非悄悄丢弃；矩阵/编码斜杠/额外路径不能访问internal。详情/operation未知404，依赖不可用503，超大请求413。
- [x] 定向测试RED后实现；HTTP创建成功首次201、已有成功结果恢复200、进行中202；创建依赖失败503并保存FAILED回执，GET operation以200返回明确Operation和错误码；GET只读不采集、不创建。
- [x] GREEN并选择性提交 feat: 开放受保护的股票对比接口。

## Task 5: 对比页面、入口与本人历史

**Files:**
- Create: frontend/src/api/comparisons.ts
- Create: frontend/src/pages/comparison/ComparisonPage.vue、ComparisonResult.vue、ComparisonHistory.vue、comparisonState.ts、chartOptions.ts、README.md
- Test: 同目录ComparisonPage.test.ts、ComparisonResult.test.ts、ComparisonHistory.test.ts、comparisonState.test.ts、chartOptions.test.ts
- Modify: frontend/src/router/index.ts、frontend/src/App.vue及相关测试
- Modify: frontend/src/pages/watchlist/WatchlistPage.vue、frontend/src/pages/market/MarketAnalysisPage.vue及其测试
- Modify: frontend/src/api/README.md、auth.ts、auth.test.ts（将/comparisons纳入AUTH_REQUIRED事件路径）

**Interfaces:** comparisons.ts严格定义Options/Input/Operation/Result/History类型并通过现有request()请求；路由/comparisons及/comparisons?comparisonId=UUID，单股入口仅预选，不自动开始。

- [x] RED：默认3M、2—3股校验、搜索只允许当前支持列表；自选入口预选至多3股但明确显示，不擅自增加第四股。选股/周期变更重新加载options，旧响应丢弃。
- [x] RED：开始按钮loading禁连点，网络超时保留选择与requestId并显示查询状态入口；发送前将完整原Input按owner命名空间写入sessionStorage，刷新后先GET operation，不自动POST。404表示尚无已保存操作，可由用户明确重试原POST；RUNNING未过期只能查询，RUNNING过期允许明确恢复原POST；FAILED显示失败且新计算生成新键。存储不可用时发送前提示，不能丢掉恢复条件后继续发送。完成后URL携comparisonId，刷新GET固定结果。
- [x] RED：改变选择后旧结果保留但标注“上次结果”，不能把新筛选标题贴到旧指标上；readonly历史加载错误保留筛选与已显示结果。
- [x] RED：五项内容（曲线+四类指标）以及单位、252约定、后复权口径、实际/请求日期、数据截止、采集时间与历史快照状态均可见；缺失不是0，逐股原因可见，不显示买卖排名。
~~~ts
expect(chartOptions(series).series[0].connectNulls).toBe(false)
expect(wrapper.text()).toContain('数据截至 2026-10-09')
expect(wrapper.text()).toContain('成交量缺失')
~~~
- [x] RED：历史10条分页固定upper，大于2^53的游标原样字符串传递，列表不包含完整sourceSnapshots；打开本人快照，/comparisons返回401触发既有登录流程；注销清除本用户临时operation键，不能下账号复用。sessionStorage损坏/禁用、刷新恢复404/RUNNING/过期/FAILED均分别测试。
- [x] 测试RED后实现Vue script setup，沿用白底卡片、蓝色交互、可键盘操作，移动端结果卡片换行，主导航新增入口不溢出。图表仅把后端字符串转展示浮点，不重新计算收益；不提供尚未接入的可点击AI按钮。
- [x] GREEN：pnpm test --run、pnpm build；选择性提交 feat: 提供股票对比与本人历史页面。

## Task 6: 集成验收、独立审核与部署

**Files:**
- Create: docs/verification-sdd007b.md、docs/evidence/sdd007b-comparison-checks.json、docs/sdd007b-execution-ledger.md
- Modify: docs/implementation-status.md、docs/pending-items.md、docs/superpowers/specs/2026-10-12-sdd007-stock-comparison-design.md
- Create: scripts/check-comparison-snapshot.ps1、对应scripts/README说明

- [x] 运行backend隔离clean verify（scripts/check-auth-task1.ps1，使用现有测试库配置、cli-build/auth-integration）；前端完整test/build；不并发Maven，不重复未改动算法实验。
- [x] 一次独立最终代码审查；修复具体问题后重跑受影响测试。审查不能代替真实接口/页面验收；只记录实际发生的审查次数。
- [x] 保存当前市场/预测/网关/前端镜像摘要和精确回退命令，升级对应服务；数据库新增表使用前向修复，不删除旧表。MySQL迁移编号冲突先修计划再部署。
- [x] 使用已有三份证据验证3M/6M/1Y，无外部行情/AI调用；以独立Python Decimal离线复算对照服务结果，报告每项误差与输入hash。
- [x] 经正常已登录会话创建、刷新、查看历史，核对两角色本人隔离；不读取密码、不伪造登录token。源头更新场景用隔离测试，不为验收主动触发真实行情更新。
- [x] 上线前后核对原用户、自选、模型发布、预测及分析/问答、预警、固定比较证据数量与关键hash；不因业务部署开启邮件投递。
- [x] 实际浏览器工具仍不可用时记录限制，完成接口与自动化组件验收，向用户请求本机页面核对；不得写“视觉验收通过”。
- [x] 选择性提交 docs: 记录股票对比指标与页面验收；没有Git远程不声称已推送。

## 计划自审

- 覆盖：来源/时间/同口径→任务1；公式/独立缺失→任务2；固定依据/幂等/本人分页→任务3；会话/权限/白名单→任务4；入口/加载/历史/断线/数字一致→任务5；独立复核与部署保全→任务6。
- A片内容hash算法必须兼容，不能将后端首次生成的新hash当作来源验证；日期与证据固定在POST前，避免同请求跨午夜改变范围。
- 未引入新的业务，不改现有RAW公开接口，不覆盖预测或单股AI协议；AI比较属于C片，当前不宣称已完成。
- 规划文件路径均以E:/aiStock/项目代码/stock-system为根；每个新增代码目录配README。新增类/值对象在各任务明确定义。
- 任务顺序1→2→3→4→5→6；每任务RED/GREEN和独立功能提交。下次执行先复核现有代码与迁移末版本。


## 第二次审核结论与补充约束（2026-10-13）

整体分片合理：先确定性计算和可复核记录，再C片AI解释，避免同时改动模型和业务。此次只修订计划，不修改产品代码，不开展外部调用。

1. 数据发现修正为“先确定共同截止，再选覆盖完整范围的版本”，防止最新短窗误伤一年对比；并发发布不能拼出不一致的选择。
2. 来源完整快照仅后端存一份；GET详情返回曲线、指标和来源引用，历史页只发摘要，减少传输和反序列化开销。
3. 公共POST实际五字段，公开路由实际五个，已纠正计数；选股与evidenceIds使用下标配对，计算指纹排序前先建立配对，不能各自排序后配错。
4. 比较客户端独立30秒超时；旧预测客户端实际185秒，不能直接复用其等待策略。依赖20秒预算与45秒租约有明确余量，不为了本片新增线程池、消息队列或异步后台执行器。202只表示另一请求已在执行，不意味着已经建立后台任务。
5. lease_generation必须同时保护成功和失败更新；失去租约时回滚新增结果，避免孤立结果出现在本人历史。
6. 补齐auth.ts比较路径、sessionStorage恢复协议以及超大游标精度测试；保留现有账号generation隔离。
7. 不给单股指标按全组价格完整性统一置不可用；共同端点统一后，各股检查自己的价格/成交量。单股中间缺价不应阻断另一股完整风险指标，缺量不应影响任何价格指标。图表共同端点之外不画成基准100的可比区间。
8. 暂不在本片增加新定时采集任务；比较历史自动持续更新尚未由A片证明，需列入pending-items，并在用户页面明确来源截至时间。已完成的RAW自动更新不等于比较后复权证据已自动更新。不能以固定历史演示宣称持续最新比较已完成。
9. sourceHash/contentHash验证用于发现内容变化与错配；内部鉴权提供传输访问边界，哈希本身不能证明供应商事实绝对正确。A片contentHash不包含所有元数据，MS03必须额外校验ID、供应商、basis、capturedAt格式及时间范围；不得声称哈希覆盖了所有字段。
10. 补充并发保护：每个MS03实例确定性比较同时执行上限4（配置项aistock.comparison.max-concurrent，默认4），非阻塞准入，满额429 COMPARISON_BUSY且不新建操作；已保存成功/失败回执查询先于准入，不因繁忙阻断读取。该上限不是集群全局配额；不引入Redis分布式锁。

附加测试归属：任务1加入新短窗/一致性读取；任务2加入“一股缺价另一股风险仍可算”和未哈希元数据校验；任务3加入失租约失败写入/事务回滚/并发429不建操作/已完成不占准入；任务4加入专用超时配置与429透传；任务5加入401事件/恢复/超大游标/轻量列表；任务6明确记录持续比较证据更新仍待完成。

## 实施差异和验证范围
执行裁定见docs/sdd007b-execution-ledger.md：固定checkout、Windows账本、独立有界客户端、JsonNode快照、摘要列、独立曲线组件、upper-only首页重读、逐日dataIssues。任务6本人创建/刷新/历史由用户真实操作确认；两角色隔离为HTTP/数据库测试，不冒称两个浏览器账号交叉验收。窄屏视觉矩阵仍为待办，实际回退未演练。没有Git远程，选择性本地提交，不推送。
