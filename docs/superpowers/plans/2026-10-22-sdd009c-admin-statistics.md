# 管理员运营统计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 交付有明确数据口径、分区故障隔离及权限保护的管理员统计页。

**Architecture:** 四业务服务各自只读聚合本库，通过原Nacos/Feign暴露精确管理员GET；前端并发读取各分区。网关仅采集本进程HTTP完成请求计时，不跨库、不拉行情、不调用模型。

**Tech Stack:** Java17、Spring Boot3.2.5、JdbcTemplate、MySQL8、JUnit5、Vue3/TypeScript/Pinia/ECharts/Vitest；沿用现有依赖。

**Spec:** E:/aiStock/项目代码/stock-system/docs/superpowers/specs/2026-10-22-sdd009c-admin-statistics-design.md

状态：2026-10-22八项任务已实现并验收：代码、一次独立审查与修复、982/277项回归及构建、备份部署与真实只读页面/角色核验均完成。保持原分支和其他未完成业务；完整结果见docs/verification-sdd009c.md。

## Global Constraints

- 13项业务、五业务服务、ADMIN/USER两角色不变；不跨库SQL、无新数据库或外部调用。
- 业务日期Asia/Shanghai，默认含今天7日，from/to必须成对，最大90日；起点包含、to次日00:00不包含，内部UTC。
- 用户存量、行情当前头、网关进程观测不伪装成所选日期历史快照；各分区显示自己的queriedAt。
- 成功登录人数只代表成功登录活跃，不标DAU；预测业务请求不当HTTP/算法实际推理；同结构不同种子是模型版本。
- 统计count/非空定点值为字符串，空样本均值/比例null；不可用不是0；所有状态保留原定义。
- 接口耗时仅网关当前进程；不与任务尝试耗时、首次派发等待或训练耗时混合，不提供伪P95。
- GET不写业务数据，不触发外部行情、预测、MaaS或邮件；统计自身访问不进入观测业务组。
- 直连与网关都必须校验ADMIN，剥离伪造身份；401/403清空统计，503局部失败。统一API+no-store。
- 当前分支codex/sdd004-market-refresh和固定目录保留；不建新worktree，选择性小步提交，不push。每个自建目录附README。
- 无证据不宣称测试通过/性能达标；SDD009B取消待办及FR08/11/12/13其他缺口不删。本片不写CodeArts实际生成证据。

## Review Focus

1. user last_active_at不是DAU、会话过期注销不应抹成功登录历史；Task2验证跨天/多会话/注销样本。
2. 同日期请求的state后续变化与隐藏/幂等重放不应让总量漂移；Task3验证当前结果分布与模型分母。
3. 重试后历史尝试、旧任务跨窗口新尝试和异常时间不应混成同一分母；Task4验证独立时间队列。
4. 任意动态URI、异常/错误分派和计数并发不能泄露个人数据或生成无界标签/错误率>100%；Task6验证固定标签与一次最终计数。
5. 同进程旧数据、跨日期旧请求、登录撤权和一服务故障不能串分区/身份；Task7验证迟到响应和局部失败，Task8核对真实路由。

## 文件职责与接口约定

每个业务statistics目录放本分区Records、Repository、Controller；不把报表SQL塞入原业务仓库。stock-common新增statistics/StatisticsWindow.java、StatisticsQuery.java与README，仅负责无业务状态的日期/数字与元数据契约，不持有DB/身份/HTTP客户端。下文U/P/S/M/G分别表示backend/stock-user-service、stock-prediction-service、stock-scheduler-service、stock-market-service、stock-gateway，包根均src/main/java/com/aistock/{user,prediction,scheduler,market,gateway}；测试同模块src/test/java同包。任务File列表的模块缩写须按此机械展开，不猜目录。

共享类型（CountByKey/DailyCount/DurationSummary集中定义在公共statistics/StatisticsRecords.java，通过静态导入使用）：StatisticsWindow.parse(MultiValueMap<String,String>,Clock)；StatisticsMeta(window,queriedAt,definitionVersion)；CountByKey(String key,String count)；DailyCount(LocalDate date,String count)；DurationSummary(String sampleCount,String excludedInvalidCount,String meanMs)。元数据window输出只包含from/to/timeZone，不把UTC内部边界混成另一筛选口径。业务records使用组合meta字段；前端DTO与Java字段逐个一致。默认所有Records为Java嵌套record，无Lombok。

路径GET /api/v1/admin/statistics/users、predictions、tasks、market（日期参数）；GET /api/v1/admin/statistics/http（无参数）。market额外page/size，其他分区拒绝。所有接口错误data=null。

### Task 1：统一统计日期和输出契约（约0.5日）

**Files:** Create backend/stock-common/src/main/java/com/aistock/common/statistics/{StatisticsWindow.java,StatisticsQuery.java,StatisticsMeta.java,StatisticsNumbers.java,StatisticsRecords.java,README.md}；Test backend/stock-common/src/test/java/com/aistock/common/statistics/{StatisticsWindowTest.java,StatisticsNumbersTest.java,README.md}。

**Interfaces:**
- StatisticsWindow.parse(query,clock)产出四字段record，StatisticsMeta.of(window,now)输出公共meta（含definitionVersion="admin-statistics-v1"）。
- StatisticsQuery.window(query,clock,Set<String> extras)允许market额外page/size但保留日期严格校验；.none(query)供http拒绝所有参数；.page/.size仅解析明确上限。
- StatisticsNumbers.count(long)；ratio(long numerator,long denominator)返回百分数2位或null；meanMillis(BigDecimal sumMillis,long samples)保留3位或null。禁止负计数，0ms是合法样本。

- [x] **Step1 RED：** 写ShanghaiMidnightUsesHalfOpenUtcRange、requiresPairedDatesAndRejectsDuplicates、zeroDenominatorIsNull、largeCountsRemainStrings。断言Clock固定2026-10-22T00:00:00+08，默认from=10-16、to=10-22，UTC边界10-15T16:00Z/10-22T16:00Z；同一天有效、91日/未来日/重复from/未知参数400。
- [x] **Step2 运行：** backend执行mvn -B -pl stock-common test；Expected新增类型或行为缺失导致失败，排除编译环境错误后才算RED。
- [x] **Step3 GREEN：** 实现上述纯函数和record，日期错误映射BusinessException/STATISTICS_QUERY_INVALID400。计数999999999999999999可精确序列化；1/3=33.33，分母0=null。
- [x] **Step4 验证与提交：** 同命令PASS，记录实数；仅add本任务文件，commit `feat: 定义运营统计日期与数值契约`。

### Task 2：用户规模与登录统计（约0.75日）

**Files:** Create U/.../statistics/{UserStatisticsRecords.java,UserStatisticsRepository.java,UserStatisticsController.java,README.md}；Test同包UserStatisticsMysqlTest、UserStatisticsHttpTest及README；必要时扩展现有AuthDatabaseFixture仅测试工具，不修改真实登录业务。

**Interfaces:** UserStatisticsRepository.summary(StatisticsWindow,Instant):UsersSummary；Controller GET users(query,Authentication)要求ADMIN。UsersSummary(meta,totalUsers,enabledUsers,disabledUsers,roleCounts,registrations,registrationDaily,successfulLogins,distinctLoginUsers,loginDaily)；LoginDay(date,successfulLogins,distinctUsers)。当前角色分布USER/ADMIN补0，状态ENABLED/DISABLED补0。

- [x] **Step1 RED：** 隔离库插入普通用户2、管理员1，ENABLED2/DISABLED1；同用户当日2次登录、另一日1次，另一用户当日1次；窗口内登录4、去重2、日去重合计3。撤销/过期会话仍计原成功，不凭last_active_at补造；窗口外注册影响存量不影响新增。测试前后业务行及last_active_at不变。无邮箱/sessionId/password/token出现在JSON。
- [x] **Step2 运行：** 用已核验隔离DB环境在backend执行mvn -B -Pauth-integration,cli-build -pl stock-user-service -am test '-Dtest=UserStatisticsMysqlTest,UserStatisticsHttpTest' '-Dsurefire.failIfNoSpecifiedTests=false'；Expected缺实现RED。
- [x] **Step3 GREEN：** 本库只读RR事务统计存量、按user_account.created_at新增、login_session.created_at计成功登录；上海分组，不用DATE(UTC列)直接分日。沿用现有真实会话ADMIN校验；新统计路径拒绝重复Cookie/身份头注入。若安全框架默认合并cookie，统计路径前置精确验证，避免放宽其他业务。
- [x] **Step4 验证：** mysql/http与完整user-service测试PASS，包含401、403、ADMIN200、no-store、错误参数及只读；不为测试发送验证码邮件。
- [x] **Step5 提交：** 选择本任务文件，commit `feat: 提供管理员用户与登录统计`。

### Task 3：预测请求及模型版本使用统计（约0.75日）

**Files:** Create P/.../statistics/{PredictionStatisticsRecords.java,PredictionStatisticsRepository.java,PredictionStatisticsController.java,README.md}；Modify PredictionSecurityConfiguration.java、PredictionSessionFilter.java（仅新增路径所需严格身份边界）；Test同包PredictionStatisticsMysqlTest、PredictionStatisticsHttpTest。

**Interfaces:** summary(StatisticsWindow,Instant):PredictionsSummary(meta,acceptedRequests,stateCounts,requestDaily,modelUsage)。PredictionDay(date,accepted,succeeded,failed,running)；ModelUsage(modelId,artifactId,label,acceptedRequests,successfulRequests,usagePercent)。标签不输出manifest/evaluation原文。

- [x] **Step1 RED：** 预测6条、模型A4/B2、隐藏1条仍计6；SUCCEEDED3/FAILED2/RUNNING1；模型占比分别66.67/33.33。缺model_version记录的历史模型仍有分组且label为“历史版本信息暂缺”；同operation重放无新增；RUNNING转成功只改状态分组不改accepted。上海日界、全空、超JS安全整数序列化和百分数舍入测试。
- [x] **Step2 运行：** Maven隔离命令同Task2，模块stock-prediction-service，test=PredictionStatisticsMysqlTest,PredictionStatisticsHttpTest；Expected RED。
- [x] **Step3 GREEN：** created_at窗口且hidden不筛除，本库LEFT JOIN版本元信息，非TopN、排序稳定。精确统计管理员路径加入hasRole ADMIN，不让authenticated()误放普通用户；身份服务503与撤权401/403分别保留，未知/畸形身份失败关闭。
- [x] **Step4 验证/提交：** 新测试及prediction完整套PASS；mock算法/MaaS调用次数0，读取不改snapshot/请求行；commit `feat: 汇总预测请求与模型版本使用`。

### Task 4：任务状态和两类耗时（约0.75日）

**Files:** Create S/.../statistics/{TaskStatisticsRecords.java,TaskStatisticsRepository.java,TaskStatisticsController.java,README.md}；Modify security/AdminTaskAccessFilter.java及其注册配置（检索现有Scheduler配置确认文件名后记录）；Test TaskStatisticsMysqlTest、TaskStatisticsAccessTest及新目录README。

**Interfaces:** summary(StatisticsWindow,Instant):TasksSummary(meta,taskTotal,taskStateCounts,taskTypeCounts,taskDaily,attemptTotal,attemptStateCounts,attemptDuration,unfinishedAttempts,firstQueueWait)。DurationSummary沿用公共定义；taskDaily为日期及taskTotal，当前状态分组单独展示。

- [x] **Step1 RED：** 一个task三次尝试不能变三个task；旧窗口外task在窗口内新attempt必须只计attempt；RUNNING/RECONCILING/EMPTY/SKIPPED/CANCELLED不并入失败；尝试耗时[0,100,300]均值133.333，负时间剔除并计异常，未结束不入样本。ordinal1首次派发等待可计，重试不作为首次；查询revision和旧失败行不变。
- [x] **Step2 运行：** Maven隔离模块stock-scheduler-service，test=TaskStatisticsMysqlTest,TaskStatisticsAccessTest；Expected RED。
- [x] **Step3 GREEN：** 三类白名单，任务按创建时间、尝试按started_at各聚合，RR事务；路由只新增GET /api/v1/admin/statistics/tasks，不扩大原task动作白名单。单条异常记录不使整份统计失败，不把异常耗时变0。
- [x] **Step4 验证/提交：** 完整scheduler测试含009A/B原权限与动作矩阵PASS；commit `feat: 提供任务状态与执行耗时统计`。真实取消待办不以此关闭。

### Task 5：行情当前发布状态（约0.5日）

**Files:** Create M/.../statistics/{MarketStatisticsRecords.java,MarketStatisticsRepository.java,MarketStatisticsController.java,AdminStatisticsAccessFilter.java,README.md}；Modify security/MarketSecurityConfiguration.java；Test MarketStatisticsMysqlTest、MarketStatisticsAccessTest。

**Interfaces:** summary(StatisticsWindow,int page,int size,Instant):MarketSummary(meta,scope,instrumentCount,availableHeads,items,hasMore)；HeadItem字段按spec7。scope固定CURRENT_HEADS；page默认1、size20、上限10000/50。

- [x] **Step1 RED：** 一股票两个AVAILABLE来源头统计instrumentCount1/availableHeads2；历史AVAILABLE但不在head不重复计；demo排除；未建head和head.version为空保留明确缺失；REJECTED/UNAVAILABLE不算可用；相同checkedAt但不同coverageEnd不能显示相同最新交易日；分页与稳定排序测试。
- [x] **Step2 运行：** Maven隔离模块stock-market-service，test=MarketStatisticsMysqlTest,MarketStatisticsAccessTest；Expected RED。
- [x] **Step3 GREEN：** 以非demo instrument为基础LEFT JOIN本库head和version；无head时item允许provider/priceBasis/versionId/status等null，用NULL FIRST排序固定。counts统计当前AVAILABLE头而不是全版本；查询不访问供应商。新filter精确GET/statistics/market，权威ADMIN，重复Cookie和伪造头拒绝；预检无resolve/取数。
- [x] **Step4 验证/提交：** 新测试和market完整回归PASS；commit `feat: 展示管理员行情发布状态`。

### Task 6：网关安全分发与进程响应指标（约1日）

**Files:** Create G/.../statistics/{AdminStatisticsApiProxyController.java,StatisticsIdentityClient.java,StatisticsAdminAccess.java,HttpStatisticsCollector.java,HttpStatisticsFilter.java,HttpStatisticsRecords.java,HttpStatisticsConfiguration.java,README.md}；Modify gateway/ProxyHeaderPolicy.java、UserApiProxyController.java与必要CORS配置；复用UserPublicClient/MarketPublicClient/PredictionPublicClient或ForecastPublicClient/SchedulerPublicClient的GET（实施前按实际method类型转换，不新建裸传输）。Test AdminStatisticsProxyTest、StatisticsDiscoveryWiringTest、HttpStatisticsCollectorTest、HttpStatisticsAccessTest。

**Interfaces:** Collector.record(String group,int status,long durationNanos)；snapshot(Instant queriedAt):HttpSummary(processStartedAt,observedSince,queriedAt,definitionVersion,scope,groups,droppedSamples)。固定group枚举；AdminStatisticsApiProxyController只映射精确五分区；StatisticsAdminAccess.requireAdmin(HttpServletRequest)使用服务器resolve，不对无权限者返回指标。

- [x] **Step1 RED：** 5路GET分发到正确服务，from/to和market page/size完整保留；重复/额外参数400；http不接受日期；POST404；User通用/admin/**不能抢路由；response<=256KiB且只读错误脱敏。无显式URL的真实Feign装配通过虚拟发现实例的随机非默认端口，不能只测URL直连。
- [x] **Step2 RED：** Collector记录完成请求100个（80个2xx、10个4xx、10个5xx），耗时每个10ms，计数100、mean10.000、error20.00；多线程读写满足errors<=total；未知URI不生成新group；统计/health/internal/静态访问不计；ERROR分派不二计，异常最终500非200；新实例计数0和不同processStartedAt。mock单调时钟避免sleep测性能。
- [x] **Step3 运行：** backend mvn -B -Pcli-build -pl stock-gateway -am test，Expected上述新增测试RED。
- [x] **Step4 GREEN：** 精确代理、服务发现保留、失败映射STATISTICS_UNAVAILABLE503（非账号失效）；http本地权威ADMIN；统计只读GET、no-store。计时器固定八组，完成后单次同步提交一个不可变聚合快照；不要用LongAdder分别读取导致比率不一致。MVC异常最终状态无法确定时droppedSamples++，明确实现顺序与测试相符，不吞异常、不改响应。不扩大/actuator暴露面。
- [x] **Step5 验证/提交：** 网关全套与真实发现装配PASS；日志不含Cookie/URL原文/query/正文；commit `feat: 接入运营统计代理与网关响应观测`。

### Task 7：管理员统计页面与故障隔离（约1日）

**Files:** Create frontend/src/api/adminStatistics.ts及test；Create frontend/src/pages/admin/statistics/{StatisticsPage.vue,StatisticsQueryForm.vue,StatisticsSection.vue,StatisticsChart.vue,statisticsPresentation.ts,README.md}及对应必要.test.ts；Modify frontend/src/router/index.ts、实际主导航组件（先定位文件，不能假定App有全部导航）、router权限测试。

**Interfaces:** api getUsers(window)、getPredictions(window)、getTasks(window)、getMarket(window,page,size)、getHttp()，返回上述对应DTO；window={from,to}。getHttp无入参；从auth.ts现有request只读通道复用，统计没有POST/自动业务动作。

- [x] **Step1 RED：** 管理员可进入、普通用户拒绝；一个服务503其他4块正常且不填0；同查询旧值显示旧时间/stale，换日期清空旧值；旧日期的迟到response和401/403不能恢复数据；http进程ID变化清理旧指标。零样本“暂无样本”、三候选标签为模型版本，不出现保证准确率。
- [x] **Step2 RED：** 完成初始化仅5个GET，刷新不调用POST；日期只在点击应用后生效；进行中按钮禁用，最多一批；page切换只查market且保留同日期。巨大count保留原字符串，超过Number.MAX_SAFE_INTEGER时禁图并显示精确文本，不能强转失真。keyboard聚焦/回车筛选、空态、图表文字替代。
- [x] **Step3 运行：** frontend pnpm test -- src/api/adminStatistics.test.ts src/pages/admin/statistics；若当前Vitest版本将--后参数当普通值造成全套运行，记录实际全套，不误称仅运行目标；Expected新模块/行为RED。
- [x] **Step4 GREEN：** 页面六区块用Promise.allSettled及epoch身份边界，分区元数据独立，当前存量/所选日期/进程期间视觉分明；禁止自动轮询。ECharts固定图例、注册resize/unmount清理，图表失败不影响精确文本。窄屏图表最小宽与滚动、标签可换行；不在产品文案堆框架名。
- [x] **Step5 验证/提交：** pnpm test、pnpm typecheck、pnpm build全部PASS，原包体警告另记不假称消除；commit `feat: 增加管理员运营统计页面`。

### Task 8：全回归、独立审查、只读部署与验收（约1—1.5日）

**Files:** Create docs/verification-sdd009c.md、docs/evidence/sdd009c-verification.json；Modify实施状态/待办/13业务核对报告及specs/plans README；运行日志和私密备份runtime/sdd009c不提交。

- [x] **Step1 环境隔离：** 运行scripts/start-auth-test-db.ps1前确认测试容器label=aistock.sdd002.auth-test、tmpfs、127.0.0.1:13316、五个_test数据库，无生产卷；隐藏读取runtime/auth-test-db-secret.txt只注入AUTH/MAIL/MARKET/SCHEDULER/PREDICTION_TEST_DB_URL与对应PASSWORD，地址分别stock_user/notification/market/scheduler/prediction_test。缺任一或production地址则拒绝运行。用本片自有执行工作区脚本，不读写其他计划临时文件。
- [x] **Step2 全回归：** backend mvn -B -Pauth-integration,cli-build clean verify；frontend pnpm test/typecheck/build。保存逐模块实际测试/失败/错误/跳过数；不复用933/261旧数作为本片结果。分区SQL用已知fixture独立计算核对，SELECT数量不靠同一聚合SQL复制校验。检查查询EXPLAIN；如索引不足先修订迁移计划，不宣称大数据性能已达标。
- [x] **Step3 一次独立代码审查：** 覆盖本片全差异和Review Focus五项；按既定inline流程一位fresh reviewer，重要问题RED→GREEN一轮修复及受影响全套，无重复评审循环；记录所有取舍及搁置项。
- [x] **Step4 部署保护：** docker compose config --quiet；确认原卷desktop-linux、七应用、各服务原DB版本及功能开关；备份四业务库的结构/数据和拟更新服务旧镜像并核对SHA256。统计片不改真实.env，不开009B写入口、不改worker/MaaS/邮件。代码shared-common变化需构建验证依赖方，部署仅实际改动的user/prediction/scheduler/market/gateway/frontend，通知/算法不重建。
- [x] **Step5 部署核验：** up -d --no-deps更新明确组件，原持久卷保留；七应用UP、Nacos每服务单健康实例、HTML与新asset哈希一致、原登录/业务可读。无Schema变化则四库原Flyway版本保持；若后续批准加索引必须单独记录真实迁移。不可打印.env/docker展开Env。故障先回原镜像，不倒灌备份；完整回退未演练即标未演练。
- [x] **Step6 只读验收：** 用户自行登录或使用其已登录会话、不得复制Cookie或历史密码。五分区显示口径/期间、模型标签、空值与数据时间；任取固定窗口独立只读复算，连续刷新不增加预测/任务/行情业务行，只有已授权登录会增加登录记录。普通用户直连和网关403、匿名401；身份服务故障模拟只在隔离环境，不随意停止真实业务。页面窄屏/Tab/同查询失败/换日期验证。首验最多读取五分区2轮加market1次分页，不调用付费AI、发邮件或触发供应商。
- [x] **Step7 记录与提交：** 区分产品实现/测试/部署/人工验收，HTTP明确进程起点及重启限制，不以有图证明有效DAU或历史监控；FR13仍保留其他任务适配。选择性commit `test: 记录管理员运营统计验证结果`，无remote不push，保留当前分支。只有上述门都通过才标本片已验收。

## 排期、依赖和检查点

阶段A（第1—2工作日，目标10-23至10-26）：Task1/2/3，完成日期契约、用户和预测可复算接口。检查点：口径可解释、计数不混淆，先不做图表美化。

阶段B（第3—4工作日，目标10-27至10-28）：Task4/5/6，任务/行情与安全网关响应采集。检查点：五接口独立权限通过，服务发现无回归；HTTP不能提供历史的限制在响应里明确。

阶段C（第5—6工作日，目标10-29至10-30）：Task7及Task8前半，完成页面、全回归和一次独立审查。

阶段D（第7—8工作日缓冲，目标11-02至11-03）：审查修复、备份部署和真实只读验收。日期是目标安排而非保证；从用户正式确认设计及计划后的实际开始日顺延，不占用或改写任务书已定节点。

Task1产出被Task2—6消费；Task2—5接口共同供Task6代理与Task7页面使用；Task6进程指标供Task7/http；Task8只在全部完成后执行。实现仍顺序进行，不自动多agent并行开发。原009B取消不成为统计只读开发阻塞项，待自然合适任务另补验收。

## 自审与审核门

设计1—3由Task1、Task8约束覆盖；4用户由Task2；5预测由Task3；6任务由Task4；7行情由Task5；8进程指标及9网关安全由Task6和各服务权限测试；10页面由Task7；11交付与边界由Task8。五项Review Focus均有明确测试。

检查DTO与TS字段一致、from/to边界统一、market额外参数仅本分区、http不接日期；保护旧原任务状态机、无跨库写与后台副作用。异常样本、空集和部分服务故障均有确定行为。不设未测性能阈值，不夸大进程统计或模型表现。

历史规划阶段要求先审核再实施；用户已批准，当前实际进度以上述状态和核验记录为准。

### 可实施性检查及自审修订

独立builder-check两问均已解决：无头股票必须生成缺失行并参加分页，Task5按instrument LEFT JOIN实施；started_at缺失无法归窗口，不推测归属，Task4明确只对可归窗口样本判断负耗时/未结束。后者当前NOT NULL字段，测试用SQL数据完整性断言不强造不合法NULL生产Schema；对未来兼容输入的纯函数可测空起点不归日。

字段复核：四分区meta结构与设计一致，http保持独立结构；公共CountByKey/DailyCount/DurationSummary定义在StatisticsRecords.java而非未创建类型。工作日目标按10-23/26、27/28、29/30、11-02/03安排，实际开始晚于10-23则顺延。设计/计划都仍待用户完整审核。

## 2026-10-22查询计划与迁移决策

只读EXPLAIN显示login_session.created_at、forecast_request.created_at、task_attempt.started_at没有可用前导索引，当前原库分别9、1、126行，采用全扫描。本片维持无Schema迁移和5秒只读事务超时，不宣称大数据性能通过。扩容前单独规划并测试登录(created_at,user_id)、预测(created_at,state,model_id)、尝试(started_at,task_id)及注册日期索引；先量测写放大/EXPLAIN/延迟，再执行迁移与回退，不在真实库临时加索引。存量全量计数仍可能扫描，日期索引不能替代容量验证。

2026-10-22 16:05追加：修复后982/277及构建通过，六组件已部署，七应用/注册/资源/原版本/原开关核对通过；Step6、7待用户登录后的页面复算与记录关闭，不将Task8标完成。

2026-10-22 16:32追加：管理员页面独立复算、两批只读行数保全、390/1280尺寸、Tab/ShiftTab/Enter/方向键及日期草稿不触发查询均PASS。Step6仅剩普通账号在生产网关/http与直连/users拒绝访问人工复查，用户已收到问题；Task8仍不关闭。

最终关闭：用户报告普通账号生产网关/http与直连/users均拒绝访问，代表角色人工检查通过；其余逐路由权限有隔离自动化证据。SDD009C验收完成，不将此等同FR13全业务完成。
