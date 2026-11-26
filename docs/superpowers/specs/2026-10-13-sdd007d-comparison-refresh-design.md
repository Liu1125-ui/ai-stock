# SDD007D 股票对比证据持续更新设计

日期：2026-10-13（Asia/Shanghai）。状态：设计已完成自审，用户2026-10-13明确确认并要求编写实施计划；尚未编写产品代码、部署或新增外部采集。承接SDD007A/B/C，不增加13项业务或改变五服务、双角色。用户已确认目标：先核对与设计，再实施新对比用新证据、旧结果不变、失败保留的更新链路。

## 1. 目标和成功标准

当前三只股票的RAW行情已到2026-10-13，而独立比较证据仍为2025-10-09至2026-10-09。比较指标及AI解释正确引用旧快照，但还不能随新交易日持续更新。本片将已有人工“RAW快照核对→同源RAW/后复权/日历采集→MS02校验发布”的流程接入MS04持久任务，形成每日可恢复的更新。

成功意味着：符合收盘和日历边界的新证据可发布；新建2—3股/3M、6M、1Y比较选取最新共同可用范围；旧比较、预测、解释、问答、账号和预警记录保持原ID与内容；失败或结果未知可追溯，不假报最新，不盲目重采。它不训练模型，不自动生成AI或发送邮件，不承诺未来供应商始终可用。

## 2. 已核对的现状

- 当前分支codex/sdd004-market-refresh，最近验收提交d44300c；只有既有未跟踪.superpowers资料，不能清理或混入提交。
- Python app/comparison/provider.py的ComparisonSource复用ForecastSource，一个SDK会话查RAW adjustflag=3、后复权adjustflag=1、交易日历三类数据；25秒子进程、2MiB输出、4096字节请求、全局_COLLECT_SLOT，当前上限一年窗口。
- MS02 ComparisonEvidenceService/Repository/Validator发布不可变版本，比较候选中的rawClose、volumeShares、tradeStatus逐日核对绑定RAW版本；缺失/停牌显式存储，不补零。已有发布操作幂等，但没有采集执行回执、租约与自动任务恢复。
- MS02 ComparisonEvidenceOptions及MS03 ComparisonService已经支持最新共同截止、覆盖查询与固定证据ID；前端显示证据截止、采集时间、RAW已推进而证据未同步。刷新已有结果不重算，也不自动调用AI。
- MS04 TaskRepository/TaskWorker已具备持久任务、尝试、90秒行情租约、RECONCILING/UNKNOWN、原操作回执核对及至多三次重试。不能把新类型加入创建却忘记工作器、恢复分支或失败策略。
- MS02 AlertBoundaryService.resolve拒绝末行tradeStatus=0，不能直接复用于允许停牌证据的比较任务。应复用日历服务与时间原则，建立比较自己的就绪判定。
- 当前市场Flyway V11、调度V6；预测比较1份、AI操作2份、成功解释1份。三股RAW日期至10月13日，比较日期至10月9日；独立日历覆盖7月1日至11月9日、采集于10月10日。此为只读基线，不是新增采集验收。详细记录runtime/sdd007d-design-baseline.json。

## 3. 方案选择

采用：复用MS04现有持久任务/尝试，增加COMPARISON_UPDATE类型及仅该类型的固定上下文表；MS02负责采集执行回执、校验与原子发布，Python仍只产生候选。

相比直接给PowerShell/Python脚本设定时器，本方案能恢复未知结果、限制过期发布、留存任务记录，且不产生另一套调度来源。相比只抓新增几天后拼接，它每次核验一年同源窗口，能发现窗口内配对差异，不把复权历史修订问题隐藏在拼接中。当前三股数据量适合先验证全窗口方案；以后优化增量必须单独验证口径与修订，不直接替换当前策略。

## 4. 当前版本策略和正确性不变量

当前策略：现有三股SH600030、SH600276、SZ000002；北京时间20:00后检查，每60秒只读扫描；单股每天一个逻辑更新周期，跨度为targetDate.minusYears(1)至targetDate；串行工作器、每任务最多三次尝试。证券列表、扫描间隔、重试间隔和启用开关为配置策略；增加股票不改业务定义。扩容必须评估供应商预算与历史覆盖，不承诺第一版更新全A股。

不变量：同一baostock来源、BACKWARD口径及现有definition；绑定不可变RAW和来源hash；不使用未完成交易日；每次采集/发布具有固定尝试编号和不可续期租约；相同编号不同参数409；失败不改旧版本；不跨库读写或建跨库外键；内部接口仅服务认证、网关不公开；任何页面/状态GET不采集、不更新快照、不调用MaaS。

## 5. 就绪判定与每日任务

MS02新增只读比较更新就绪接口，按配置证券返回各自状态。asOf取服务Clock，时区Asia/Shanghai，截止规则与当前系统20:00一致。只在20:00后建立当日自动周期；跨日只补当前到期周期，不自动回补每个漏运行自然日。

就绪判定顺序：

1. 证券真实、ACTIVE，有AVAILABLE的baostock RAW版本；读取其不可变ID/contentHash及完整窗口。
2. 使用CalendarService.covering在有效TTL内覆盖RAW最近日及收盘截止；只读验证日历日期连续、来源/hash与未来时间约束。没有有效日历则CALENDAR_UNAVAILABLE，交给已有日历更新链路处理，本片不另起日历采集器，不用周一到周五推测。
3. targetDate为日历中不晚于截止的最后开放日。RAW窗口必须覆盖目标及一年范围的连续日历，并包含目标日行情行；tradeStatus=0允许，不能因停牌把就绪误判成非法股票。RAW未到目标则RAW_LAGGING，不采集，不将旧日期冒称当前。
4. 已发布证据覆盖请求一年窗口且截止等于目标时，比较其绑定RAW的窗口投影与当前RAW：日期、rawClose、volumeShares、tradeStatus及窗口内日历一致即CURRENT。只新增周末日历或RAW窗口外变化不导致再次采集；不得只凭RAW版本ID变化判定需更新。旧绑定RAW仍保留以供核对。
5. 无兼容当前证据且RAW窗口足够则READY，输出固定快照；不足一年为INSUFFICIENT_HISTORY，第一版自动采集不悄悄缩短范围；已有人工发布的短期证据及3M/6M比较行为不因此删除。短历史股票的自动更新支持是以后版本策略，不影响现有三股。

MS04读取就绪响应，不读MS02数据库。READY时创建日逻辑任务，业务键由服务端生成comp-update:<cycleDate>:<instrumentId>:v1；cycleDate为实际计划日，不能由客户端传任意key绕开去重。同日重复扫描或重启返回原任务，不替换原上下文，即使当天RAW后来变化。超过原计划日后，未领取的新尝试（含重试）标COMPARISON_CYCLE_EXPIRED而不再采集；已在途的尝试先核对原回执至终态。下一日若仍落后可按新周期捕获最新RAW；这意味着同日任务成功后的源修订不会自动再次采集，限制和日期必须如实说明。

CURRENT或RAW_LAGGING等状态不创建外部采集尝试。失败任务同日不由计划自动重置三次计数；第二天的新周期可以继续。普通休市日若数据兼容CURRENT则零外部采集；若仍有旧缺口，最多创建该日一个补最新周期，不声称休市日有新价格。

## 6. 固定上下文、预算与重试

MS04在创建任务的本库同一事务写scheduled_task和新comparison_update_context，后者task_id为本库FK，字段含cycleDate/instrumentId/rawVersionId/rawContentHash/rangeStart/targetDate/definition/planAsOf。上下文不可变，不存账户密码、供应商凭据或模型内容。增加上下文表而不把一年候选JSON塞进通用任务表；现有MARKET_REFRESH/PREDICTION_VERIFY记录和API保持兼容。

每次claim使用现有attemptId和90秒租约，POST请求中的operationId就是该attemptId。新类型的临时失败退避15分钟、30分钟，最多三次；现有类型60秒/120秒规则不改。SOURCE_BUSY/SOURCE_TIMEOUT/SOURCE_UNAVAILABLE/ATTEMPT_EXPIRED可在明确终态后有限重试；数据不配对、范围/日历不符或参数非法终态停止。SOURCE_BUSY不能循环立即重发。服务器或网络结果未知进入RECONCILING，先GET原回执，未到期/不可判断时不新建尝试。

预算针对本自动调度管道、以计划日建立的逻辑周期计数：正常三股各一组，共9次数据查询（RAW、后复权、日历各一次）；若三股都用满三次尝试，最多27次数据查询，另有每实际SDK会话login/logout。请求跨午夜仍计入原逻辑周期，这不是供应商账单按自然日计费次数的严格证明。失败可能在查询完成前退出，27是保守上界。内部状态查询和同终态操作重放不计供应商请求。本轮设计阶段零请求；真实首验最多三股各一组9次数据查询，无真实自动重试。

新类型通过既有人工retry入口也不得把单个日周期总尝试突破三次；比较类型需按task_attempt累计数量校验，不沿用重置attempt_count就无限扩额。预算耗尽保留失败，等待下一周期。此规则是当前自动运维策略，可在后续书面调整，不写成股票业务永不可变约束。

## 7. MS02执行与发布原子性

新增ComparisonRefreshService/Repository/Collector，复用ComparisonSource、ComparisonEvidenceValidator和版本发布，不改变原手工候选发布契约。

1. 服务身份与4096字节上限先于JSON处理；校验严格字段、UUID、范围、definition、leaseUntil（上限120秒，当前MS04发90秒，毫秒精度）。查询原回执优先；相同参数终态或过期状态返回原结果，不能再采集。
2. 短事务登记RUNNING回执及固定参数hash，提交后才调用Python；不持有数据库锁跨供应商网络。相同ID正在运行只返回RUNNING/冲突，不启动第二采集。
3. 根据固定rawVersionId读原版本，不用rawWindow现有“最新头”方法重新选源。核对股票/来源/RAW/hash/窗口与上下文相符，截止范围已完成。使用既有算法接口采集同范围候选，最多25秒子进程、Java HTTP连接2秒/请求30秒；读取不超过2MiB，拒绝重复/尾随JSON及畸形候选。新接口的失败保持受控业务码。
4. 目标开放日必须在候选中出现（停牌行允许），不能供应商只返回旧行却推进to；其余历史缺失沿用已有MISSING/SUSPENDED事实，不补零、不承诺风险指标一定可算。
5. 发布必须复用原有配对、日历、数值与内容hash校验，写入新证据及成功回执在同一个本库事务。先锁回执，再做全部可能阻塞写入，锁后和最后写入后重新读取Clock校验RUNNING/同参数/未过期；不续租。截止已到则回滚全部新版本/操作映射，保存或派生ATTEMPT_EXPIRED，不能留孤立已发布版本。
6. 接收端宕机，GET回执返回持久成功或失败；RUNNING已过期且无原子成功记录时，只读派生FAILED/ATTEMPT_EXPIRED，不把GET变成重新采集或无条件状态写入。旧工作器在最后租约检查阻断，原编号永远不能恢复成新的采集。
7. 参数hash包含固定上下文和leaseUntil，相同attemptId换RAW/范围/租约409。已有相同contentHash时复用证据，成功回执仍原子关联；失败不写RAW头的last_error_code，避免将比较故障冒充基础行情故障。

## 8. 接口和记录契约

所有JSON业务响应使用{code,message,data,traceId}，错误不泄漏Token、SQL或内部异常。新增内部路由：

- GET /internal/v1/market/comparison-update-options?ids=SH600030,SH600276,SZ000002：1—50个唯一证券，未知/重复query拒绝。data含asOf、definition、items；item含instrumentId/state/reason、rawVersionId/rawContentHash/rangeStart/targetDate，以及已选evidenceId/sourceTo。未就绪字段允许null且有明确reason；不做源请求。
- POST /internal/v1/market/comparison-refresh：operationId、instrumentId、rawVersionId、rawContentHash、start、end、definition、leaseUntil。由调度固定上下文生成。data为operationId/state/versionId/errorCode；state RUNNING/AVAILABLE/FAILED，versionId为比较evidenceId，不是RAW版本。
- GET /internal/v1/market/comparison-refresh/{operationId}：只读核对原尝试，不续租、不重采；未登记404。
- MS04沿用/internal/v1/tasks列表、详情、人工retry/cancel；新增任务类型由专门计划创建，不修改原创建RAW任务的body，也不允许前端直接调用采集接口。TaskWorker的新类型执行/恢复分支使用ComparisonTaskClient，并保留旧分支的响应处理。claim及pendingReceipts必须按当前启用类型筛选：比较工作器关闭时不领取新比较任务，原工作器关闭而比较开启时不领取RAW/验证任务；两者都关闭则不自动执行。不能只给新分支加if而让通用claim提前消费新类型。

调度到MS02的比较客户端独立连接2秒/读取45秒、不重试、不重定向；状态GET3秒。不得将新超时变成所有客户端的全局默认。仅该类型使用比较上下文，执行响应中的versionId明确为evidenceId；RAW/验证任务的versionId语义不变。内部网关路由继续404；前端/API目录中的MaaS请求路由和超时不改。

数据库迁移预计MS02 V12创建comparison_refresh_receipt（operation_id PK、request_hash、固定参数/租约、state、evidence_id本库FK、error_code、created/finished），MS04 V7创建comparison_update_context（task_id本库FK、固定参数）。实施前重新核验末版本，不能覆盖并行新增迁移。跨服务仅保存UUID/hash引用，不建跨库FK。

## 9. 新旧比较与页面行为

成功发布新证据后，新比较选源沿用现有latest/common-end/coverage逻辑。三股更新不同步时取共同可用截止；某股已经滚动一年窗口，若不足覆盖滞后共同区间的起点，需从保留的旧证据选择完整覆盖，不能让最新部分窗口挤掉可用旧证据。必须加入这一混合版本测试。

旧比较result_json/sourceHash、旧AI context/paragraphs和请求ID不改；用户刷新旧链接仍读旧结果。要使用新日期须明确新建对比，再由用户选择是否生成AI。本片不自动重算用户所有历史、不自动追加模型费用。

现有页面继续展示拟比较区间、逐股证据截止/采集时间和与RAW差异；失败仍能查看旧指标/解释。内部任务详情可查看尝试及失败码，本片不增加完整管理员任务页面、不伪装已经实现该业务。

## 10. 配置、部署和真实验证预算

新COMPARISON_UPDATE_PLAN_ENABLED、COMPARISON_UPDATE_WORKER_ENABLED默认false；COMPARISON_UPDATE_INSTRUMENTS默认现有三股，COMPARISON_UPDATE_SCAN_POLL_MS默认60000。现有RAW/日历/预警/邮件/MaaS开关不随本片启停。实现前保留镜像与现用关键计数/hash；本片不迁移预测库，不改变既有模型或13业务。

先在独立MySQL和本地HTTP/SDK完成验证，再关闭新开关部署。首次真实验证先一股一组3次数据查询，通过后才其余两股，全部最多9次，SDK登录/退出另计；任何源错误停止真实扩量，不为了成功连续重复。同一终态编号重放、回执GET与重启恢复不得新增采集。真实费用模型调用0、邮件0。设计中的9次真实数据查询预算需随后续实施计划明确审核，当前没有启动该预算。

回退前先关闭新计划，保留新MS04/MS02处理在途及未知回执；停止新领取后，对QUEUED/RETRY_WAIT的新类型任务受控取消，已在途等待终态/租约核对，确认没有待旧工作器领取或恢复的新类型，再切旧镜像。旧TaskWorker会把COMPARISON_UPDATE视为TASK_TYPE_UNSUPPORTED，不能直接换回旧调度镜像而保留可领取新任务。新增表/证据/任务历史不降库、不删数据；旧Flyway兼容另测，不能启动则保持新版关闭开关前向修复。

确认三股新证据源hash/逐日配对/范围，重新创建一份比较检查日期推进；旧比较及解释hash不变。之后启用三股计划与工作器，连续运行另记录，不能用首次成功保证未来长期可用。后端/算法/前端按受影响范围测试；全量后端和严格前端构建作为最终门禁，Python未修改也需运行相关既有比较契约测试。Maven不并发，无远程不声称push/CI。

## 11. 必须验证的边界

- 19:59/20:00、周末/节假日、闰年一年窗口、日历TTL/覆盖缺口、RAW落后、已到期停牌行、无一年历史：不使用未完成交易日、不虚构新价格。
- 同日多次tick/多实例/重启只一个任务与固定上下文；同编号换参数409，跨日新周期不可覆盖昨日记录；人工retry不得绕过总预算。
- 同来源窗口未变但RAW只扩展非交易日日历应CURRENT、零外部调用；同日源修订不再采集的限制明确，下一周期才处理，不声称全修订自动捕获。
- HTTP未知、保存已成功但响应丢失、查询不可用、恰到期、锁等待跨过期、进程中断：先核对原回执，旧工作器无孤立发布、无重复源请求，GET纯读。
- RAW/后复权配对错、缺目标日、日历冲突、畸形/过大/重复或尾随JSON：失败保留原证据，缺失不补零，数据非法不自动重试。
- 三股部分推进、滚动一年起点与滞后共同范围冲突、已有内容复用、旧用户比较和AI解释保持：新结果引用正确版本，旧结果hash不变。
- 未认证/错误服务身份、公开网关内部访问、两旧任务类型回归、独立超时与采集槽冲突、比较关闭但旧工作器开启、旧工作器关闭但比较开启、旧版本回退遇到新任务：拒绝越界，不扩大业务或自动费用。

## 12. 交付顺序与边界

后续实施计划围绕四个可独立测试的交付：MS02就绪/固定窗口契约；MS02有界执行回执与原子发布；MS04持久调度和恢复；完整回归/受控部署/真实推进与保全。每片先测试后实现、选择性提交，沿用当前会话原生执行方式。

本文件完成的是设计；用户已通过书面设计审核，实施计划已撰写并自审，等待计划审核后编码。自动更新尚未接通。CodeArts真实差异仍待补，当前代码生成者如实记录；不把MyBatis-Plus规划当作已集成。

## 用户书面设计审核
用户已确认本设计。后续实施计划细化真实首验attemptLimit=1（不超过设计最大3），该限制写入固定周期上下文，成功后新周期使用默认3；不会通过真实自动重试突破9次首验预算。该细化是演练保护，不扩充业务。

2026-10-17实施状态：本设计已按确认计划实现、审查修复、部署并通过三股真实首验和页面验收；正常周期启用。实际市场V13（新增激活序号，不改既有V12）、调度V7。长期运行与完整旧镜像回退未验证，详见docs/verification-sdd007d.md。
