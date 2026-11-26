> 最终状态：2026-10-23，本片已实现、审查修复、部署并验收，见docs/verification-sdd010a.md；下方设计阶段状态保留为历史，FR-11后续写业务未关闭。

# SDD-010A 管理员行情查询 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. 沿用用户既定当前对话逐项实施方式，末尾一次fresh reviewer；用户已委托代理审核范围，本轮审核完成；实施时沿用当前对话方式，不再重复请求相同范围的形式确认。

**Goal:** 为FR-11交付只读行情覆盖/来源、历史版本/回执、固定版本质量诊断管理员页面。

**Architecture:** MS-02在本库RR只读事务中查询三类资源，网关双重ADMIN校验和精确GET代理，Vue分区按明确操作加载。诊断为固定版本有限日期纯函数，不触发补取或过期对账；不跨库。

**Tech Stack:** Java17/Spring Boot3.2.5/JdbcTemplate/MySQL8/JUnit5，Vue3/TypeScript/Pinia/Vitest，沿用现有依赖。

**Spec:** E:/aiStock/项目代码/stock-system/docs/superpowers/specs/2026-10-22-sdd010a-admin-market-readonly-design.md

状态：2026-10-22用户委托代理完成范围审核及修订，六任务计划可进入实施；代码核对基线4a57b92，本轮未实现/测试/部署业务功能。预计4—6个工作日，取决于审核/环境与审查修复，不修改现用任务书日期。

## Global Constraints

- 13业务、五业务服务、ADMIN/USER不变，FR11仍部分完成；SDD009B真实取消待办和其余业务保留。
- 只用本库已有表，全部GET、统一{code,message,data,traceId}和no-store；无Schema变更，不调用外部数据/模型/邮件/算法，不改变worker开关。
- 固定版本诊断默认上海含今天90日、最多366日、1990-01-01至今天、from/to成对；列表/版本历史没有诊断日期筛选。
- count和revision为十进制字符串、BigDecimal数值；缺证据不填0结论、不造满分/准确率；异常日期样本上限20。
- “可用头/最近失败”“版本/接入回执/刷新回执/调度任务”“疑似缺失/确认异常/未知日历”分别展示。
- 保持E:/aiStock/项目代码/stock-system及codex/sdd004-market-refresh，选择性提交，无remote不push；不创建新worktree。每个新源码目录附README。
- 规格已列明DTO与所有字段，下面所有Java类型不使用未定义别名。U=stock-common、M=stock-market-service、G=stock-gateway；路径均backend/对应模块/src/main/java/com/aistock/{common,market,gateway}，测试同模块src/test/java。

## Review Focus

1. 有AVAILABLE头但最近取数失败：状态并列而非二选一；Task3/5固定fixture与界面断言。
2. 非交易日/已记录停牌/无行停牌未知/部分日历：只按固定版本证据诊断；Task2四类输入明确count和state。
3. 并发新增版本与同发布时间：keyset不重复/漏掉原下一页，cursor绑定股票和来源，Task1/3测试。
4. 合法JSON0身份、缺失/null/浮点/字符串身份、重复Cookie/查询参数不能被默认转换放行；Task3/4真实JSON→权限测试。
5. 快速切股票/版本/日期、503、撤权、离页时旧结果不能恢复到新区域；Task5延迟Promise与身份epoch测试。

## 文件和接口归属

公共adminmarket/只定义参数及窗口，不持有DB/用户/HTTP；行情adminquery/包含DTO、纯诊断、游标、本库只读仓库、控制器及范围专用权限；网关security/提取无DB的管理员权威校验，adminmarket/负责白名单代理。前端pages/admin/data/仅管理员查询，不将业务放在通用个人中心。

### Task 1：严格查询契约、DTO与版本游标

**Files:** Create U/adminmarket/{AdminMarketQuery.java,README.md}；Create M/adminquery/{AdminMarketRecords.java,VersionCursor.java,README.md}；Test U/adminmarket/AdminMarketQueryTest.java，M/adminquery/VersionCursorTest.java及README。

**Interfaces:** AdminMarketQuery.listing(MultiValueMap):ListFilter(q,provider,headState,page,size)；history(MultiValueMap):HistoryFilter(provider,cursor,size)；detail(MultiValueMap,Clock):DetailFilter(from,to)；readPath(String):boolean精确匹配规格三个GET资源路径（不检查HTTP方法），用于网关/市场filter复用，controller另检查GET。AdminMarketRecords定义规格4/5全部嵌套records（HeadPage/HeadRow、VersionPage/VersionSummary/IngestionSummary/RefreshSummary、VersionDetail/VersionMetadata、Window、Diagnostics/DateFinding、DiagnosticBar/CalendarDay/VersionBounds）。VersionCursor.encode(Cursor(instrumentId,provider,lastPublishedAt,lastVersionId)):String及decode(String,String instrumentId,String provider):Cursor，v固定1，不作权限凭证。Window(from,to,timeZone)固定Asia/Shanghai；VersionMetadata平铺VersionSummary字段并添加contentHash/sourcePayloadHash/headRevision。

- [ ] **Step1 RED：** 固定Clock2026-10-22T04:00Z，默认诊断from=2026-07-25/to=2026-10-22（90日）；同日有效、366日有效、367日/未来/1990前/半对/重复/未知参数400。列表size0/51、page0/10001、浮点和空白数字400；q空串null，64字符可用、65字符400。history拒绝page/from。游标同毫秒两UUID稳定、错股票/来源/版本/畸形Base64/未知或重复JSON字段/512以上400。
- [ ] **Step2 运行：** backend执行mvn -B -Pcli-build -pl stock-common,stock-market-service -am test '-Dtest=AdminMarketQueryTest,VersionCursorTest' '-Dsurefire.failIfNoSpecifiedTests=false'；Expected缺类型/方法或确定行为失败。PowerShell所有含逗号参数须整体引号。
- [ ] **Step3 GREEN：** 实现参数白名单与单值校验、UTC游标和纯records，public错误MARKET_ADMIN_QUERY_INVALID；无HMAC/新密钥、无DB写入。DTO JSON字段与规格逐一一致，版本详情类型VersionMetadata包含VersionSummary字段和两hash/headRevision。
- [ ] **Step4 验证：** 相同命令PASS，默认90日实算校验；Java记录的所有计数字段为String，时间字段Instant/LocalDate。
- [ ] **Step5 提交：** 仅本任务文件，commit `feat: 定义管理员行情查询契约`。

### Task 2：有限日期覆盖与质量诊断

**Files:** Create M/adminquery/CoverageDiagnostics.java；Test CoverageDiagnosticsTest.java。

**Interfaces:** calculate(DetailFilter,VersionBounds,List<DiagnosticBar>,List<CalendarDay>):Diagnostics，使用Task1 records。返回全部规格字段；DateFinding计数精确、sampleDates升序20、truncated由实际数量计算。

- [ ] **Step1 RED：** 固定诊断fixture时钟2026-10-24，2026-10-19—23五个开市日，四行包含1个trade_status=0，则knownOpen5/bar4/traded3/suspended1、疑似缺失1且state=NEEDS_REVIEW，不能记停牌行为缺失或宣告已确认错误；将23设休市则缺失0，增加休市行则barsOnClosed1。缺一日日历unknown1且无其他问题时INCOMPLETE_EVIDENCE；超出覆盖的日期只计outsideCoverageDays，无交集OUTSIDE_COVERAGE。负价格、high低于close、交易行零量/缺额、非法tradeStatus分别invalid；同一日多问题只计一个日期。不修改输入集合。
- [ ] **Step2 运行：** mvn -B -Pcli-build -pl stock-market-service -am test '-Dtest=CoverageDiagnosticsTest' '-Dsurefire.failIfNoSpecifiedTests=false'；Expected不存在算法或断言失败。
- [ ] **Step3 GREEN：** 按最多366个LocalDate比较日期集合；OHLC用BigDecimal，计数String；按OUTSIDE_COVERAGE、ISSUES、NEEDS_REVIEW、INCOMPLETE_EVIDENCE、NO_RECORDED_ISSUES优先级输出；已确认异常和待核对分别展示。字段坏到不能解释的元数据失败，不悄悄变成空集正常。
- [ ] **Step4 验证：** 同命令PASS；25个疑似日期count25、样本20、truncated=true；未知日历不是开市/休市；NO_RECORDED_ISSUES不输出“完全准确”。
- [ ] **Step5 提交：** commit `feat: 实现固定行情版本的覆盖诊断`。

### Task 3：行情本库查询、安全接口与直接访问保护

**Files:** Create M/adminquery/{AdminMarketReadRepository.java,AdminMarketReadController.java,AdminMarketDataAccessFilter.java,AdminMarketErrors.java}；Modify M/security/MarketSecurityConfiguration.java；Test AdminMarketReadMysqlTest、AdminMarketReadHttpTest、AdminMarketReadWiringTest；复用market/support/MarketDatabaseFixture，不改生产表约束制造坏行。

**Interfaces:** heads(ListFilter,Instant):HeadPage；versions(String,HistoryFilter,Instant):VersionPage；detail(String,UUID,DetailFilter,Instant):VersionDetail。控制器实现规格三个GET、注入现有marketClock，调用本片仓库；缺非demo资源404，DB故障503。filter使用SessionIdentityClient严格解析，server ADMIN属性供Controller复核。

- [ ] **Step1 RED：** 非demo三股，一股双来源、第二无头、第三头version空；list默认四行、指定来源仍保留无该来源头股票。AVAILABLE+SOURCE_UNAVAILABLE同时保留；demo不出现、q通配符按字面、排序/页边界稳定；伪关联他股票/来源/口径返回REFERENCE_ERROR且不泄其元数据。
- [ ] **Step2 RED：** 历史版本30条含相同published_at，首20后插入更新版本，再取nextCursor得到原余10且不重复；wrong-owner版本404；只有拒绝回执没有版本时历史空但rejected可见。回执11条取10并truncated；RUNNING租约已过也不写FAILED；tushare不显示baostock刷新回执。固定版本详情使用本版本日历而非独立日历当前头，读取前后六业务表/回执/头revision不变、collector0调用。
- [ ] **Step3 运行：** 已核验隔离DB环境执行mvn -B -Pauth-integration,cli-build -pl stock-market-service -am test '-Dtest=AdminMarketReadMysqlTest,AdminMarketReadHttpTest,AdminMarketReadWiringTest' '-Dsurefire.failIfNoSpecifiedTests=false'；Expected缺实现失败，不得连接生产库或使用生产口令。
- [ ] **Step4 GREEN：** 三个请求各自RR只读5秒，SQL显式列+预编译参数，不使用会写入的旧lookup。错误码白名单UNKNOWN_ERROR；缺失或无效价格不得用0填充。list按规格JOIN/筛选、history keyset、detail主键范围读取；必要的多个SQL在一个读事务，不做N+1版本诊断。
- [ ] **Step5 验证权限：** 匿名401、USER403、ADMIN200/no-store；重复Cookie/多头、X-Role伪造、畸形JSON credentialVersion拒绝，0合法；真正装配的FilterRegistrationBean保护三路径及子树未知路由，预检无身份/仓库调用。只放GET/OPTIONS，不扩大原instruments/statistics路径。来源不可用/异常映射安全503，无原始SQL或Token。
- [ ] **Step6 验证与提交：** 三测试及完整market服务回归PASS，记录实际数，不复用193作为新数；commit `feat: 提供管理员只读行情与质量查询`。

### Task 4：网关精确代理与共享管理员读取校验

**Files:** Create G/security/{AdminReadAccess.java,README.md}；Modify G/statistics/StatisticsAdminAccess.java为保留原构造/requireAdmin方法的委托；Create G/adminmarket/{AdminMarketDataProxyController.java,AdminMarketDataCorsConfiguration.java,README.md}；Modify ProxyHeaderPolicy.java、UserApiProxyController.java；Test AdminMarketDataProxyTest、AdminMarketDiscoveryWiringTest、AdminReadAccessTest。沿用StatisticsIdentityClient和MarketPublicClient，不另造传输。

**Interfaces:** AdminReadAccess(StatisticsIdentityClient,String token).requireAdmin(HttpServletRequest):void，bean由AdminMarketDataCorsConfiguration独立@Bean提供；原StatisticsAdminAccess构造中委托同实现，旧测试调用方式不变。ProxyController使用共享AdminMarketQuery校验MultiValueMap、MarketPublicClient.get(String,Map,Map)，body上限256KiB，统一错误和no-store。精确资源路径白名单复用Task1的AdminMarketQuery.readPath(String)，方法仅GET。

- [ ] **Step1 RED：** 三GET正确转MS02并保留合法参数；重复参数不能提前折叠；unknown/body/POST/internal路径拒绝；外部身份头剥除；USER网关403、匿名401、身份503独立于market503。保留SDD009C实际JSON缺失/null/浮点/字符串/负数失败关闭及合法0，403文案改通用不改code。
- [ ] **Step2 运行：** mvn -B -Pcli-build -pl stock-gateway -am test '-Dtest=AdminMarketDataProxyTest,AdminMarketDiscoveryWiringTest,AdminReadAccessTest' '-Dsurefire.failIfNoSpecifiedTests=false'；Expected缺类型/精确路由失败。
- [ ] **Step3 GREEN：** 使用原服务发现，UserApiProxyController显式拒绝market-data子树，新增精确Cors且注册顺序明确；剥离X-User/X-Role；失败安全映射400/401/403/404/503，不透传内部错误正文。controller只读，不暴露internal/v1或管理员写端点。固定HTTP组ADMIN，不引入股票/游标标签。
- [ ] **Step4 验证：** 无显式URL、发现实例为非默认端口的真实Feign调用PASS，预检零身份/业务调用；market不可用不影响原统计GET和任务列表，原统计/任务安全与网关完整回归PASS。256KiB边界/坏JSON有断言，不宣称共享客户端具有读取前内存流量上限。
- [ ] **Step5 提交：** commit `feat: 接入管理员行情查询安全网关`。

### Task 5：管理员行情页面与分区状态隔离

**Files:** Create frontend/src/api/adminMarketData.ts及test；Create frontend/src/pages/admin/data/{DataPage.vue,MarketHeadList.vue,MarketVersionPanel.vue,MarketVersionDetail.vue,marketDataPresentation.ts,README.md}及对应tests；Modify frontend/src/router/index.ts、frontend/src/App.vue、api/auth.ts（新受保护路径401事件），新增router/adminMarketData.test.ts。

**Interfaces:** api getHeads(ListFilter):Promise<HeadPage>；getVersions(instrumentId,HistoryFilter):Promise<VersionPage>；getVersionDetail(instrumentId,versionId,DetailFilter):Promise<VersionDetail>，DTO逐一按Task1定义；只走现有request GET/no-store/身份generation，不使用自动POST或私有Cookie。运行时检查definitionVersion/所属股票版本/窗口/计数字符串/日期样本和列表界限，畸形响应抛INVALID_RESPONSE，不转换0。

- [ ] **Step1 RED：** /admin/data ADMIN可见/USER拒绝。初始化只有1个listGET；点击查询才应用筛选，点击查看版本才查history，点击具体版本才取detail。AVAILABLE与最近失败同时可读，无头/无版本不伪造状态；拒绝回执独立于版本；当前/历史标识、未知日历/疑似缺失/停牌说明正确。
- [ ] **Step2 RED：** 应用新q/provider/headState后旧版本/回执/详情全清；草稿编辑或仅列表翻页不误清固定版本详情，详情明确自身股票与查询时间；快速A股→B股、版本A→B、日期切换后的迟到响应被拒绝；503只标本区，同条件保留旧时间/提示，改变条件清空旧区；401/403/退出/降权/离页不恢复旧结果。history只使用cursor并保存已看页，不把重启列表新增版本追加到旧页序列；忙时同操作不能重复发请求，大数字精确保留。
- [ ] **Step3 运行：** frontend pnpm test src/api/adminMarketData.test.ts src/pages/admin/data src/router/adminMarketData.test.ts；确认实际Vitest筛选行为，不错报测试范围。
- [ ] **Step4 GREEN：** 原蓝白布局，覆盖列表与可收起详情，日期仅作用版本诊断。原始机器码用安全中文+必要来源说明；长UUID/hash放可展开只读区域；明确“同版本随附日历”“未发现问题不代表数据完全准确”。无价格编辑/补取假按钮，无自动轮询。native按钮/输入有label、loading/status/alert、Tab/Enter、390px表格横滚、1280px正常；离页卸载并处理epoch。
- [ ] **Step5 验证与提交：** 新测试及前端完整test、vue-tsc、Vite PASS，原>500KiB警告单独保留；不新增图表库；commit `feat: 增加管理员行情数据查询页面`。

### Task 6：完整回归、一次独立审查和只读验收

**Files:** Create docs/verification-sdd010a.md及docs/evidence/sdd010a-verification.json；Update实施状态/待办/13业务核对/规格计划索引；临时日志、私密备份置runtime/sdd010a不提交。

- [ ] **Step1 环境核对：** 测试容器label=aistock.sdd002.auth-test、tmpfs /var/lib/mysql、127.0.0.1:13316、无生产卷；五个AUTH/MAIL/MARKET/SCHEDULER/PREDICTION_TEST_DB_URL指向各自stock_*_test，秘密隐藏读取runtime/auth-test-db-secret.txt。缺环境不运行mysql测试，不修改生产.env。
- [ ] **Step2 全回归：** backend mvn -B -Pauth-integration,cli-build clean verify；frontend pnpm test及build内vue-tsc。保存逐模块实际失败/错误/跳过和测试数，旧982/277仅基线历史。对相关SQL做EXPLAIN，缺索引/规模限制记录，不假称压测通过。
- [ ] **Step3 独立审查：** 当前片全差异一次fresh reviewer；核对Review Focus五类，重要问题先RED后GREEN一轮修复及受影响套件，minor按真实影响记录，不循环派审。无审查结论前不部署。
- [ ] **Step4 保护与部署：** 先完整备份marketV13与旧market/gateway/frontend镜像、SHA256；核对desktop-linux原持久卷、七应用、原开关和.env哈希；compose config --quiet后只重建/更新实际变化三组件，不迁移Schema、不停止其他服务或清卷，原计划继续原状态。失败先回旧镜像，不直接灌备份；未演练回退明确注明。
- [ ] **Step5 真实只读验收：** 七应用UP、五服务单健康Nacos、HTML/资源哈希正确、V13/开关不变；用户原ADMIN会话，禁止复制Cookie/历史密码。首轮以列表/历史/详情各约2次为参考，必要的本地只读复核不设人工授权次数锁，记录实际次数；独立读取选定版本原始非敏感列并计算覆盖/缺口对照，不用同聚合SQL自证；读前后版本/头/日线/回执/revision不变。无生产缺陷样本不造记录，按fixture覆盖。
- [ ] **Step6 权限与页面：** 匿名网关/直连401/no-store；用户自行用普通角色在另一浏览器检查网关和直连代表路由拒绝，自动化证据和人工报告分开。390/1280、Tab/Enter、只读刷新和固定版本/局部失败/账号切换检查通过；原自动worker活动与管理员查询分开说明：若自然写入先核对原任务事件，在稳定窗口复查，不为验收停worker，不宣称所有后台供应商调用为0。
- [ ] **Step7 记录与提交：** 列明本片已实现/测试/部署/人工验收；FR11补取/标记不可用及其他待办不关闭，CodeArts使用不假归属；commit `test: 记录管理员行情查询验收结果`，保持原分支，不push。本片六任务验收后才标完成。

## 自审与实施门

规格1/2范围由Global和Task6约束，规格3页面由Task5，规格4契约由Task1/3/4/5，规格5诊断由Task2/3，规格6数据一致性由Task3/6，规格7权限由Task3/4，规格8验收由Task6。Review Focus各已补到对应具体测试。

已核对本库字段、状态和副作用：不会误调用RefreshRepository.lookup；没有按不存在的“拒绝版本”表设计；provider筛选在JOIN、历史cursor并发、历史版本归属和诊断交集均明确。日期默认90日的示例需程序实算复核，不能只凭文字记忆。

本文件是经用户委托代理审核的计划，不是已完成代码；后续沿用当前对话逐项实施，不新建任务对话，不重复请求同一范围批准。重要业务行为更改或外部付费另行确认。

### 本轮自审修订（未运行功能测试）

已实算2026-10-22向前89日为2026-07-25，90日含首尾；明确Window字段和定义版本名；把路径白名单补入Task1产出供Task4复用；补游标重复JSON字段拒绝；明确诊断fixture时钟避免测试示例跨未来日；固定安全错误码白名单、成功ADMIN查询预算及原worker自然写入的复查方法。没有另派agent，也没有把静态文档检查记作功能测试。

### 用户委托范围审核的同步修订

范围审核于2026-10-22完成，仅保留已有六任务，不增功能。Task2增加NEEDS_REVIEW状态与优先级，Task5增加应用筛选清理从属区域/草稿与翻页保留事实的反例测试；Task6取消本地成功GET的硬授权上限，改为首轮约6次参考并记录实际读数。只查已登记非demo股票，不扩为无主接入日志审计；列表不把来源行数当去重股票数。所有数量/时间/响应大小阈值为当前版本集中策略，业务不变量与可调参数分开，保持后续扩展能力。

范围审核结论是“可以实施”，不把文档审核当功能测试/部署通过。下一实际动作是Task1的契约与失败测试，不重新让用户审核同一范围。
