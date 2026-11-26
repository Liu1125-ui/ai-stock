> 最终状态：2026-10-23，本片已实现、审查修复、部署并验收，见docs/verification-sdd010a.md；下方设计阶段状态保留为历史，FR-11后续写业务未关闭。

# SDD-010A 管理员行情查询详细设计

日期：2026-10-22。状态：用户明确委托代理审核范围，2026-10-22范围审核及修订完成，可沿既定流程实施；本轮仍只有设计与计划，未编写业务代码。代码核对基线4a57b92。沿用FR-11、13项业务、五业务服务、ADMIN/USER两角色及现用任务书顺序。

## 1. 目标和边界

管理员能够回答：某只股票目前有哪些已保存来源版本，覆盖到哪一天；最近更新为什么失败；指定版本在所查日期内有哪些已知数据问题或证据缺口。

本片为行情管理的查询阶段：覆盖与来源列表、来源版本历史、固定版本详情、保存回执及有限日期诊断。全部GET，只读现有market数据库。沿用Vue3/TypeScript/Pinia及Spring Boot/JdbcTemplate；不引入新的基础设施、第三方依赖、数据源、数据库或第六个业务服务。

后续SDD010B单独设计手动补取、重新获取、标记不可用、调度任务与操作记录。本片不提供价格编辑、数据修复、强制发布、版本删除/切换或管理员写操作；不改变自动更新、预测、比较、预警和旧快照。FR-11整体仍部分完成。

## 2. 已有事实与可行性

以下代码是设计依据，不把脚本当作管理员页面：

- stock-market-service的version/MarketVersionRepository以不可改写版本发布行情，再移动instrument_data_head；失败保留旧头，可记录last_error_code。当前AVAILABLE和最近SOURCE_UNAVAILABLE可以同时成立。
- ingestion/CandidateValidator检查OHLC、数值精度、交易状态、日期顺序等；已保存quality通常为ROW_VALIDATED或CALENDAR_UNKNOWN。标签不等于独立第三方质量认证。
- refresh/RefreshRepository的market_refresh_receipt保存RUNNING/AVAILABLE/LEGITIMATE_EMPTY/FAILED；lookup可能完成过期核对并写库，**本片不能调用此方法充当查询**，应直接只读查询选定列。
- market_ingestion_operation保存PENDING/AVAILABLE/REJECTED及安全错误码；拒绝候选不一定生成market_data_version。不能把“无被拒版本”解释成“没有失败”。
- instrument、instrument_data_head、market_data_version、market_daily_bar、market_calendar_day、上述两个回执表均属于MS-02。本片不读取调度库或预测库、不跨库联表；调度任务仍到既有任务页查看。
- 正式更新链当前为Baostock；Tushare是已有适配/研究能力，不因列表出现来源记录就宣称有权限或自动切换已验收。demo_only样本排除。

## 3. 管理员页面 /admin/data

沿用现有后台导航、管理员路由保护和蓝白样式。页面分为三个区域，不把全部股票的全部诊断同时加载。

1. 覆盖与来源列表：按股票代码/名称搜索、来源和当前头状态筛选；每行表示股票+已保存来源+口径，无对应来源头的股票保留缺失行。展示名称及是否核验、来源、RAW口径、当前版本状态、覆盖起止、检查时间、取数/发布时间、质量标签与最近错误。当前版本可用和更新失败提示分开。
2. 股票版本与回执：点击“查看版本”后分页列出该股票历史版本，明确“当前版本”是查询时快照；同时展示各最多10条最近接入/刷新回执，有更多则提示本区域仅摘要。失败摘要区与版本列表有独立标题，不合并计数、不把行情回执当调度任务。找不到历史版本时仍可展示已有失败回执。
3. 固定版本详情：管理员明确选择版本后查看元数据、当前头关系及指定日期诊断。默认含今天90日、可调整到366日；只在点击“应用诊断日期”后查询。历史版本覆盖范围外显示“不在此版本覆盖范围”，不是缺失行情。

不出现补取/标记不可用的假按钮。草稿筛选尚未应用时不清理或查询；应用新的q/provider/headState后清理版本列表、回执、选中版本和诊断，避免旧来源详情搭配新筛选。仅翻列表页或同条件刷新列表可以保留明确选中的固定版本详情，详情仍显示自己的股票、来源、版本和查询时间，不伪装为刚刷新。切换股票清空版本与诊断，切换版本/应用诊断日期清空旧诊断。同条件读取失败可保留旧数据并标“尚未刷新”和原查询时间；条件发生变化则不复用旧结果。401/403清空全部、503仅影响对应区域，离页/账号切换/降权拒绝迟到响应。无自动轮询。

## 4. 查询接口与统一契约

网关与MS-02均只允许以下三个GET路径；精确OPTIONS无角色解析、取数和计数。统一{code,message,data,traceId}，Cache-Control:no-store。返回queriedAt、definitionVersion="admin-market-readonly-v1"，时间点UTC ISO8601，界面显示上海时间，日期字段LocalDate。

### 4.1 GET /api/v1/admin/market-data

参数q（可选，trim后最多64字符，空串视无搜索）、provider（可选baostock/tushare）、headState（可选AVAILABLE/STAGING/REJECTED/UNAVAILABLE/NO_VERSION/REFERENCE_ERROR）、page（默认1，1..10000）、size（默认20，1..50）。参数不得重复或出现未知名称；数字禁止小数/指数/前后空白。q按代码/名称包含匹配，转义SQL LIKE的%、_、反斜线，不能当正则或SQL。

数据HeadPage(queriedAt,definitionVersion,query,items,hasMore)。query回显规范化筛选与页码，items为HeadRow：instrumentId,ticker,displayName,nameVerified,listingStatus,provider,priceBasis,headPresent,headState,headVersionId,headRevision,versionStatus,quality,coverageStart,coverageEnd,checkedAt,fetchedAt,publishedAt,lastErrorCode。

默认以非demo instrument LEFT JOIN当前头，稳定按instrumentId、来源存储原值、priceBasis排序；page用size+1判断hasMore，不伪造全局total。provider筛选放在JOIN条件，未有该来源头的股票仍生成缺失行，provider/priceBasis为空、headPresent=false；选定来源保留在query中，不能把筛选条件冒充已有数据。来源比较可LOWER用于兼容历史存储大小写，显示/行键保留有界原值，不能合并两个不同存储键。headState筛选在缺失行生成后执行。

无头或head.version_id为空时headState=NO_VERSION；有关联版本时必须同时核对version的instrumentId/provider/priceBasis，异常关联为REFERENCE_ERROR，不显示其他股票的版本元数据。AVAILABLE等状态仅来自实际关联版本；lastErrorCode是另一个字段，不用失败错误覆盖可用状态。修订号为十进制字符串，其余未知字段保持null，不补0日期。

### 4.2 GET /api/v1/admin/market-data/{instrumentId}/versions

instrumentId只接受(SH|SZ)[0-9]{6}。参数provider同上、cursor可选、size默认20/max50；无page或日期参数。响应VersionPage(queriedAt,definitionVersion,instrumentId,provider,items,nextCursor,recentIngestions,recentRefreshes,ingestionsTruncated,refreshesTruncated)。

items为VersionSummary(versionId,provider,priceBasis,status,quality,coverageStart,coverageEnd,fetchedAt,publishedAt,isCurrent)。不只列AVAILABLE，避免把历史状态藏掉；不返回原始供应商正文或文件路径。版本严格归属所选非demo股票。

版本按published_at DESC,version_id DESC，用最后一条键进行keyset翻页，每次取size+1；并发新增版本不会让下一页重复上一页，新增记录需刷新第一页查看。isCurrent和回执摘要是各次查询时状态，列表跨页不是全局原子快照。cursor只放v=1、instrumentId、规范化provider、lastPublishedAt（UTC毫秒）、lastVersionId，Base64URL无填充、最多512字符；严格JSON字段、真实日期、UUID及查询绑定检查。cursor是非授权翻页位置，可人为构造但不能改变参数化SQL/角色/股票归属，不引入Token或签名密钥。非法游标400，不能静默回第一页。

IngestionSummary(operationId,state,versionId,errorCode,createdAt,finishedAt)来自本库接入回执。RefreshSummary(operationId,state,targetDate,versionId,errorCode,createdAt,finishedAt)来自本库刷新回执。两者各取created_at DESC,operation_id DESC最多11条以输出10条及truncated；provider筛选接入记录来源，刷新回执仅现有Baostock链路，选择tushare时该列表为空并明确当前未接入此来源刷新回执。PENDING/RUNNING不推断超时失败，不执行对账或过期落库。

### 4.3 GET /api/v1/admin/market-data/{instrumentId}/versions/{versionId}

versionId为规范小写UUID，必须归属所选非demo股票，否则404 MARKET_ADMIN_RESOURCE_NOT_FOUND。参数from/to必须成对，严格ISO日期；未提供时上海今天向前89日，含首尾90日。范围1990-01-01至上海今天，最多366日；历史覆盖不足不自动扩大范围、不访问供应商。

响应VersionDetail(queriedAt,definitionVersion,instrumentId,version,window,diagnostics)，window为Window(from,to,timeZone="Asia/Shanghai")。version在VersionSummary基础上含contentHash/sourcePayloadHash（仅合法64位十六进制，否则null）及headRevision（当前引用时为字符串，否则null）。不返回request_hash、原始payload、内部URL/路径、Token或账户信息，不声称历史版本从未被更改有外部公证。

## 5. 可解释诊断：固定版本、有限窗口

只用同一versionId的日线和随版本保存的market_calendar_day，不把今天另一版本的日历悄悄替换进历史依据。界面标“同版本随附日历”，非独立数据源证明。

- requestedFrom/to是管理员选择；effectiveFrom/to是与version.coverageStart/end交集，无交集则null且state=OUTSIDE_COVERAGE。outsideCoverageDays单独计，不算缺失。
- 在有效交集内按日期逐日核对：barDays、有交易行tradedRowDays（trade_status=1）、已记录停牌行suspendedRowDays（trade_status=0）、knownOpenDays、unknownCalendarDays。
- suspectedMissingOpenDays：已知开市日无日线行。提示“需核对停牌或数据缺失”，不能只据此判数据错误或自动下线；有停牌行的开市日属于已有行，不计疑似缺失。
- barsOnClosedDays：已有日历明确休市但有日线；invalidRowDays：已有行违反基本正价格/OHLC边界、trade_status值或交易行成交量/额有效性。此查询只给检查结论，不改旧行；不宣称实现所有财务数据检查。
- 上述异常/疑似日期分别返回总数十进制字符串及最多20个升序日期样本、truncated；不会下载无限历史或通过分页把限制绕过为自动扫描所有版本。
- 无交集优先OUTSIDE_COVERAGE。有效交集内，invalidRowDays或barsOnClosedDays非零时state=ISSUES（已发现保存记录异常）；仅有suspectedMissingOpenDays非零时state=NEEDS_REVIEW（存在待核对日期）；否则有未知日历时state=INCOMPLETE_EVIDENCE；其余state=NO_RECORDED_ISSUES。优先级为OUTSIDE_COVERAGE、ISSUES、NEEDS_REVIEW、INCOMPLETE_EVIDENCE、NO_RECORDED_ISSUES，各分项仍完整展示。待核对不等于已确认数据错误；“当前检查未发现问题”也不能叫“100%准确/质量满分”。
- counts均非负十进制字符串，支持超过JS安全整数；无可计算依据用null并说明原因，不输出假质量分、收益率或未经定义的完整率。日期枚举最多366项，SQL只取此版本/日期交集，BigDecimal处理原始数值，不用浮点比较。

Diagnostics字段：state,calendarEvidence="VERSION_ATTACHED",effectiveFrom,effectiveTo,outsideCoverageDays,barDays,tradedRowDays,suspendedRowDays,knownOpenDays,unknownCalendarDays,suspectedMissingOpenDays,barsOnClosedDays,invalidRowDays。后三项为DateFinding(count,sampleDates,truncated)，其他计数为字符串。

## 6. 数据访问与一致性

AdminMarketReadRepository分别提供heads(ListFilter,Instant)、versions(String,HistoryFilter,Instant)、detail(String,UUID,DetailFilter,Instant)，本库RR只读事务、5秒超时。原SQL使用预编译参数，不走MarketVersionRepository.publish/reject、RefreshRepository.lookup/finish或任何collector。每个分区单次请求一致，不承诺跨请求或跨服务同一快照。

诊断记录输入使用DiagnosticBar(date,open,high,low,close,volumeShares,amountYuan,tradeStatus)及CalendarDay(date,open)，由CoverageDiagnostics.calculate(DetailFilter,VersionBounds,List<DiagnosticBar>,List<CalendarDay>)纯函数返回Diagnostics。版本界限与实体记录先校验，不将损坏的元数据转换成合法0；本库暂不可查询503 MARKET_ADMIN_UNAVAILABLE。

当前无Schema变更。日线/日历现有(version_id,date)主键可限制范围；历史版本、接入/刷新回执可能缺匹配排序的索引，实施前做EXPLAIN并在真实小数据量计时。若需要扩容索引，单列迁移任务/锁表风险/回退，不在本片临时改原库，不宣称规模性能达标。

## 7. 权限与网关

MS-02新AdminMarketDataAccessFilter精确保护三个路径，沿用SessionIdentityClient及SDD009C严格credentialVersion解析；拒绝重复session Cookie/多Cookie头、未知方法路径、畸形身份。匿名401、普通用户403，身份服务异常503 USER_SERVICE_UNAVAILABLE，参数400 MARKET_ADMIN_QUERY_INVALID，资源404，数据依赖503。错误码白名单映射中文，未知error_code统一UNKNOWN_ERROR，不回传内部异常正文。已知错误白名单限SOURCE_TIMEOUT、SOURCE_BUSY、SOURCE_UNAVAILABLE、SOURCE_INVALID、MARKET_DATA_INCOMPLETE、MARKET_DATA_INVALID、REFRESH_REQUEST_INVALID、REFRESH_RANGE_INVALID、REFRESH_OPERATION_CONFLICT、REFRESH_RUNNING、ATTEMPT_EXPIRED、MARKET_REVISION_CONFLICT、MARKET_COVERAGE_REGRESSION、MARKET_OPERATION_CONFLICT、MARKET_OPERATION_REJECTED、REAL_MARKET_VERSION_REQUIRED；null保留null，新增错误码需显式加入映射。

网关提取现有StatisticsAdminAccess中的通用逻辑到security/AdminReadAccess，不改变StatisticsIdentityClient的Feign发现、严格JSON或禁止重试配置；StatisticsAdminAccess保留原构造/方法作为委托兼容层，新代理复用AdminReadAccess。通用403文案“仅管理员可以访问此功能”。这一内部提取不是新增产品业务。

AdminMarketDataProxyController保留MultiValueMap至校验完成，防止重复参数提前丢失；使用共享AdminMarketQuery校验后转单值Map，通过MarketPublicClient.get转发，保留服务发现和统一安全头；不新建裸HTTP/内部公开Token。只转发小于等于256KiB、统一OK对象响应和白名单错误；不信任X-User/X-Role。UserApiProxyController拒绝新子树，防止回落用户服务。HTTP进程计数归ADMIN组，固定标签不包含股票、游标或URI。

网关和market各新增精确GET/OPTIONS CORS配置，注册次序沿用现有-100预检/-90角色保护；不扩大原任务写路径，也不向/public actuator开放数据。统计与原任务接口须回归。

## 8. 实施及验收

按契约/诊断纯函数→本库只读查询与权限→网关→前端→完整回归/一次独立审查/部署验收推进。每个自建源码目录附README说明作用与实现，不建空目录。

先观察测试失败再实现，使用核对标签和tmpfs的隔离MySQL及五个_test库；不得用真实库跑测试清理。真实验收只读：先检查列表初始化/刷新、历史翻页、固定版本前后对照等代表场景；6次成功管理GET只作为首轮规模参考，不是授权上限。必要的本地只读复核可继续，不为增加GET次数反复要求授权，记录实际次数；匿名/普通账号拒绝检查单独记录。不用真实候选或失败任务造数据。不调用MaaS、邮件、算法和行情供应商，不改现有worker开关。真实无缺陷样本时用隔离fixture证明异常分支，不伪造生产记录。

全模块clean verify、前端测试/类型/构建后进行一次独立审查，重要问题一轮RED→GREEN修复及受影响回归；再备份market原库与受影响旧镜像，核对原卷/.env/开关，受控部署实际变化的market/gateway/frontend。若shared-common改变但原其他服务实现无变化，仍全后端验证；不无故重建其他服务。本片不迁移，market维持V13。

验收区分自动化、代理实际检查和用户人工报告：ADMIN可读、USER直连和网关代表路由拒绝、匿名401/no-store；固定版本详情一致、读前后业务行/版本/头/回执不变；若原worker自然写入，独立标识原任务活动并在稳定窗口复查，不为验收停用原worker或把自然活动归于只读查询；窄屏横滚、Tab/Enter及无障碍标签、退出/降权/迟到响应与局部失败。既有恢复/回退和容量未验证限制继续说明。真实取消及正式实验/周训练、CodeArts/知网证据不借此关闭。

## 9. 自审结论

已逐项核对FR-11与原AC-11：本片交付查询/检查，去重发布和异常不入新输入仍沿用原链路；补取/标记不可用及依赖传播明确留后续，不删需求。已纠正三类易混淆：头可用不等于最近更新成功、无被拒版本不等于没有拒绝记录、日历开市缺行不等于确认异常。接口字段与计划相同，当前仅文档，不宣称代码或测试已完成。

## 10. 2026-10-22用户委托的范围审核

结论：保留本片三个查询区域，不增删13项业务，不提前加入写操作；范围适合作为FR11的第一个可独立验收切片。现有market表和回执可支持查询，但只能提供已保存证据，不能承诺数据来源正确率或完整停牌识别。

本轮已修正：疑似缺失单列NEEDS_REVIEW；应用筛选后的从属区域清理规则明确；本地只读GET不设人工授权次数锁。仅覆盖已登记的非demo股票，登记前被拒绝或无法关联股票的原始接入记录不作为全局异常审计平台扩入本片；此限制不等于删日志，日后可单独规划。

当前列表一行是股票+来源+口径，不是股票去重总数；界面不把行数标成股票数量。当前存有Tushare记录与实时访问权限不同，查询不到也不证明供应商不可用。历史版本“当前引用”只说明head关系，不等于其状态一定AVAILABLE。诊断只读，绝不因标签自动改版本状态或解除预测限制。

90/366日、分页20/50、page10000、日期样本20、回执摘要10、5秒事务与256KiB响应检查是当前版本的集中策略参数，不写成不可修改的永久业务规则。实现时服务端集中定义，前端展示/校验及测试同步；以后调整参数不改变版本归属、只读、权限及不改历史快照等正确性不变量。

后续手动补取、标记不可用/依赖传播、全局异常审计、跨来源一致性评价、指标重算、质量评分和自动切换均不在本片。已有FR11其他需求仍在待办，不能将第一片完成说成FR11全部完成。用户已经委托代理审核该范围，不再次要求用户阅读同版文档作形式批准；后续业务增删、关键行为变化或外部付费仍另行确认。
