# SDD-009C 管理员运营统计详细设计

日期：2026-10-22。状态：用户于2026-10-22批准按设计与计划进入实施；实现/验证/部署进度分开记录。业务范围沿用FR-13，不新增业务、角色、数据库或微服务。SDD-009B真实重试已验收，真实取消等待自然任务，独立保留待办。

## 1. 目标和依据

管理员能看到用户规模与登录情况、预测业务请求与模型版本使用、三类后台任务状态与耗时、当前行情更新状态，以及网关响应耗时。每个数字说明数据来源、统计区间和采集限制，刷新页面只查询，不启动预测、取数、模型、邮件或任务。

依据需求分析V1.9的FR-13/AC-13、五服务数据归属规定及导师的用户活跃度、预测调用量、模型使用占比、接口响应耗时要求。保持任务书开发顺序。使用现有Vue3/TypeScript/Pinia/ECharts、Spring Boot/JdbcTemplate/MySQL/Feign/Nacos，不引入Redis、消息队列、Prometheus或新的数据仓库作为本片依赖。MyBatis-Plus持仓CRUD保持不动。

本次默认选择由实施者提出，供用户审核：日期默认最近7个上海自然日，支持今天/7日/30日/自选最多90日；统计普通用户和管理员的总数及角色分布，不另建第三角色；接口耗时先展示当前网关进程观测，明确不具备重启前历史。

## 2. 实际数据审查与关键选择

MS-01已有user_account、login_session。SessionService仅成功登录时创建会话并写last_active_at，后者不是每次使用记录。现代码未发现会话删除流程；以login_session.created_at统计成功登录次数与去重登录用户数，可包含已注销、已过期或被撤销的历史会话，因为当时确实成功登录。不得只数当前有效会话，不将此指标标成DAU/在线人数。将来新增会话清理时必须先设计独立登录事件存档，不悄然改变口径。

MS-03 forecast_request保存唯一prediction_id及(user_id,operation_id)唯一约束，state为RUNNING/SUCCEEDED/FAILED，含model_id、hidden、created_at，没有可靠finished_at。可准确统计已受理预测业务请求；不能将其当HTTP请求、算法实际推理次数或云API账单。不得凭created_at和expires_at推算推理耗时。

MS-04 scheduled_task/task_attempt已有任务创建时间、尝试起止时间、状态和原序号。可分别统计任务与执行尝试，不能把重试次数当成新增任务。三类任务仅MARKET_REFRESH/PREDICTION_VERIFY/COMPARISON_UPDATE；预警及未来训练保持独立待办。

MS-02 instrument_data_head关联market_data_version，含checked_at、last_error_code、published_at、fetched_at、coverage_end。以非demo股票为基础只读关联当前发布头，无头也显示缺失，不统计所有历史版本为当前覆盖股票数，不将检查时间当行情日期。

网关有Actuator依赖但仅开放health/info，未发现可直接使用的历史接口统计。选择有界、无个人信息的进程内完成请求计时，不开放/actuator/metrics，不额外持久化或声称历史P95。

备选方案：跨库大SQL违反数据归属，不采用；新增集中事件仓库、定时采集及跨实例归并会扩大本片，留给确有长期监控需求时；本片采用服务自有聚合接口，前端并发读取，局部失败局部降级。

## 3. 公共查询契约

四个业务分区使用GET /api/v1/admin/statistics/{users|predictions|tasks|market}?from=YYYY-MM-DD&to=YYYY-MM-DD。from/to同时提供或同时省略，严格校验日期、重复/未知参数、from<=to、to不晚于上海今天、含首尾天数<=90。默认7日含今天，服务内转为上海起点当天00:00至to次日00:00的UTC半开区间。业务数据按各自明示时间字段筛选。

统计数据类型：count均为非负十进制字符串，防止JS大整数精度损失；均值、比例与毫秒值为定点十进制字符串或null，不返回NaN/Infinity。比例由后台计算，百分数保留2位；分母0返回null，前端显示“暂无样本”。计数为0是正常数据，服务不可用不是0。

公共Java StatisticsWindow(LocalDate from,LocalDate to,Instant startInclusive,Instant endExclusive)，static parse(MultiValueMap<String,String> query,Clock clock)。四业务分区返回{meta:{window:{from,to,timeZone:"Asia/Shanghai"},queriedAt,definitionVersion:"admin-statistics-v1"},...sectionData}。http为独立进程契约，不伪造日期meta。错误STATISTICS_QUERY_INVALID/400、AUTH_REQUIRED/401、ACCESS_DENIED/403、USER_SERVICE_UNAVAILABLE或STATISTICS_UNAVAILABLE/503。统一{code,message,data,traceId}及Cache-Control:no-store。

业务服务使用本库只读REPEATABLE_READ事务，使分区内部总数、分组、趋势一致；不存在跨服务同一时刻快照，页面显示各分区queriedAt。无全局总成功率。上限、默认日期属于版本策略，正确性与权限要求不随配置放宽。

## 4. 用户统计口径

UsersSummary：totalUsers、enabledUsers、disabledUsers、roleCounts[{role,count}]为查询时当前存量，不按选择日期截断；registrations为窗口内user_account.created_at数量，registrationDaily[{date,count}]按上海日补零。

successfulLogins为窗口内login_session.created_at行数；distinctLoginUsers为该窗口COUNT(DISTINCT user_id)，loginDaily[{date,successfulLogins,distinctUsers}]分别按日计算。窗口去重人数不是每日去重人数相加。包含当前被禁用或后来改角色的账号历史登录，因为事件发生时成功；不推导历史角色。此分区仅返回聚合，不返回邮箱、昵称、userId、sessionId或任何凭证。

不新增业务写入和登录埋点，不用访问统计页或会话resolve更新last_active_at。若后续业务活跃DAU另立事件定义和采集，不以本片登录人数替代。

## 5. 预测与模型统计口径

PredictionsSummary：acceptedRequests、stateCounts[{state,count}]、requestDaily[{date,accepted,succeeded,failed,running}]按forecast_request.created_at落入窗口的请求统计，并以查询时state分类。日趋势是“当日创建请求目前的结果”，不是当日完成量；旧请求从RUNNING变为终态时，同一日期的分类可以变化，页面明确原因。

hidden只影响本人历史展示，不删除系统运营计数。去重重放原operation不新增预测行，刷新与重复读取不增加业务请求数。鉴权/参数失败发生在受理前，不进入此业务计数；HTTP错误在网关指标中另记。

modelUsage[{modelId,artifactId,label,acceptedRequests,successfulRequests,usagePercent}]按原forecast_request.model_id分组，分母为窗口全部acceptedRequests；artifactId来自本库model_version安全字段，不能输出manifest/evaluation整段JSON。版本缺失时保留modelId且label显示“历史版本信息暂缺”，不丢行。当前三份候选是同一Transformer结构的种子版本，不标成三种算法。分组按数量降序、modelId稳定排序；无固定TopN截断损失分母。

不新增MaaS调用量、token消耗、费用和推理耗时估算，不把模型占比当模型准确率。

## 6. 任务与耗时统计口径

TasksSummary：taskTotal、taskStateCounts、taskTypeCounts、taskDaily按scheduled_task.created_at窗口与三类task_type筛选；运行中与结果核对中独立显示，不并入失败或成功。SKIPPED/EMPTY/CANCELLED保留各自类别。

attemptTotal、attemptStateCounts按task_attempt.started_at在窗口内且归属三类任务筛选，允许归属任务创建于窗口之前。failureAttempts包含原失败尝试；人工重试不能覆盖这些行。两个总数的时间队列不同，不要求attemptTotal=taskTotal。

attemptDuration:{sampleCount,excludedInvalidCount,meanMs}只纳入尝试开始在窗口内、已结束且finished_at>=started_at的行，真实0ms合法，负值不归0。started_at当前Schema为NOT NULL；如后续损坏/兼容来源缺失起点，因无法归属窗口，不进入窗口计数或excludedInvalidCount，不按结束时间/任务日期猜归属。已有窗口内起点而未结束的另列unfinishedAttempts。firstQueueWait:{sampleCount,excludedInvalidCount,meanMs}只计算首次ordinal=1尝试开始减scheduled_task.created_at，按首次尝试started_at窗口；它是“首次派发等待”，不推断后续自动退避/人工重试的排队时间。task总体耗时、算法计算时长、训练时长不由此推算。

展示link到现有任务页时只带现有页面支持的参数；若任务页不支持参数初始化，先普通导航，不提供无效筛选链接。

## 7. 行情当前状态

MarketSummary以全部非demo_only股票为基础LEFT JOIN当前头和版本；无头股票生成一条缺失行，也参与分页，provider/priceBasis/versionId/status等为null。列表按instrumentId/provider/priceBasis稳定排序，null固定排前，分页默认20/max50，额外参数page/size仅market分区允许，page正整数上限10000，size1..50。返回availableHeads、instrumentCount、items[{instrumentId,provider,priceBasis,versionId,status,quality,checkedAt,fetchedAt,publishedAt,coverageEnd,lastErrorCode}]与hasMore，不能访问原始价格/持仓详情。

当前状态不套日期筛选：返回window仅为全页选择，额外scope:"CURRENT_HEADS"解释。availableHeads仅AVAILABLE头数量，instrumentCount为有至少一个AVAILABLE头的去重股票数。无头/无版本明确缺失，不以0日期填充。停牌/非交易日不据自然日差直接标“过期”；当前没有独立SLA先展示真实日期不下错误结论。禁止统计GET触发行情补取或自动切换数据源。

## 8. 网关耗时与错误率

GET /api/v1/admin/statistics/http不接受任何参数，单独返回scope:"CURRENT_GATEWAY_PROCESS"、processStartedAt、observedSince、queriedAt、definitionVersion、groups[{group,completedRequests,http4xx,http5xx,totalDurationMs,meanDurationMs,errorPercent}]。平均数=实际完成请求耗时总和/完成数；errorPercent=(4xx+5xx)/完成数，不等于业务预测失败率。0样本均值/比例null。

新增Servlet OncePerRequestFilter计时，System.nanoTime单调时钟，finally记录一次。只采集正常分派同步MVC完成响应，不含ASYNC/ERROR重复分派；异常由API错误处理结束后以最终状态计数，无法得到最终状态的样本单独droppedSamples，不把throw自动当200。本片MVC不新增异步业务处理。不得把客户端断开当完整成功结果。

固定八组USER、MARKET、PREDICTION、ANALYSIS、COMPARISON、PORTFOLIO、NOTIFICATION、ADMIN；按已匹配的MVC路由模板映射，未知路由不产生新标签。不保存URL原值、query、traceId、用户身份、cookie、IP或正文。统计接口自身、health/info、静态资源、internal路径与未匹配404不纳入；所有统计页刷新不会给被观察业务凭空增加请求。

使用LongAdder/等价并发累加，但读取应提供同一个快照锁或可验证的原子聚合，保证0<=错误数<=总数及均值分母一致。实例启动清零，如重启/滚动更新导致processStartedAt变化，旧值不得继续拼接。无多实例全局归并，不提供伪P95；标签明确“网关响应，包含等待下游；不等于算法计算时间”。

## 9. 服务边界与安全

MS-01只读user_account/login_session；MS-03只读forecast_request/model_version；MS-04只读scheduled_task/task_attempt；MS-02只读instrument/current head/version；网关仅保存上述进程计时，不访问任何业务数据库。MS-05本片不改，无通知统计扩展。没有跨库SQL/写入或新增外部HTTP。

四个分区接口在所属服务均独立校验服务器权威ADMIN身份，即使绕过网关直连也应401/403。user沿用SecurityConfiguration；prediction显式追加精确管理员路径；scheduler扩展AdminTaskAccessFilter保护范围且不放宽旧路径；market新增精确管理员只读过滤器。网关本地/http使用User服务resolve权威身份，不能信任X-User/Role头或前端角色。

重复同名Cookie、多Cookie头、身份服务响应畸形/负credentialVersion及角色非ADMIN均失败关闭；credentialVersion必须存在、非null且为JSON原生非负long整数（0合法），拒绝字符串/小数/布尔隐式转换与溢出；需要对新统计路径补测试，不能以旧模块已经校验替代。内部session解析凭证不输出日志。OPTIONS仅精确预检、无业务计数和取数。

网关使用专用AdminStatisticsApiProxyController处理五条精确路径，UserApiProxyController明确排除此子树以免落入通用/admin/**映射。复用各服务Feign/Nacos客户端，不新增裸Client破坏服务发现；不将超时映射为账号失效，不转发原始异常和SQL。所有统计限GET，非允许路径/方法404，错误data=null。

## 10. 页面和故障行为

新增/admin/statistics，仅ADMIN导航及路由可达。分为日期筛选、用户、预测与模型、任务、行情状态、网关响应六部分；小卡片配折线/条形图，保留纯文字/表格可读替代。饼图不是必需，模型仅一版本时也正常。

用户点击应用日期后同时发出四个分区查询，http独立查询不附日期；Promise.allSettled逐块渲染。最多一次批次在途，同批局部失败保留同查询旧值并标“尚未刷新”及原时间；切换日期必须清空旧日期数据，不能展示旧值配新日期。http可保留同process旧数据且标旧时间。401/403立即清空全部统计并走现有鉴权边界，503只影响对应分区。

每个区块展示自己的统计口径、查询时间及状态；页面刷新仅GET，不启用后台自动轮询。日期更新、退出/降权或离页使用epoch拒绝迟到响应。金额、持仓、问答正文不在后台统计出现。窄屏、键盘、loading/empty/error均验收。

## 11. 验收与交付边界

先隔离MySQL测试已知样本：上海午夜、相同账号跨天登录、注销后仍计当时登录、隐藏/去重预测、历史模型、任务重试不抹失败、跨日尝试和耗时负值。使用mock/虚拟时钟验证网关并发计数及重启清零，不为凑数请求付费API。

生产首验只读：七应用健康、各分区权限/结构/日期、已有行数独立复算、刷新不新增业务行、前端窄屏/键盘和管理员/普通用户隔离。不得以同一实现SQL对照自己充当独立算法复算。允许正常登录记录造成登录指标变化，记录登录发生的时间。

预计8个任务、约6—8个有效工作日，人工反馈和环境故障另外留缓冲，2026-10-23开始的目标完成窗口为10-30至11-03，属于安排非保证。先完成设计审核，再逐项实施、小步提交、最终一次独立代码审查、修复、备份与受控部署。无remote不push。无需原数据库新迁移；若EXPLAIN证明必须加索引，单独列迁移影响供本片计划修订，不能临场覆盖现有迁移。

未完成的DAU、历史监控/P95、实际推理/云账单、训练/周更新/预警适配、FR-11完整行情管理、真实取消验收、质量门及CodeArts证据继续保留，不把本片统计页当全系统完成。说明书不宣称预测收益或准确性提升。

## 12. 独立可实施性检查与处置（2026-10-22）

brainstorming builder-check由fresh子审阅者只读完成，提出两项实质问题，均由现有设计目标及实施者默认选择解决，不要求用户额外逐项回答：

- 无数据头股票是否显示：选择展示一条缺失行并参与分页，以instrument为查询基础，非demo过滤；这能让管理员看见尚无数据的对象，代价是列表不全是已有head。availableHeads/instrumentCount仍只数真实可用头/有可用头的股票。
- 起点缺失如何归日：当前Schema NOT NULL；若遇损坏或未来兼容来源，缺少started_at不能放入任何窗口，不猜替代时间、不并入窗口异常分母。窗口内负耗时才计excludedInvalidCount，未结束独立统计。首次派发等待采用同一不猜时间规则。

元数据位置统一为四业务分区meta对象、http独立进程契约。设计/计划字段已同步；以上为实施者提案，用户随后已批准进入实施。独立检查不是功能测试或代码完成证明。

## 2026-10-22实施验收状态

用户批准后八任务已实现并验收，修复后982项后端/277项前端与构建通过，一次审查重要问题一轮修复；备份部署、管理员独立复算/只读交互和用户报告普通角色代表路由拒绝访问通过。核验记录docs/verification-sdd009c.md，FR13仍部分完成，限制及原待办保留。
