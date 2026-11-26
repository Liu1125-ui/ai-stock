# SDD-009B 管理员任务受控重试与取消详细设计

日期：2026-10-20。状态更新2026-10-21：用户已确认优化设计和实施计划；代码、隔离测试、独立审查修复和V9默认关闭部署完成。新页面人工验收已通过，真实重试已验收、入口已恢复关闭，真实取消待自然任务验收，不能以设计确认替代真实操作授权。

## 1. 目标、依据与边界

在已验收的SDD-009A任务工作区中，使管理员能够处理可恢复失败，取消尚未执行的任务，并查看操作记录。成功标准是操作符合任务类型与当前状态、重复提交不重复生效、并发冲突不覆盖后台执行、历史记录可追溯。重新入队不是执行成功，取消不是撤销已经发生的业务行为。

用户明确范围：行情更新MARKET_REFRESH、预测验证PREDICTION_VERIFY、比较更新COMPARISON_UPDATE三类任务；符合条件的FAILED可重试；QUEUED和RETRY_WAIT可取消；RUNNING和RECONCILING禁止操作；保留历史，限制重复提交并记录管理员操作；比较任务不突破原周期与次数预算。

依照E:/aiStock/AGENTS.md、正式工程AGENTS.md、需求正文V1.9、SDD-009A及用户最新确认。13项业务、五个业务服务、ADMIN/USER两角色、任务书开发顺序不变。新增能力属于FR-13增量，不另算业务或微服务。

不包含批量操作、强制终止、暂停每日计划、重跑成功任务、修改原参数、恢复已取消任务、补建比较周期、预警任务适配、训练任务或运营统计。不新发预测、不调用MaaS、不发送邮件。重试之后现有worker可能按原机制访问行情服务或供应商；生产写入验收须单独限定对象与调用预算，不能以本次设计确认代替。

## 2. 现有实现核对与方案选择

TaskRepository已经使用本库短事务及FOR UPDATE领取、完成、重试和取消任务。已有内部POST /internal/v1/tasks/{id}/retry与cancel；它们由服务凭证保护，不适合作为浏览器接口。普通任务内部重试重置attempt_count；比较任务检查同一上海自然日、累计尝试上限与错误白名单。累计task_attempt历史不会因重试删除。租约到期先进入RECONCILING，再查询原尝试回执，不直接再派发。

三个可选方向：

- 直接代理内部retry/cancel：改动少，但缺管理员身份、持久幂等操作及并发版本保护，不采用。
- 在调度服务增加管理员命令层，复用现有worker与本库状态迁移：改动集中，能原子保存操作结果和任务变更，采用。
- 引入独立运维平台、消息命令总线：本片没有跨服务分布式命令需要，增加不必要部署与一致性成本，不采用。

MS-04拥有任务、尝试及新增管理员操作记录。MS-01继续权威解析会话；网关只代理明确公开契约；前端不构造内部服务身份。禁止跨库查询、写入或外键。同步命令只做调度库短事务，不在持锁事务中发起供应商或业务服务HTTP请求。

## 3. 状态和操作规则

### 3.1 重试

必须满足：类型在三类名单内；任务当前FAILED；没有残留current_attempt或lease_until，也没有RUNNING/UNKNOWN尝试；对应worker开启；公开操作开关开启；expectedRevision匹配；原因属于该类型的可恢复白名单。未知、缺失、不匹配或不可恢复原因默认拒绝，不允许仅凭FAILED放行。

白名单沿用已存在的错误语义，按类型收窄：

- 行情更新：SOURCE_TIMEOUT、SOURCE_BUSY、SOURCE_UNAVAILABLE、ATTEMPT_EXPIRED、MARKET_REVISION_CONFLICT、REFRESH_RUNNING。
- 预测验证：SOURCE_TIMEOUT、SOURCE_BUSY、SOURCE_UNAVAILABLE、ATTEMPT_EXPIRED、VERIFICATION_SOURCE_UNAVAILABLE、VERIFICATION_BUDGET_EXCEEDED、REALIZATION_RAW_UNAVAILABLE。
- 比较更新：SOURCE_TIMEOUT、SOURCE_BUSY、SOURCE_UNAVAILABLE、ATTEMPT_EXPIRED。

VERIFICATION_BUDGET_EXCEEDED仅表示允许重新排队尝试，不授予额外下游配额；下游预算约束继续生效。SOURCE_INVALID、权限错误、参数错误、上下文不完整、周期过期和预算耗尽均不可通过此入口绕过。白名单扩展必须增加明确语义与测试，不能按错误字符串前缀猜测。

行情更新/预测验证复用现有人工重试语义：同一task_id重回QUEUED，attempt_count归零，旧task_attempt保留，实际领取时再生成新尝试编号，单轮最多三次自动尝试。清除当前任务的错误及结果引用，next_at设为当前时刻；旧尝试记录及其版本引用不改，不删除已保存行情、预测、验证或分析结果。

这两类任务增加可配置人工重试冷却，默认同一任务两次成功人工重试间隔至少60秒；首个操作不受此限制。冷却只限制人工操作，不改既有自动退避及供应商限频。每次新轮都必须由管理员针对新版本再次确认，不自动连续补轮。

比较更新额外要求context完整、cycle_date等于服务端上海当天、task_attempt累计条数小于原attempt_limit。原窗口、RAW引用、周期、预算均不可修改。人工重试不重置其attempt_count，不增加attempt_limit。比较自动尝试已耗尽时按钮不可用；不能为了让按钮可点而补预算。大多数自动失败任务可能因预算已用完而无法人工重试，这是正确结果。

请求提交后与worker领取前都保留原比较周期/预算校验：例如23:59排队但跨日才领取，应由原worker判定过期，不能依赖页面的旧日期。冷却以本库最近APPLIED重试记录为准，命令重放不重复计算。

### 3.2 取消

仅QUEUED、RETRY_WAIT可取消，且无current_attempt、lease_until和未核对尝试。取消不要求worker开启；全局管理员写入口关闭时仍禁止操作。当前任务转为CANCELLED，不改累计次数、不删除尝试、不生成伪造的取消尝试，也不修改已有版本结果。

RUNNING、RECONCILING、SUCCEEDED、EMPTY、SKIPPED、FAILED、CANCELLED以及未知状态均拒绝新的取消。相同已完成操作编号的重放属于查询原结果，不当作新的取消。

取消只针对这一个task_id；不关闭market_update_plan、比较计划、预测验证扫描或后续日期任务，不回退计划游标、不撤销下游结果。要停止后续计划应另行设计，界面确认时明确说明。

## 4. 并发版本与原子命令

### 4.1 单调版本

scheduled_task新增revision BIGINT NOT NULL DEFAULT 0，CHECK revision>=0。API始终以十进制字符串表达，避免JavaScript大整数丢精度。它是乐观并发版本，不代替任务结果version_id，也不是授权凭证。

所有改变scheduled_task业务状态的UPDATE都要在同一语句revision=revision+1：过期判定、租约转核对、领取、未知标记、完成、自动退避、内部retry/cancel、新管理员命令。幂等创建的ON DUPLICATE KEY UPDATE task_id=task_id不增版本；无变更的读操作不增版本。实现前全工程搜索写入点并核对；并发控制不能只覆盖管理员写入而遗漏worker。

与既有task_attempt的变更保持原任务行锁顺序。历史revision初始化0合法；修改后的所有写入者必须先于写入口启用上线。旧调度镜像不递增revision，因此回退旧镜像时必须关闭管理员写入口；不得让新写入口连接仍运行旧worker的混合版本。

### 4.2 单事务命令流程

1. 校验Origin、JSON格式及大小，权威解析管理员身份；身份服务不可用时拒绝，不能用缓存ADMIN兜底。
2. 校验路径UUID、正文operationId、expectedRevision、reasonCode；拒绝未知字段、重复JSON字段、任何查询参数及过长内容。
3. 在本库事务内以operation_id唯一键登记命令，绑定当前actor_user_id、task_id、action与规范化请求哈希。并发相同编号由唯一键串行化，不进行业务HTTP调用。
4. 同编号同管理员同内容且已有终态：返回已保存回执，不再校验旧版本，不再次改任务。不同管理员或内容复用编号：409 OPERATION_KEY_CONFLICT，不泄露原操作内容。
5. 锁定scheduled_task行，再读取该任务比较上下文、尝试与最近人工重试时间。比对expectedRevision，重新执行全部状态/类型/预算/冷却判断，不信任按钮状态。
6. 允许则原子变更任务、递增revision，并将命令标为APPLIED；不允许则保持任务不变，将有效命令标为REJECTED及安全原因。正常业务拒绝以返回结果完成事务，不能抛出会回滚已写拒绝记录的异常。
7. 原子提交后返回回执。任何数据库异常使整笔事务回滚，不能出现已修改任务但没有操作记录，或操作显示APPLIED而任务未变。

登记过程中的暂态只存在于未提交事务，外部永远不展示假RUNNING操作；提交时必须APPLIED或REJECTED。目标不存在/未授权/非法请求不创建可读取业务审计记录；记录基础设施安全诊断时仅使用脱敏信息。数据库锁超时或死锁回滚返回503 TASK_ACTION_UNAVAILABLE，客户端先查原operationId，不自动换编号重发。

与worker竞争：同一个任务行锁决定先后。取消先提交，worker后续无法领取；worker先领取，取消看到revision变化或RUNNING，返回拒绝，不宣称已取消。两个管理员不同编号操作同一旧版本最多一个生效。比较跨日、预算和租约结果不确定均在锁内复核。

## 5. 操作记录与数据迁移

建议下一个迁移V9__admin_task_actions.sql；实施前再次确认当前最高迁移，不能覆盖已用序号。只改MS-04库，新增revision和admin_task_operation，不重建或清空业务表。

admin_task_operation字段：

- operation_id CHAR(36)主键，全局唯一；task_id CHAR(36)关联本库任务，保留本库外键可行；actor_user_id CHAR(36)仅外部用户引用，无跨库外键。
- action VARCHAR(16)：RETRY/CANCEL；request_hash CHAR(64)；expected_revision BIGINT；reason_code VARCHAR(32)。
- outcome VARCHAR(16)：事务内登记PENDING、终态APPLIED/REJECTED；仅终态可提交。result_code VARCHAR(64)有限枚举。
- before_state、after_state VARCHAR(24)可空；before_revision、after_revision BIGINT可空；requested_at、finished_at DATETIME(3)；冷却及审计索引(task_id,action,outcome,finished_at,operation_id)，历史分页索引(task_id,requested_at,operation_id)。

不要将“仅终态可提交”误实现为MySQL行级CHECK禁止插入PENDING；使用事务程序保证并测试回滚，CHECK约束限定枚举与非负数即可。所有回执保留提交时状态与版本，后续worker推进不得改写历史回执。

reasonCode供管理员选择：RETRY_DEPENDENCY_RECOVERED、RETRY_TRANSIENT_FAILURE、CANCEL_NOT_NEEDED、CANCEL_DUPLICATE_REQUEST，分别绑定对应action；不是替换任务原error_code。第一版只选固定原因，不提供自由备注，API拒绝note等未知字段，数据库不新增note列。管理员身份、原因、时间及前后状态已构成基本追踪记录；自由备注后续另行设计。前端文本方式渲染，不能v-html。

新增记录不属于防篡改合规审计系统。当前本片不自动清理，保留策略后续独立制定；数据库管理员仍可修改底层数据，不宣称不可篡改。

## 6. 公开接口与安全边界

沿用{code,message,data,traceId}，Cache-Control:no-store；所有新接口每次解析权威会话并校验ADMIN。POST解析得到的actorUserId放入服务器内部请求上下文，不能接受浏览器actor/role，也不能在Controller再解析一次后使用不同身份。

### 6.1 能力提示

GET /api/v1/admin/tasks/{taskId}/action-options，无查询参数。

返回taskId、revision字符串、queriedAt、actionsEnabled布尔值、retry与cancel各自的allowed、reasonCode、message及retryAvailableAt（如适用，可空）。actionsEnabled仅说明管理员写入口全局开关，不代表当前任务允许新的重试或取消；用于恢复原请求入口的提示，服务端POST仍重新校验。读取任务/尝试/比较上下文/冷却记录时使用与009A一致的本库只读快照；worker开关及全局写开关也参与提示。不创建命令、不产生副作用。

页面先显示详情，再请求此接口。能力判断仅为用户提示，POST必须重新检查。现有009A列表和详情DTO保持不变，避免把操作能力缓存进列表。读取失败显示“操作条件暂不可用”，不默认放行。

### 6.2 提交操作

POST /api/v1/admin/tasks/{taskId}/retry
POST /api/v1/admin/tasks/{taskId}/cancel

Content-Type:application/json，正文最多4KiB，必须X-Aistock-Admin-Action:1。正文精确字段：operationId为客户端生成的规范UUID；expectedRevision为非负规范十进制字符串（不超Long.MAX_VALUE）；reasonCode为对应操作的枚举；第一版不接收自由备注字段。无用户ID、内部attemptId、租约、预算、worker开关或新任务参数。

HTTP 200：APPLIED回执，表示状态变更已提交；非任务执行成功。相同成功命令的重放仍200。

HTTP 409：code使用对应安全错误，data承载已登记REJECTED回执，traceId为当前HTTP请求追踪；禁止使用会丢弃回执的仅null-data错误封装。例如TASK_REVISION_CONFLICT、TASK_ACTION_NOT_ALLOWED、TASK_RETRY_COOLDOWN、COMPARISON_CYCLE_EXPIRED、COMPARISON_BUDGET_EXHAUSTED；这些回执可按operationId再查。原拒绝编号重放依然409，不因条件后来改变而执行。operation key冲突返回普通安全错误，不展示他人回执。

回执字段：operationId、taskId、action、outcome、resultCode、message、beforeState、afterState、beforeRevision、afterRevision、requestedAt、finishedAt。revision均字符串；拒绝时before/after为锁内实际观察值，且不得含任何内部执行令牌或会话。部分无任务的错误不带回执，前端不得把每个409都当作可查询已登记记录。

### 6.3 查原操作和历史

GET /api/v1/admin/tasks/{taskId}/operations/{operationId}：仅创建此命令的当前管理员可查询超时后的原回执；其他管理员返回404。返回200与原回执，其outcome决定是否生效；首次POST拒绝的记录也可查。

GET /api/v1/admin/tasks/{taskId}/operations?limit=20&cursor=...：当前ADMIN可查看此任务的脱敏操作历史，展示actorUserId用于区分管理员，不调用用户服务逐条补邮箱/昵称，不暴露请求哈希和原始凭证。按requested_at DESC、operation_id DESC稳定分页，默认20、最多50，固定首屏上界；游标绑定taskId/limit/排序键并严格校验，无任意SQL字段。沿用009A有界游标解析及重复参数拒绝规则。

匿名/过期401、普通用户403、账号服务不可用503；路径非法400、记录不存在404。未知方法/路径404；GET不触发retry/cancel，HEAD/PATCH/DELETE不提供旁路。网关安全错误白名单要覆盖明确的新错误，不回传原始异常正文。

### 6.4 跨站与网关

新POST在网关和直连调度服务均要求精确允许的Origin，初版仅http://127.0.0.1:15173及http://localhost:15173；缺失、null、非允许Origin均403。不依据浏览器可伪造的role或Forwarded头授权。Origin与固定自定义头结合JSON约束形成浏览器跨站写保护，Cookie SameSite与CORS不能单独代替它。以后换部署域名统一配置允许来源，禁止通配符配凭证。

受控OPTIONS仅允许精确新路由、方法及必要请求头，不解析会话、不查询任务、不写库。网关转发原Cookie、Origin、Content-Type、固定动作头和既有trace约定，不接受外部actor或内部服务Token；底层业务凭证只能由服务自身配置。

全局配置aistock.admin-task.actions-enabled默认false；读取列表、详情、能力与历史保持可用，能力中的allowed为false并说明写入口关闭。全局开关关闭时新的POST拒绝且不改任务；已提交回执仍可只读查询。同一operationId重放POST仍需满足当前安全门，不以幂等绕过撤权、跨站或关闭开关。

## 7. 前端交互与恢复

在现有任务详情中新增“管理员操作”和“操作记录”，不塞入每行快捷按钮，避免误点。动作条件由action-options决定，禁用按钮旁保留具体文字原因。权限、任务切换、401/403继续使用009A共享失效边界：任一区域撤权，所有任务敏感数据清空并使迟到响应失效。

点击允许的动作弹出确认框，显示股票、任务类型、目标日期、当前状态及动作影响。重试说明“只会重新排队，可能继续访问行情服务，不能保证成功”；取消说明“只取消当前未执行任务，不撤销结果，不关闭后续自动计划”。必须选择对应原因，不提供自由备注；取消对话框不产生operationId或请求。

确认提交前生成operationId，保存不可编辑的原请求：taskId/action/operationId/expectedRevision/reasonCode，并禁用重复提交。按当前userId分区保存在sessionStorage，额外记录createdAt用于显示；不保存Cookie/Token/密码或自由文本。刷新时先校验固定字段、类型、UUID、版本、原因及task绑定，非法存储仅报恢复信息损坏，不能据此直接发POST。切换账号或退出清理；任何请求仍校验当前权威身份，客户端存储不是授权凭据。

网络超时、502/503或失去响应时显示“操作结果尚未确认”，提供“查询原操作结果”；不自动重试POST、不生成新编号，也不宣称失败未执行。GET404不能证明之前POST未提交（原事务可能未提交），维持未知状态。刷新或重新进入页面只自动GET，不自动补发。

为避免请求根本没有到达时永久卡住，新增“恢复原请求”人工入口：查询后仍未确认，可再次打开确认框，清楚说明“若原请求已完成，将返回原结果；若尚未登记，可能现在执行”。管理员明确确认后仅发送相同taskId/action/operationId/expectedRevision/reasonCode，不能改版本、原因或编号；每次人工确认最多一个POST。恢复期间禁用重复点击；成功返回原回执或按当前任务锁内条件处理，冲突则返回持久拒绝，不得替换为新请求。失去响应继续保留原请求，管理员可再次先查后确认恢复，不做定时重发。写入口关闭、会话失效或权限丢失仍拒绝恢复，保留只读查回执途径。

未知请求未解决时当前任务的“新重试/新取消”保持禁用，但恢复原请求不依赖原动作能力仍allowed（任务可能已推进，查询回执暂未返回）；它依赖当前ADMIN身份、写入口开启及原请求完整。服务端仍先安全鉴权、同编号重放，未登记时才重新校验旧expectedRevision和所有规则。其他任务可只读浏览，其他标签页的新编号由revision/行锁限制。

APPLIED后展示“已重新排队/已取消”及回执时间，刷新当前详情、能力和操作历史；列表按当前已应用条件刷新，不把操作回执的QUEUED冒充worker尚未领取。REJECTED展示安全原因并刷新，管理员若要再次尝试必须基于新能力/版本重新确认，生成新编号。操作回执本身不随worker后续状态更新。

窄屏确认框不超出视口，文字可换行，历史表格可横滚；Tab/Enter可操作，打开确认框聚焦标题或首个字段，取消/提交后恢复合理焦点。处理中及未知结果有可读提示，不只用颜色。刷新页面不自动发送命令。

## 8. 代码职责与兼容

MS-04 admin包新增操作DTO、政策判断、命令应用、操作仓储与公开Controller；revision相关SQL在TaskRepository所有既有写入点补齐。单一政策组件供action-options与POST共用，POST仍在行锁内重新读取，不能复用旧查询结果。由一个事务边界完成命令登记、任务迁移及终态保存，不能嵌套启动彼此独立的新事务。

避免直接调用现有内部retry并在另一个事务外追加审计。抽取可复用的本库锁内迁移或在同一仓储事务中实现管理员命令；内部接口原有调用契约不因浏览器DTO改变，仍不向公网开放，但其写入必须递增revision。既有自动重试、任务预算、租约与回执核对保持原业务含义。

网关AdminTask代理与SchedulerPublicClient增加精确POST和查询契约、安全头及错误白名单。GET原行为保持；旧泛化用户代理仍禁止捕获task路径。调度服务安全过滤器区分只读GET、受控POST和OPTIONS；不因开放POST而放开任意子路径。

前端adminTasks API与tasks页面增加操作组件和历史组件，复用taskAccess权限失效边界。新增自建目录有README，描述作用与实现方法。日期与时间继续上海显示，数据库UTC保存；金钱规则不涉及本片。

## 9. 测试与验收矩阵

先测试后实现；隔离MySQL验证并发和事务，不能只靠H2或mock推断行锁。

- 政策：三类型、全部现有状态、未知类型/错误、白名单分隔、残留租约或UNKNOWN尝试、worker/全局开关、冷却首轮及60秒边界、比较同日/跨日/预算耗尽和context损坏。
- revision：每一种既有写入路径递增；相同毫秒多次状态变化仍不同；幂等创建与只读不递增；JSON大整数保持字符串；旧版本操作拒绝。
- 命令：同编号并发仅一次APPLIED；相同回执重放；不同内容或管理员编号冲突；相同旧版本不同管理员至多一笔生效；固定原因绑定、未知备注字段拒绝；拒绝记录提交；异常回滚无半笔数据。
- worker竞争：取消先提交无法领取；领取先提交取消拒绝；重试后领取生成新ordinal，累计历史完整；比较重试不补预算；跨日待领取过期；RUNNING/RECONCILING和旧回执不可被人工覆盖。
- HTTP安全：匿名/USER/ADMIN/禁用/会话撤销、伪造actor与内部头、身份服务故障、可信/不可信/缺失Origin、JSON大小/类型/重复字段、路径与方法白名单、直连调度服务与真实Feign转发、CORS预检无副作用。
- 只读回归：009A列表/详情/尝试不创建操作，不改变任务revision；原游标边界与no-store不变；未知错误安全映射，不输出Token或原始异常。
- 前端：禁用原因、确认取消不发请求、重复点击一次POST、状态冲突刷新、超时先GET回执、刷新默认只查询、未知编号404不误标未执行、人工恢复同编号原请求且不自动POST、账号/任务切换迟到响应、子区403共享清空、原任务筛选保持、窄屏与实际键盘。
- 完整回归、正式构建、一次独立代码审查及问题修复通过后才能部署。测试数量不等于覆盖率，仍保留项目覆盖率/性能/长期运行待办。

部署前备份原MS-04库，保留旧scheduler/gateway/frontend镜像及校验值；迁移后默认写开关关闭，先验收旧只读业务、操作条件和历史。隔离环境以固定样本及mock业务客户端验证真正重试/取消；不能为验收在生产篡改任务或预算。

验收分三阶段记录：隔离真实命令验证、默认关闭的本机部署核验、限定对象和预算的真实写链路。前两阶段不能替代第三阶段；未完成第三阶段不得标记本片完整验收或把生产写入口长期关闭说成功能已完成。如需真实写操作验收，另行取得具体task_id、动作及供应商调用预算授权，并明确worker自动尝试上限，监测任务和回执；没有合适对象时保留“真实重试已验收，真实取消待自然任务验收”，不能凭界面禁用状态声称整个功能通过。既有自动任务配置不擅自修改。故障先关闭管理员写开关，再回旧镜像；新增字段和记录保留，不自动倒灌备份或删除数据，完整回退没有实跑则明确说明。

## 10. 自审与待审核结论

已逐项核对TaskRepository全部创建/领取/过期/核对/完成/重试/取消写入、TaskWorker类型开关、比较上下文与预算、TaskController内部契约、SessionIdentityClient、管理员鉴权过滤器、网关精确白名单及V1—V8迁移。

已消除歧义：版本是独立revision，不用毫秒updated_at充当并发版本；operationId与后台attemptId分开；重试只入队，取消不关后续计划；人工操作不会删除尝试或修改已保存业务结果；比较预算满时允许保持不可重试；回执未知不能自动重发，但可再次确认后恢复同编号原请求；审计终态与任务修改同事务；回退旧worker前关闭新写入口；读取条件不代表提交时仍满足。

本片不更改主需求、任务书顺序或13业务，不将其余后台管理功能标为完成。本次设计实际由Codex整理，CodeArts真实使用证据仍待用户补充；没有新增文献收录或实验效果承诺。

详细设计已经用户确认。下一步：审核配套七项实施计划，通过后沿用当前对话逐项执行方式按TDD编码。本文件是设计，不是已实现或已验收证明。

## 11. 2026-10-20优化修订

用户采纳：人工恢复同编号原请求、第一版固定原因不提供自由备注、三阶段验收、明确隔离测试准备、限制改动范围。同步修订接口/数据表/测试/页面恢复，保留七步实施顺序及全部核心并发与权限保护。

2026-10-21追加记录：用户授权自主完成后，一次真实重试成功，旧三次失败记录及操作回执保留；关闭入口后历史仍可读取。当前没有可取消自然任务，取消生产链路待验收，Task7不标全部完成。
