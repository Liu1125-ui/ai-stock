# SDD-010B 行情管理写操作分期设计与010B1详细设计

日期：2026-10-25。状态：详细设计完成；首轮独立审阅修订及最终一致性复核已完成，独立复核未发现阻碍编写实施计划的实质矛盾。用户本轮要求完成设计，尚不据此宣称实施计划已批准或业务已实现。没有实现、迁移、部署或供应商调用。
依据：E:/aiStock/AGENTS.md、正式工程AGENTS.md、需求正文FR-11/AC-11，以及已验收SDD010A、SDD009B。13项业务、五业务服务、两个角色、任务书开发顺序不变。

## 1. 目标与分期

管理员从/admin/data看到数据覆盖、诊断与更新失败后，可以提交有记录、可追踪的行情重取任务。提交成功仅代表登记任务，不代表数据已更新或质量已通过。

010B1：手动重取已有Baostock RAW行情，覆盖范围沿用当前可用头的起始日期至明确目标日。复用MARKET_REFRESH、原执行器、接入校验和版本发布；保留操作记录和失败结果。

010B2：标记版本不可用、保护依赖该版本的新预测及相关新分析，并完成不可用后的安全重取。还须考虑历史结果的状态提示及缓存输入；不能简单修改status就宣称已完成。该阶段仍在FR-11待办，需另写可审核的详细设计，不以010B1替代或删除。

任意历史起点补取、从无版本初始化、跨来源自动切换、批量更新、直接编辑价格、强制发布和版本回滚不在010B1。用户要求的手动补取在本片先交付已有覆盖范围内的重取；更早历史扩展应保留待办，不能称全范围补取已完成。

## 2. 已核对的实现事实

- RefreshService从RefreshRepository.head取得AVAILABLE的Baostock RAW头，以coverage_start为采集起点。目标日期必须不早于起点，跨度不得超过现有1500日限制；此限制不是本片新增的永久规则。
- 原刷新检查整个窗口的随附日历与开市日日线、OHLC及候选来源，失败保留旧头。已有历史返回空不能当更新成功。
- MarketVersionRepository.publish检查expectedRevision、拒绝覆盖终点倒退，按可用内容哈希复用版本；发布与RAW修订事件在行情库同事务内完成。相同内容可能成功但不产生新版本。
- TaskRepository.create在调度库按幂等键登记MARKET_REFRESH。现有每日计划、重试、取消和核对状态不得绕开。
- ForecastInputRepository已有RAW状态检查，但010B2仍须核对已准备输入、跨服务在途推理和其他依赖；仅新增一个UPDATE不可满足AC-11。
- 正式刷新当前仅Baostock。管理列表存在Tushare记录不代表支持其在线重取或已有访问权限。

## 3. 方案选择

不在浏览器直接代理/internal接口；不在行情库事务中跨服务创建调度记录；不另造同步更新执行器。

采用调度服务拥有管理员提交记录和任务，在同一调度库事务内登记操作与任务。执行时复用行情服务的校验、收集、发布链路。行情服务提供一个仅内部可访问、无副作用的重取预检接口，让调度服务通过服务凭证获取事实，不读取其他数据库。

预检与任务登记之间不存在分布式事务。任务保存的是已核对请求的约束；真正执行前必须再次核对，条件变化可失败，不以预检保证最终成功。

## 4. 页面及用户行为

/admin/data在Baostock RAW且已有可用真实头的行增加“重新获取行情”。详情中也提供相同入口，历史版本详情明确此次更新针对当前来源头，不是编辑选中历史版本。

点击先读取预检，展示股票、来源、RAW、基准版本/修订号、实际覆盖起点、默认目标日、预计采集范围与“将访问数据源”。管理员可把目标日调到允许范围内；不得小于当前coverage_end，不得超过服务端上海时间的采集截止日。默认截止日沿用20时策略：20时之前为昨日，之后为今日；这是自然日边界，不推断今日一定交易或一定已有最终数据。

若当前coverage_end超过截止日，显示暂不可重取，不静默截断。窗口仍须通过原1500日及源数据完整性检查；不承诺任意股票都能通过现有链路。

提交前展示固定原因选项：RECHECK_DATA、RECOVER_UPDATE。提示与已有每日任务可能存在覆盖，但本入口不会新建周期计划。窗口大小和冷却等在服务端集中配置，前端从预检获得。

操作完成显示操作编号、任务编号及“已排队/已关联进行中任务/拒绝原因”；跳转现有/admin/tasks查看运行、尝试、错误及后续重试取消。原任务操作条件和开关继续生效。页面保留本次operationId，网络超时先查原请求，不自动生成新编号重发。

刷新列表和操作回执均为只读。无自动轮询、无打开页面即访问供应商、无点击两次产生两次任务。切换股票时清除编辑草稿，但尚未确定结果的操作保留可恢复入口及原股票标识。401/403清空私人操作数据，迟到响应不能覆盖新选中股票。

## 5. 预检契约

GET /api/v1/admin/market-data/{instrumentId}/refresh-options，无参数。网关与调度服务每次权威验证ADMIN，返回统一{code,message,data,traceId}、no-store。

Data：queriedAt、actionsEnabled、allowed、reasonCode、instrumentId、provider=baostock、priceBasis=RAW、baseVersionId、headRevision（十进制字符串）、coverageStart/End、minTargetDate、maxTargetDate、defaultTargetDate、cooldownUntil、activeTaskId、policyVersion。

调度服务通过新内部GET /internal/v1/market/admin-refresh-context/{instrumentId}读取非demo股票的Baostock RAW头、关联版本和范围，严格检查版本归属及状态；不得调用会写回执的lookup。仅服务凭证可用，不由网关公开。不存在404、不适用返回结构化禁止原因，依赖不可用503，均不伪造可用状态。日期、版本缺失或损坏默认禁止。

allowed不等于提交承诺；写开关默认关闭，worker关闭也禁止新建任务，但仍可读已有操作回执。activeTaskId仅指本功能登记且当前尚未终结的同股票任务，不能宣称列出全部自动任务。

## 6. 提交、回执与并发

POST /api/v1/admin/market-data/{instrumentId}/refresh-operations
正文仅operationId(UUID)、baseVersionId(UUID)、expectedHeadRevision(非负十进制字符串)、targetDate(严格ISO日期)、reasonCode。拒绝查询参数、未知/重复字段、超长正文；provider和actor均不由浏览器设置。

GET /api/v1/admin/market-data/refresh-operations/{operationId}只读已登记命令及本库任务摘要。所有接口限ADMIN，禁止匿名和USER直连及网关访问。管理员操作记录可供其他管理员查看，但同operationId的提交重放仍绑定原actor，不允许替另一个人提交。

原子流程：先解析身份/校验格式；先查同编号已有终态回执，匹配actor/股票/正文哈希即返回，不再要求旧预检仍有效；不匹配409且不泄漏原正文。新编号才调用内部预检，检查请求与当前头、日期和策略一致。

调度库事务锁定每股票的管理员刷新守卫行；同编号唯一键串行化，统一锁顺序为守卫行→操作→任务。锁内再次核对本库活动关联、冷却和worker配置。跨服务预检不在持锁事务内执行。

如守卫已有非终结任务：新操作必须与该任务冻结的baseVersionId、revision、targetDate一致，才记录ATTACHED并关联已有任务；内容不一致记录REJECTED/ACTIVE_REFRESH_EXISTS。不得改原任务参数。即使旧任务已成功，同编号重放仍返回原回执，不能另建。

若无活动任务且满足冷却，使用命名空间admin-market-refresh-{operationId}登记MARKET_REFRESH及冻结上下文，再保存QUEUED操作结果与守卫关联，同一事务提交。TaskRepository需提供复用同一事务的创建方法，不能以两个独立提交伪装原子性。已有自动任务不纳入新守卫；发生竞争由行情头修订和刷新租约保护，不声称全系统一次采集。

有效业务拒绝保存REJECTED；非法请求/无权限/不存在对象不建立业务回执。SQL异常整笔回滚，返回503并要求查询原编号。GET找不到只能说明当前没有已提交记录，不保证另一请求没有在途事务；若需重发仍用同编号和原正文。

新建成功或成功人工重试后，默认同股票60秒冷却，由最后一次本功能成功人工准入时间计算；ATTACHED、拒绝和同编号重放不延长。人工重试还须满足SDD009B原有同任务冷却，两者同时满足。冷却不替代供应商限频、每轮任务自动尝试上限及既有重试规则。

## 7. 执行时约束与恢复

新增管理员任务上下文由调度库拥有，包含taskId、baseVersionId、headRevision、coverageStart、targetDate及policyVersion。旧自动任务无该上下文，保持原行为。新任务派发时向行情刷新契约传递可选受控约束，只允许内部服务设置；旧调用兼容。

RefreshService在访问供应商之前检查当前头仍符合冻结版本、修订及起点；不符返回明确ADMIN_REFRESH_CONTEXT_CHANGED，不隐式换范围。发布时继续检查原expectedRevision；自动更新先提交则人工任务失败并保留原版本，管理员需刷新后提交新操作。租约过期、未知执行结果仍按原RECONCILING查同attemptId回执，不直接再采集。

操作结果QUEUED或ATTACHED是提交时回执，不随后改成“更新成功”；GET附带当前任务状态及versionId/errorCode，明确任务可能完成或失败。相同内容成功可引用原版本；不伪造新版本。重试和取消继续由SDD009B条件控制，新增上下文不允许被重试改写。

操作入口关闭不取消已排队任务，也不停止原每日计划。部署前关闭新入口；原任务可保持运行。所有写入者和兼容测试通过后才允许开启。真实验收须明确股票、目标日、最大数据源请求与重试预算；本设计不授予供应商调用额度。

## 8. 存储与安全

调度库新增admin_market_refresh_operation、admin_market_refresh_guard、admin_market_refresh_context。前者保存operationId、actor引用、股票、规范请求哈希、固定原因、原版本修订/目标日、outcome/resultCode、taskId、时间；guard每股票一行保存最近成功人工准入时间和活动taskId；context以taskId唯一保存执行约束。外部用户/行情版本仅引用，不跨库外键或联表，本库task关系可用外键。

迁移号在实施时查看全部现有迁移确定，不凭字符串排序的最后文件决定。字段日期、字符串大小、索引及锁顺序由本设计第11—13节确定，实施计划仅拆分落地步骤；不清库、不修改原价格和历史快照。当前不自动清理操作记录，不宣称防篡改审计。

网关只代理白名单路径；市场只读代理原/market-data子树的catch-all须调整为精确路由，避免新操作误发market或user。共用管理员身份解析、安全Origin/CSRF、严格JSON、白名单错误、不重试写请求、限长响应、no-store和精确OPTIONS。不开内部路径CORS，不输出供应商原文、地址、密钥或用户会话。

## 9. 验收与依赖

先做隔离测试：新建/重放/异actor冲突、并发同股票最多一新建、相同约束ATTACHED、不同约束拒绝、冷却、开关、worker关闭、依赖失败、SQL回滚、提交超时恢复、跨日目标和在途头变化。

验证执行前无效约束不访问供应商，发布竞争不改旧头，缺行/空/错误来源不发布，成功或相同内容复用符合原规则；自动任务和既有管理员重试取消不回归。真实供应商请求为零的fixture可以验证这些分支，不能称真实取数成功。

前端验证重复点击、只读恢复、切换股票/账号、乱序与局部失败、任务链接、宽窄屏横滚及Tab/Enter。后端全部测试、前端测试/类型/构建及独立审查后备份受影响库与镜像，迁移兼容验证后受控部署。回退前关闭入口；旧worker不能处理新上下文时禁止把遗留管理员任务交给旧镜像。

010B1完成不能关闭FR-11：010B2停用与依赖传播、无头初始化、扩展历史等限制继续记录。测试项数不代表覆盖率，CodeArts来源与知网证据不借此关闭。

## 10. 设计者自审

已纠正“任意区间补取可直接复用”“标记状态即完成依赖保护”“排队即成功”“同内容必然新版本”四类过度承诺。采用冻结范围和头版本的保守策略，代价是与自动更新竞争时需要重新审核提交；避免静默更新不同范围。当前仅设计，没有改变代码、数据库或后台开关。
## 11. 独立审阅及修订（2026-10-25）

独立builder check指出：任务A失败后已新建B，再经SDD009B重试A会绕过本片守卫。按以下规则修订，不改变业务范围：

- 所有人工重试入口（含内部TaskRepository.retry调用路径）遇到admin_market_refresh_context所属任务，必须进入统一的管理员行情重试准入逻辑；不能沿旧通用路径跳过守卫。没有本片上下文的旧任务保留原行为。
- 获取股票由不可变context确定，在事务内统一守卫→对应操作记录→任务行的锁顺序。新建与重试不得反序获取锁；只读历史回执重放不获取新准入资格。
- 守卫指向另一非终结任务时拒绝重试A，保留原关联；指向已终结任务时，锁定并核对后可重新占用。若并发存在多个关联任务，按taskId稳定顺序锁定需要检查的任务行。不能因缓存状态误判断任务终结。
- 原SDD009B权限、开关、FAILED、无在途尝试、错误白名单、expectedRevision及任务冷却仍必须通过，同时检查本片股票级冷却。通过后重试A、守卫指向A及更新最近成功人工准入时间同事务提交；失败回滚全部，拒绝仅保存相应任务操作回执。
- 重试不改变baseVersionId、headRevision、coverageStart或targetDate。新头已变化时不通过重试偷换输入；执行约束检查将拒绝旧任务，提示重新预检新建。ADMIN_REFRESH_CONTEXT_CHANGED不加入可恢复错误白名单。
- 完成/取消不必主动释放守卫行；下一次准入在锁内核对关联任务终态后更新，避免worker取得任务锁后反向取得守卫锁。取消与领取仍使用原任务行锁竞争。
- 自动退避不算新的人工准入，但任务保持关联、仍占用活动守卫。未知结果RECONCILING不能视为终结任务。
- 补充隔离测试：A失败→B排队→重试A被拒；B已终结后允许符合全部条件的A重试；重试与新建并发只允许一个成功准入；内部重试不能绕过；重放/ATTACHED不延长冷却；成功重试原子更新守卫与任务，事务失败均回滚。

独立审阅其余部分未提出实施规划前需由用户决定的实质问题。本节为设计修订，不代表代码已实现或通过测试。
## 12. 完整接口和页面恢复规则

以下约定是第4—7节的具体化。首次提交的operationId、baseVersionId必须为规范小写UUID。headRevision为不带正号/前导零的非负十进制字符串，范围0至Java有符号long最大值；0单独合法。日期严格校验真实年月日。JSON正文最大4KiB，Content-Type为application/json；时间戳UTC毫秒ISO8601，界面转上海时区。

### 12.1 预检决策

policyVersion固定为admin-market-refresh-v1，集中参数为采集截止小时20、人工冷却60秒、最大刷新跨度1500日、历史页默认20/最大50、写事务5秒。沿用现有执行器的租约、三次自动尝试及退避，不给管理员另建无限次数策略。参数是本版本默认策略，不是永久业务不变量。

内部context响应仅返回instrumentId、provider、priceBasis、eligible/reasonCode、baseVersionId、headRevision、coverageStart/End、queriedAt，不访问供应商、不修改checked_at或失败字段。读取头与关联版本在market本库只读快照中完成。属性不完整、归属不符、状态未知或非AVAILABLE时eligible=false；不存在或demo统一404。

公开options的日期范围由服务端计算：minTargetDate=coverageEnd；maxTargetDate取上海采集截止自然日与coverageStart.plusDays(1500)较早者。min大于max则不允许。默认目标日=max。每次POST重新按提交时服务端时钟核对，不使用页面的queriedAt充当授权凭证。

写开关关闭、worker关闭、预检不可用、有效范围为空或当前头不适用时allowed=false并给安全原因。若已有活动人工任务：仅在任务上下文与当前头一致且target仍在允许范围时允许关联，默认目标日改为该活动任务targetDate，并显示“同参数将关联原任务”；其他目标提交将被拒绝。若无活动任务则冷却未到期时allowed=false。options是提示，不承诺跨服务原子一致；服务配置应由同版本实例一致部署，不支持滚动混用新旧准入策略。

### 12.2 提交与已保存回执

POST成功新建返回HTTP200/code=OK；data含operationId、instrumentId、actorUserId、outcome=QUEUED、resultCode=REFRESH_TASK_QUEUED、taskId、baseVersionId、headRevision、coverageStart、targetDate、reasonCode、requestedAt、finishedAt、replayed。关联已有任务为outcome=ATTACHED/resultCode=REFRESH_TASK_ATTACHED，仍返回200。replayed是本次响应属性，不反复改写持久回执。

已登记的业务拒绝返回409，统一外层code为安全resultCode，data仍含outcome=REJECTED的回执；taskId只在允许公开的实际关联或冲突对象存在时返回，不填伪造值。REJECTED回执重放仍返回原409和原冻结内容。日期/UUID/未知字段等输入格式错误400不登记；身份401/403不登记；不存在或demo对象404不登记；依赖读取失败/数据库事务失败503不登记成功回执。

REFRESH_ACTIONS_DISABLED、REFRESH_WORKER_DISABLED、REFRESH_CONTEXT_CHANGED、REFRESH_RANGE_UNSUPPORTED、REFRESH_COOLDOWN、ACTIVE_REFRESH_EXISTS是可保存的业务拒绝原因。格式正确且对象存在、完成基本上下文读取的拒绝，在调度库短事务内保存REJECTED；没有完整事实的上游故障不落伪造业务拒绝。权威身份失败绝不写回执。新建时即使写开关关闭也不能阻止已经完成的同编号匹配重放；重放仍要重新验证当前会话身份。

GET按operationId返回queriedAt、receipt和task。receipt是提交时不可变内容；task是在同一调度库只读快照中读取的当前任务摘要（taskId、state、revision、attemptCount、versionId、errorCode、updatedAt）。REJECTED无关联任务则task=null；成功回执关联任务异常丢失时报告TASK_REFERENCE_ERROR，不伪装执行完成。查询404不自动变成新POST。

### 12.3 可发现的操作历史

补充GET /api/v1/admin/market-data/{instrumentId}/refresh-operations，参数仅cursor和size。返回queriedAt、instrumentId、items、nextCursor、hasMore。items包含提交回执及对应当前任务摘要。所有管理员可查，普通用户不可查；不返回用户邮箱、会话、request_hash或内部地址。

按requested_at DESC、operation_id DESC稳定keyset分页，最多size+1行判断是否更多。游标Base64URL严格JSON，最大768字符，绑定v=1、instrumentId、size、第一页upper时间/ID及上一页after时间/ID；时间采用UTC毫秒，UUID严格规范。禁止未知/重复字段和重复参数；游标不是权限凭证。固定upper排除之后新增记录，刷新第一页才看到新操作；任务摘要仍是每次读取时的状态，不承诺跨页任务快照相同。

页面在所选股票下新增“管理员更新记录”折叠区域，手动展开才读取第一页，支持刷新与更多；同参数查询失败保留旧数据及原查询时间并标未刷新，换股票清空旧结果。完成提交后展示回执并刷新当前股票历史，不自动刷新所有行情详情；任务成功后由管理员点击刷新覆盖/版本区域查看新状态。

未确定的请求标识只保存在当前账号的浏览器sessionStorage，内容限operationId、instrumentId和规范请求字段，不保存密码或会话。记录账号绑定，退出清理；重载先查原编号，其他账号不恢复。业务结果已确定后清除pending。查不到且用户明确选择“按原请求重发”时只用原编号/正文；服务端仍幂等，不自动发送。管理员可从服务端操作历史恢复已提交结果，避免依赖浏览器永久保存。

## 13. 三张新增表的字段与一致性

本轮已核对调度迁移最高V9，实施时若未发生其他迁移则新增V10__admin_market_refresh.sql；若序号已被占用，重新确定未用序号并同步记录，绝不覆盖。market新增内部只读context与可选刷新约束，不因本设计新增行情表。

admin_market_refresh_operation：operation_id CHAR(36)主键；actor_user_id CHAR(36)；instrument_id VARCHAR(64)；request_hash CHAR(64)；reason_code VARCHAR(32)；base_version_id CHAR(36)；head_revision BIGINT；coverage_start DATE可空（预检证据不足但可记录拒绝时不补假日期）；target_date DATE；policy_version VARCHAR(64)；outcome VARCHAR(16)；result_code VARCHAR(64)；task_id CHAR(36)可空；requested_at/finished_at DATETIME(3)。除标可空字段外非空。outcome允许PENDING/QUEUED/ATTACHED/REJECTED，PENDING仅事务内暂态不可提交；finished_at在终态非空。索引(instrument_id,requested_at,operation_id)；task_id可建本库外键；外部actor/baseVersion无跨库外键。所有已保存终态不可被worker改写。

admin_market_refresh_guard：instrument_id VARCHAR(64)主键；active_task_id CHAR(36)可空、本库任务引用；last_admitted_at DATETIME(3)可空。不得在业务事务之外预建空行；首次准入时在本次事务中insert-if-absent再FOR UPDATE。守卫是技术并发记录，不要求同库instrument表。已确认对象不存在/非法请求不建守卫行。

admin_market_refresh_context：task_id CHAR(36)主键并本库外键；instrument_id VARCHAR(64)、base_version_id CHAR(36)、head_revision BIGINT、coverage_start DATE、target_date DATE、policy_version VARCHAR(64)、created_at DATETIME(3)，均非空；head_revision>=0、coverage_start<=target_date。创建后不可变；worker必须同时核对context中的股票、target与scheduled_task一致，缺失/不一致默认失败，禁止按自动任务继续。

DDL明确二进制排序规则、非负修订及有限枚举，状态间字段条件由事务代码保证并测回滚。为避免创建PENDING后立刻被CHECK拒绝，不设置“所有行finished_at非空”之类与暂态冲突的约束。

管理员刷新任务以idempotency_key的保留前缀admin-market-refresh-识别应有context；内部通用创建接口必须拒绝外部请求占用该前缀，只有本库原子创建方法可使用。worker看到保留前缀却缺context返回ADMIN_REFRESH_CONTEXT_INVALID并保存失败，不因context丢失退回旧自动刷新行为。已有普通自动任务前缀不变。上下文校验错误与变化错误不加入自动/人工可恢复白名单。

## 14. 回执兼容、锁和部署边界

内部RefreshRequest新增可选adminConstraint对象，全部字段必须齐全：baseVersionId、headRevision、coverageStart、policyVersion；targetDate和instrumentId仍来自原外层请求。约束为空的旧自动刷新保持原语义；非空但损坏不能按空处理。结构合法的新约束先登记原刷新receipt，随后检查本库当前头，再收集供应商数据，可记录业务失败用attemptId查询。缺字段/null修订/非法格式在反序列化阶段400拒绝，不为无法规范哈希的非法正文创建回执；调度侧必须先验证冻结context后派发，禁止把损坏上下文当普通自动任务发送。

RefreshRepository当前用旧RefreshRequest.toString计算request_hash；扩展record后即使新字段为null也会改变toString。因此实现必须明确版本化哈希：无adminConstraint时保留旧四字段生成格式与字节序，不直接使用扩展后record.toString；带约束时使用规范化v2哈希并包含全部约束、lease与原字段。用固定旧请求/已存回执fixture证明重启后仍可识别旧operationId，同编号改约束仍冲突。

初始冷却、重复提交和重试的锁顺序依第6、11节。无活动任务的新建与旧任务重试都先锁同一守卫；guard指向的关联task须FOR UPDATE核对状态，不能依赖事务早期的RR普通SELECT快照。任务本身worker只取task行锁，不反向取guard，故不引入task→guard循环。

新建流程的operationId快路径只读是优化，事务内仍须重查。相同operationId同时针对不同股票竞争，唯一键串行化后冲突方返回409并回滚其临时技术写入，不泄漏原操作。对不存在的guard采用幂等插入；死锁/锁超时整笔回滚映射503，查询原编号，不将异常当业务成功。

部署顺序：备份调度/行情库与受影响旧镜像→写入口保持关闭→迁移调度库→部署支持新约束的market→部署上下文感知scheduler→部署明确路由的gateway/frontend→健康与只读核对。直到新market/scheduler一致兼容后才开启新入口；不得让旧worker领取带context的新任务。实际变更的服务才重建，不借此重部署无关服务。

回退时首先关闭入口，新后台处理完或对可取消任务按已有受控机制取消；有RUNNING/RECONCILING或可恢复但尚未核对的管理员任务时不回退scheduler/market到不理解context的版本。可只回退前端并保留兼容后端。新增表先保留，不自动执行破坏性down迁移。备份不是已经演练恢复，未实测的回退条件继续列限制。

## 15. 验收清单和交付判定

本节规定需完成的验证，不是当前测试成绩：

1. 用户与匿名经网关和调度直连均拒绝写/读操作记录；内部context不能由公开路径访问；身份服务失效时不放行；OPTIONS不登记操作。
2. options与历史GET不写表、不改checked_at、不调用供应商；未配置的来源不显示可重取。
3. 合法单次新建产生一操作、一任务、一context及一守卫关联，同事务回滚证明四者不会半成功；相同操作重放不新增。
4. 两管理员并发同股票同约束最多新建一次，另一次ATTACHED；不同约束拒绝；相同编号异actor或异正文409。
5. A失败后B已排队，再重试A被拒；B终结后满足条件的A可重新占用；内部重试同样不能绕过；自动退避保持占用，RECONCILING不能释放。
6. 日期上下界、1500日边界、跨20时/跨日、冷却及worker/入口关闭明确返回，不偷改目标日期；开关关闭仍可读历史和恢复已完成回执。
7. 执行前头变化或结构合法但不匹配的约束不调用供应商且有可查询失败回执；格式损坏的正文400且不创建回执；收集后头变化不发布；历史版本、日线、预测及解释快照不被改写。
8. 旧无约束请求哈希与已存回执兼容；新请求改任一冻结字段均冲突；请求未知结果仅核对原attemptId，不盲目重采。
9. 原每日更新、预测验证、比较更新及管理员重试/取消完整回归；新增RAW版本继续产生既有修订事件供后续核验，不伪造跨服务立即完成。
10. 页面按钮条件、拒绝及超时恢复、历史翻页、退出/账号切换、迟到响应、窄屏与键盘操作通过；任务排队/执行成功与数据版本是否变化分别显示。
11. 完整自动化回归、独立代码审查、受控部署和真实只读核对是不同证据。真实重取在另行限定股票/窗口/调用与重试预算后验证；没有授权则标记该项未验收，不把隔离测试冒充真实取数。

010B1交付状态分别标记“设计完成”“实现并通过隔离测试”“已部署”“真实重取已验收”，不合并为笼统完成。FR-11仍需010B2及原待办，不能提前关闭。

## 16. 后续实施边界

下一份实施计划按契约/存储与准入→执行约束与回执兼容→重试互锁→接口安全/网关→页面/操作历史→完整测试审查→受控部署拆分。每步先测试再实现，代码目录按既有前后端边界附README，不创建空类或新微服务。

本次仅完成设计资料。010B2应在010B1基础上另行明确版本停用的生效点、在途推理、现有输入状态及历史提示，不以本文件中分期描述当010B2已完成详细设计。无头初始化和任意更早历史范围继续保留待办。
### 最终核对记录

2026-10-25独立复核确认：第11—16节已补齐重试与守卫互锁、原编号恢复、冻结上下文缺失拒绝、旧请求哈希兼容和部署次序，未提出仍阻碍实施计划编写的实质矛盾。

守卫的“可释放终结任务”明确采用白名单SUCCEEDED、EMPTY、SKIPPED、FAILED、CANCELLED，且current_attempt/lease_until均空、没有RUNNING或UNKNOWN尝试；任何未知状态、任务关联损坏或残留在途证据默认不释放，返回需核对状态，不把记录异常当任务结束。普通只读历史仍可显示已保存安全事实。

本轮验证范围是需求/源码对照与文档一致性，不包含测试成绩、真实供应商取数或生产验收。下一步为基于本文件编写实施计划；010B2详细设计及其他原待办继续保留。