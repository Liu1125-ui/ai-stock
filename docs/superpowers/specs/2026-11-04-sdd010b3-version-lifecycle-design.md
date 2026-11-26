# SDD010B3 行情版本停用、恢复与在途保护详细设计

日期：2026-11-04。阶段：代码核对后的设计稿；尚未实现、迁移或开放操作。依据FR-11、已交付010B1重取、010B2A引用检查与010B2B审核。沿用13项业务、五业务服务、两角色、现用任务书顺序。用户已授权代理自主设计、自审和推进常规实现，本文件中的首版策略为实现建议，不冒称用户逐条确认。

## 1. 目的与范围

管理员能够阻止有问题的历史RAW版本被新计算使用，查询等待中的停用操作、恢复允许新使用，并保留全部已有快照及审计。优先保证版本切换与正在进行的预测不会互相破坏。引用统计不作为停用许可，也不以定时等待代替在途核对。

本片首版仅管理已发布的baostock/RAW历史版本。当前行情头禁止停用，返回明确原因；恢复不改变当前头。若当前行情本身存在问题，应先通过既有更新发布形成新头，再管理旧版本。该限制是可演进策略；将来支持当前头隔离需另补首页、自动更新及降级规则，不能暗中切换或清空头。

不删除版本、不编辑价格、不重写历史预测/估值/对比/解释，不创建训练任务，不自动发送AI请求或邮件。审核标记与使用许可相互独立，“已核对”不自动恢复，“需复核”不自动停用。

## 2. 已核对的真实实现及缺口

MarketVersionReferenceService顺序读取三个owner的候选引用，属于不同时点的观测，无事务互锁。MarketQueryService显式版本读取及ForecastInputRepository对RAW的AVAILABLE检查只在读取/发布输入时发生，不能覆盖跨服务推理至结果保存的整个时间段。

ForecastService.prepare/infer/succeed分属不同调用阶段；AnalysisService目前先取证据再claim，必须调整为先建立持久操作身份。ComparisonService、PortfolioService、VerificationService和AlertEvaluationService也会创建新版本相关结果，不能只保护预测入口。

MarketVersionRepository.publish先锁行情头，再查询/复用AVAILABLE版本。它也必须遵守新许可门禁，避免重新把停用的历史版本发布成当前头。既有内容发布状态与新的使用管理状态表达不同事实，应分开。

## 3. 两类状态与不可变事实

market_data_version.status继续表达发布事实（STAGING/AVAILABLE等），不批量修改原行、价格或forecast_input_version。另建market_version_lifecycle，useState为ENABLED、DRAINING、DISABLED，revision为非负BIGINT，接口用十进制字符串传输。

ENABLED：新业务可申请使用凭据。DRAINING：拒绝新的使用，已经登记的业务允许完成；不是“已停用”。DISABLED：无未解决的使用凭据，禁止新的计算使用。历史查看及已保存快照读取继续可用。

停用命令的事务先阻止新使用，再判断是否有在途凭据；没有则直接DISABLED，否则DRAINING。最后一个凭据安全释放后自动完成原停用操作。恢复把DRAINING或DISABLED改为ENABLED，递增revision；若取消了等待中的停用，旧命令明确为SUPERSEDED，不留永久等待。重复提交使用原operationId读取原结果，不重复改变revision。

每次新的停用都有独立cycleId。迟到的旧cycle完成通知不能关闭恢复后或第二次停用的周期。后台完成与恢复竞争使用同一版本行锁，以实际提交次序决定。

## 4. 必须满足的不变量

1. 停用与新使用登记在行情库锁同一版本生命周期行；不能发生“已经返回停用成功，但旧代码仍创建新引用”。
2. 一个业务使用的每个版本都必须登记；版本集合、owner、业务操作和执行代次在凭据创建后不可更改。
3. 在途保护覆盖取数、计算、结果持久化以及失败落库，不只覆盖HTTP读取时间。
4. 只有owner明确证明该代次已不可再写入，才能释放凭据；超时、断网、进程退出及租约到期都不是证明。
5. 历史快照永远保留原版本、原日期及数值；新使用许可不倒改历史模型输入或验证结果。
6. 不跨业务库联表或写库。owner以本服务事务与内部接口提供状态证明；公共管理员接口不返回用户持仓、成本、问题内容或身份。

## 5. 持久使用凭据

新表market_version_use：useId UUID、owner（prediction/notification/market）、purpose枚举、ownerOperationId UUID、generation十进制长整数、versionId、requestHash、state（ACTIVE/RELEASED）、createdAt、lastCheckedAt、releasedAt、terminalProofHash。唯一键(owner,purpose,ownerOperationId,generation,versionId)。使用ID是关联凭据，不单独作为权限，仍要求内部服务认证。

用途至少覆盖FORECAST、STOCK_ANALYSIS、COMPARISON、PORTFOLIO_VALUATION、PREDICTION_VERIFICATION、ALERT_EVALUATION及市场本地派生发布。新计算基于旧快照并额外读取RAW时也必须登记；只读取已经保存且不可变的解释/问答依据不重复登记。查看网页日线为只读浏览，不登记长期业务引用。

新业务先在owner库持久化可查询的操作及执行代次，再申请凭据。市场在同一事务内解析头/明确版本集合，锁版本及许可行，检查全部ENABLED并原子登记全部凭据，再返回绑定版本的数据或读取授权；不得先返回证据再异步补登记。多股/多持仓任一失败全部回滚，不留下半个版本集合。

同代次同集合重试返回原凭据；改变集合或用途冲突。收到旧响应时必须校验operationId/generation、版本集合和响应大小。DRAINING期间允许原ACTIVE凭据恢复读取，不允许RELEASED凭据重新使用或借旧编号新增版本。

owner结果事务成功后通知释放；失败也必须先持久化终态。释放失败保留ACTIVE，后台重试查询owner只读终态再释放。owner采用generation及终态CAS，终态后迟到的推理、采集和AI响应不能重新提交结果。新执行代次必须重新申请，旧凭据不能授权新重试。

后台按有界批次核对（建议每分钟最多50条，逐owner短超时）；owner返回UNKNOWN、处理中、找不到、代次不符、网络异常均保持ACTIVE，显示“待核对”，不自动消除。原长期RUNNING且没有安全终态机制的业务先补终态CAS和恢复机制；不得提供管理员强制释放按钮绕过。该故障可能使停用等待较长时间，保护数据优先，后续可以按owner真实回执修复孤儿操作。

## 6. 表与事务设计

行情库新增market_version_use_bundle：bundleId主键、owner/purpose/ownerOperationId/generation唯一聚合键、selectorHash、versionsHash、规范化不可变versionIds与state；先锁聚合身份再解析/登记集合。同业务重复登记先返回原绑定，不因行情头变化重选；异集合冲突，不允许同代次增补版本。新增market_version_lifecycle（versionId主键、useState、revision、cycleId、updatedAt）；market_version_use（上述凭据和检索索引）；market_version_action（operationId主键、actorId、versionId、action、expectedRevision、requestHash、reason、cycleId、state、beforeState、afterState、createdAt、finishedAt、安全拒绝原因）；history可使用独立追加event表记录异步完成/取代，禁止覆盖原请求正文。

动作正文固定{operationId, action:DISABLE|RESTORE, expectedRevision, reason}；reason去首尾空白后1—300字符，拒绝控制字符，requestHash覆盖规范化正文及股票/版本/actor。相同编号不同用户或正文冲突。开关关闭允许重放已提交回执和读取历史，不能首次执行新命令。

迁移为全部既有已发布RAW建立ENABLED记录；不修改行情头、日线或既有内容状态。后续新版本发布必须在同事务建立ENABLED行。门禁开启后缺少许可行默认拒绝并返回需核对，不能默认为可用。

锁顺序统一：业务聚合登记行（所有修改bundle的路径，包括登记重放、恢复绑定及整组释放）→行情头（如需解析头，按instrument/provider/basis稳定排序）→RAW版本（UUID稳定排序）→生命周期→使用/动作行。管理员停用/恢复及单版本异步完成不锁bundle；不能先锁version/use再更新bundle.state。整组释放先锁bundle，再按排序锁所有版本/许可/use，提交时一并更新聚合终态。不得跨版本按不稳定顺序加锁。所有相关发布、输入生成、批量解析和命令路径遵守；不得在跨服务HTTP调用期间持有数据库行锁。仅在本库短事务进行状态转换，跨服务状态先读再本地按代次幂等处理。

停用在锁头后核对当前指针；若已经被重新发布为当前头，拒绝本次停用。发布复用旧版本前必须锁并检查ENABLED。不能依赖较早预检或引用数。停用旧版本不会将已有forecast_input_version批量改状态，未来使用需回查其RAW许可；同库输入发布在短事务内检查并锁许可。

## 7. 各链路接入范围

- 五日预测：持久claim→准备固定RAW/输入并申请使用→推理→快照成功/失败事务→释放。旧请求恢复校验同代次，失败不生成占位结果。
- 综合分析：把操作身份前置，再原子绑定固定RAW及凭据；在检查许可失败时不调用MaaS。AI最终成功/失败落库后释放。已经保存的综合分析问答只使用原快照，保持历史读取。
- 多股比较：保留请求中已参与指纹的evidenceIds，按固定比较证据原子解析其rawVersionId全集并登记；不能拿当前头凭据保护旧证据，更不能替换用户指定证据。持仓估值才在同一事务解析全部当前头并登记完整集合。两者均在owner快照保存成功/失败后释放，拒绝部分成功和自动换版本。空持仓集合不造凭据。
- 实际行情验证：原预测快照只读不重算，新的实际行情证据RAW需要凭据；新验证版本完成后释放。停用相关RAW时保留旧验证，提示证据当前不可新用，不补造实际价。
- 预警评估：通知服务建立执行代次后，绑定证据RAW，再评估并在同库事务提交触发/消息/邮件outbox后释放。邮件真正投递只消费已保存通知，不重新读取RAW；历史预警不撤回、不补发。
- 调度：队列本身不凭候选引用长期占用版本；真正执行前在业务owner申请凭据。排队时冻结具体RAW的任务若尚未申请，执行时版本DRAINING/DISABLED必须明确拒绝，不自动切换。取消执行中任务继续沿既有禁止规则。
- 行情发布/派生输入/比较采集/预警证据：所有返回新业务证据的入口检查绑定使用身份，市场内单事务发布持锁完成；临时外部采集不能跨HTTP长持锁，应建立本地持久操作及凭据直到终态。

## 8. HTTP、安全与页面

管理员GET /api/v1/admin/market-data/{stock}/versions/{version}/lifecycle：当前许可状态、revision、是否当前头、在途数量、可操作原因及开关状态。POST同路径/actions：单次提交；GET /actions/{operationId}恢复原请求；GET /actions?cursor=...只读历史。按既有marketreview权限模式，直连与网关均解析真实管理员会话，配置同源Origin/自定义管理操作头、JSON大小限制、no-store与错误白名单。

内部使用协议建议位于/internal/v1/market-version-uses，支持按owner业务身份原子绑定/读取、终态通知及只读查询，稳定请求hash并限制版本数量。不得提供“服务端只信前端说已完成”的任意释放接口。owner终态证明走固定内部地址/固定用途枚举，禁止调用方传任意URL，认证失败/响应不完整一律保留凭据。owner证明状态需限定代次、terminalAt、fencingRevision和结果身份，不返回业务正文。

页面在固定版本详情新增“使用状态与操作”面板，与审核和引用检查分开。默认展示状态与原因，写操作需明确按钮及原因输入、确认固定股票/版本/动作；DRAINING显示等待数量和只读刷新，不显示停用成功。请求超时保留operationId并先查询；不自动换编号重发。切换版本/账号清理私人状态并忽略迟到响应。窄屏、键盘、加载与错误消息遵循既有组件。

新增ADMIN_MARKET_VERSION_ACTIONS_ENABLED默认false；MARKET_VERSION_USE_ENFORCED默认false仅为迁移期兼容。操作开关为true而门禁未开启或owner版本能力未齐备时仍拒绝新写命令。门禁开关不能在存在DRAINING/DISABLED或ACTIVE凭据时随意关闭，启动/配置检查必须阻止不安全回退。

## 9. 分期交付与上线门槛

B3-1：许可状态、持久凭据和纯策略/事务基础，操作入口关闭，不宣称支持停用。
B3-2：全部创建新版本引用的owner与市场内部入口接入，故障恢复/代次CAS/多版本原子绑定验证，旧worker不得绕过。
B3-3：管理员接口/页面、部署与受控验收。只有全部链路接入、旧进程退出且旧在途完成后才开启强制门禁。应用健康额外报告协议版本，服务版本缺失拒绝操作；能力报告不替代停机切换时清理旧在途。

部署先保留业务库备份和受影响镜像，在新写入口关闭时滚动兼容代码；正式启用门禁前安排短维护窗口，停止新业务接入及相关planner/worker，等待旧在途终态并核对未解决请求；有无法证明终态者不启用动作。记录并恢复原后台开关，不把备份等同恢复演练。先开启强制门禁、核对协议能力和任务，再单独开启管理员动作入口。

回退保持动作关闭；若存在DRAINING/DISABLED或未释放凭据，只允许回退前端到兼容查询版本，不回退后端到不执行门禁的旧镜像。恢复许可应以新系统显式命令完成且留痕，不能靠删除新增表或SQL批量改写绕过保护。迁移不自动回滚删除数据。

## 10. 必测反例与验收

至少覆盖：acquire/disable两事务竞争；两个方向均线性化；头在预检后变化；恢复/最后凭据释放/第二次停用竞争；多股原子全有全无；同编号异actor/正文/代次/集合；重复回执与未知提交；owner崩溃后完成回执丢失；终态CAS禁止迟到保存；租约过期不释放；依赖不可达及错误证明不释放；停用RAW已有输入不得新推理；历史快照读取/哈希不变；已保存问答不重取；排队冻结任务拒绝换版本；发布不得复用停用旧版本；审核状态不改变许可；邮件不因状态切换补发。

MySQL集成测试必须用独立测试库，两连接+屏障控制并发，不能只用mock证明跨事务正确。HTTP验证ADMIN/USER/匿名、直连和网关、CSRF边界/no-store/正文上限及安全错误；前端验证操作恢复、切换隔离、状态提示、窄屏与键盘。真实验收先使用隔离测试版本，不停用用户正在使用的生产版本制造场景；任何生产写操作单独限定版本、动作和次数预算，不借设计授权无限操作。

通过实现、集成、审查、部署及真实受控验收后再更新状态，不提前将FR-11整体关闭。当前头管理、任意历史初始化等原范围限制继续列待办。

## 11. 内部协议与复核补充

内部登记POST /internal/v1/market-version-uses：正文{owner,purpose,ownerOperationId,generation,selector,requestId}。generation为正十进制字符串；owner与purpose组合固定白名单；selector恰为一种：RAW_VERSIONS（排序去重UUID集合）、COMPARISON_EVIDENCE（保留股票/evidenceId配对并按股票排序的evidenceIds；selectorHash只标识使用选择，owner既有完整请求指纹仍绑定日期、公式及全部业务字段）、CURRENT_HEADS（固定股票集合、provider=baostock、basis=RAW）。总版本上限100且不得突破各业务原有更小上限。requestId用于传输幂等，业务聚合键用于约束重复登记；两种身份均须保持正文一致。

响应{bundleId,owner,purpose,ownerOperationId,generation,versionIds,uses,protocolVersion}，每项use返回{useId,versionId,lifecycleRevision}。完整集合和manifest持久化在同一事务；丢失响应重试先按原业务聚合键查询，不能重选当前头。证据读取传bundleId、operationId与generation，并验证真实证据的RAW全集恰匹配对应用途的绑定，不以任意ACTIVE useId授权其它请求。

终态通知POST /internal/v1/market-version-uses/{bundleId}/completion仅表示“请求核对”；市场从固定owner终态证明接口读取{owner,purpose,ownerOperationId,generation,state,terminalAt,fencingRevision,resultIdentity,versionIds}，state只接受已完成或已持久化不可再写的失败/取消。服务端核对代次与全集；证明不匹配不释放。通知重复幂等，同一bundle全部使用释放在一个本地事务内；锁所有相关版本按排序，不允许逐个部分释放后宣称整个业务完成。长时间处理中只记录检查时间与安全原因。

两侧健康/能力接口报告protocolVersion=market-version-use-v1及enforcementMode，不将协议能力报告当作普通用户授权。所有新协议入口仍有正文大小/时限/认证约束，未知枚举拒绝；内部接口不经公共网关暴露。

2026-11-04独立设计审阅指出：原§7比较与持仓共用“解析当前头”会破坏比较固定evidenceIds契约。已按现有ComparisonRecords.Input/ComparisonService修正为比较保护证据实际RAW集合、持仓才解析当前头，并补充聚合manifest和重试绑定规则。其余核心机制未见重大遗漏；这不是实现或部署验收。

最终复核补充：统一登记重放与整组释放的bundle优先锁序，避免version/use→bundle反向更新形成死锁；Task2加入相应双连接竞争测试。

## 12. Task2实现裁决（2026-11-04）

在不存在bundle时直接SELECT FOR UPDATE不足以构成稳定的业务身份锁。新增V16内部market_version_command_guard，先获取命令身份锁，再按既定bundle→头→版本→许可→use/action顺序工作。USE锁按owner/ownerOperationId/generation串行，记录已绑定purpose；改变用途冲突，新generation重新登记。ACTION锁按operationId串行，避免同编号跨版本/actor竞争形成反向加锁。锁记录在同一短事务里提交或回滚，无新公开入口。

新仓储采用READ_COMMITTED并校验嵌套事务隔离级别，避免先前一致性读快照导致计数滞后；后续嵌入旧发布事务时应显式适配，不静默降级。首版仅支持已有且非空的baostock/RAW行情头，无头初始化仍属原待办，拒绝把缺头假定为“肯定不是当前头”。

Task2中的releaseVerified仅为内部已核实证明的存储方法，尚未接入网络核验；不得对浏览器暴露原样提交证明即可释放的入口。Task3固定owner客户端及Task4/5不可再写终态接入完成前，不开放任何生产停用动作。

## 13. Task3A协议落地与后续边界

增加V17 selector_json与market_version_use_request。登记与原始选择同事务保存，requestId跨owner/正文冲突拒绝；业务代次不可重新选择头。旧selector缺失时，只允许原selectorHash匹配的重放补齐。回执可在协议开关关闭后查询，已释放原请求只返回RELEASED、不新增使用。

新协议默认关闭。POST基础路径登记；GET /requests/{id}及/{bundleId}要求owner/purpose/ownerOperationId/generation四个单值查询参数；POST /{bundleId}/completion只接受这四个身份字段，拒绝调用方自报state/证明。回复以requestId、protocolVersion、bundleId、平铺owner身份、state、versionIds及uses组成，字段状态不构成后续取证据授权。

固定owner证明路径/internal/v1/market-version-use-proofs/{purpose}/{ownerOperationId}?generation=...。client按配置owner根URL选择目标，不接受调用方URL，禁止跳转，完整响应上限64KiB和4秒截止。重复/不完整JSON、UNKNOWN、错身份/版本等均不释放；终态通知仍需owner真实不可再写的证明。

核对以bundle为最小单位而非单个use，避免部分释放；每批最多50个，先更新时间再逐条查询，损坏或未知条目保留并轮转。任务默认不运行。当前能力明确enforcement=OFF/consumerIntegration=PENDING/admin actions=false；强行启用enforcement在本构建拒启动。

本节仅为协议前置实现，实际证据/发布门禁留Task3B；不能把新内部接口等同于旧路径已经受保护，也不在生产单独部署当前片。

## 14. Task3B证据与发布接入（2026-11-07）

计算证据请求在原CandidateRequestFilter完成服务认证后，使用X-Market-Use-Bundle、Owner、Purpose、Operation、Generation五个头建立请求scope。用途按原计算路由固定白名单，缺失/重复头、错误owner/用途/代次或已释放凭据均拒绝。请求scope包含服务端已保存的selector和按股票分组RAW，不能由调用方填写价格或自行授权。

每段本地读取/发布以withActiveUse重新验证并锁定guard→bundle→RAW→许可，只有原ACTIVE可在DRAINING继续；scope元数据不是已释放后仍可使用的令牌。预测rawWindow及无显式inputId的窗口、比较rawWindow、持仓报价、实际行情验证head都解析到scope固定RAW，不再随新头变化。比较读取还必须属于原evidenceIds配对，不能借相同RAW读另一份证据；估值必须匹配整个股票集合。

本地派生发布（输入、比较证据、实际验证、预警）在短事务内再检查，外部采集不在本地事务中。RAW新发布同事务创建ENABLED行，内容复用锁版本和许可，DRAINING/DISABLED不能重新成为新头；原已提交操作回执可读，不重新附着头。直接本地生产者没有scope时也不能向被禁RAW新增派生引用。

预警准备专门拆为短事务内的begin及prepareLocal快照，collector保持外置；release发生在scope创建后也不能继续读取RAW。采集前checkpoint失败不转换成普通源不可用，迟到结果发布再次拒绝。公开历史行情/快照、内部回执、选项/规则边界等只读元数据不因停用被删除或改写，也不凭这些只读结果获得新的计算许可。

MARKET_VERSION_EVIDENCE_GATES_ENABLED默认false，仅供隔离接入测试。强制全局启用与管理员动作仍拒绝，直到Task4/5所有owner持久执行代次、终态CAS、证明及头转发完成。Task4必须同时改Java依赖客户端和Python app/inference/publication.py的内部行情复核，要求在线调用转发同一业务凭据；历史离线候选验收保持原边界。新的业务选新头，已经登记的在途业务固定原RAW，不偷偷替换。

## 15. Task4A分析持久准入（2026-11-08）

先补齐分析owner的本地准入缺口：检查本地请求/配置后，以原operationId/owner/requestHash登记并提交，再读取预测引用和行情。准备状态仍为RUNNING，但contextReady=false；不得把占位requestHash宣称为已核验contextHash。缺失或错误证据先保存失败，再保留原错误响应，原operation重新请求返回失败回执；修正业务选择须使用新operationId，不允许改写原指纹。准备不持数据库事务或付费槽位。

bindFacts按同代次RUNNING且未准备条件只写一次上下文；缓存查找先于共享MaaS槽申请，未命中才申请付费槽。到期准备、槽位到期、成功或失败都持久为不可再写终态；条件更新递增fencingRevision。首版同一操作不重新启动，executionGeneration固定1，传入错代次拒绝；新操作独立编号。旧记录通过V12默认1/1/true保全。

本片不提供market use bundle登记、完成证明或Java/Python转发，不能据此声明Task4整体完成、凭租约释放市场凭据或启用保护。后续Task4B须把本地准入与固定manifest、能力检查及真实终态核对接通。

## 16. Task4B分析owner使用协议（2026-11-08）

本片仅接入STOCK_ANALYSIS。V13本库表保存原operation/代次、requestId与单一RAW选择，在任何登记POST前提交registration_started；固定客户端验证protocolEnabled与evidenceGatesEnabled均为true。协议能力新增后者，仍明确全局enforcement=OFF、consumerIntegration=PENDING及actions=false。响应必须与原owner/purpose/op/generation/requestId和完整RAW集合一致，uses逐项核验；64KiB全响应与4秒截止、禁止重定向，只有配置固定市场根URL可用。

首次登记结果未知只用原requestId与原身份GET核对，找不到也不自动POST、换头或重算。记录不因租约过期、依赖错误或回执损坏而删除。BOUND后向市场证据客户端转发五项头，scope结束清理；不向MaaS发送这些头。已提交分析结果保持原缓存和重放，市场许可拒绝或未就绪时不取证据、不调用模型。

证明GET /internal/v1/market-version-use-proofs/STOCK_ANALYSIS/{operation}?generation=...，独立服务认证先于参数/正文，拒绝重复token、非GET、有正文或额外/重复参数，no-store。证明只由本库实际分析操作不可再写终态、同代次和已绑定版本集组成，返回owner/purpose/op/generation/state/terminalAt/fencingRevision/resultIdentity/versionIds九字段，不含用户、问题、事实或持仓。尚在途、未知绑定和错代次不提供成功证明；即使停止新登记，原绑定终态仍可查询。

本地分析提交完成后再通知市场请求核对；通知失败不撤销结果或假定已释放。默认关闭的恢复每批最多25条，先记录lastChecked再解码，损坏与未知记录保留但轮转，不饿死后续。恢复只查询原登记并请求核对终态，不启动模型。proof短事务按共享MaaS槽→分析操作读取，登记/附着按分析操作→本库使用记录，网络全部在事务之外；与市场库不跨库联表或写入。

预测、实际验证、Python和其它owner仍未接通，owner完整能力报告及全链路就绪留Task4C/5/6。当前两个新开关均false；本片不能单独启用生产证据门禁或管理员停用动作。

## 17. Task4C预测与实际验证接入（2026-11-08）

FORECAST使用全局唯一predictionId作为ownerOperationId，避免不同用户相同公开operationId冲突；实际验证继续用原全局operationId。两者请求与原业务先提交，再登记单股CURRENT_HEADS并保存服务器原RAW全集；未知登记只用原requestId核对，不重新解析头或执行模型。首版代次均1，同一操作不重启；V14补generation/state条件写入、终态时间和修订，旧快照与历史验证不改写。老已完成预测新增finished_at保持空，不虚构旧完成时间；没有新绑定的历史记录不能据此生成使用证明。

Java证据与推理HTTP转发同一scope；取得窗口后额外核对其RAW等于回执RAW，推理前检查业务仍在运行。正式Python路由先服务认证、拒重复/错误目的/代次/UUID的头，原五字段正文不能自报window或_marketUse。仅路由生成私有子进程凭据；worker向固定市场地址查询原inputVersion与绑定RAW时均转发同一身份，不再误比新的活跃头。模型发布查询不带市场使用头；离线候选流程不要求凭据。

实际验证仅新实际证据申请使用，未到验证日不登记；读取后RAW一致才计算、独立写验证版本，不改预测snapshot。证明来自各仓储真实不可再写终态，AVAILABLE映射SUCCEEDED；未知/错代次拒绝。关闭登记后仍允许核对原绑定终态。owner内部能力列三条已支持目的，仍PARTIAL，不把Task5比较/持仓/通知冒认为已接通。

新配置全部false，不在本片启用全局强制保护或部署。其它owner和市场本地派生入口完整核对仍留Task5，动作就绪检查/部署留Task6—8。

## 18. Task5A比较执行身份与历史证明（2026-11-08）

保留当前比较请求在RUNNING租约过期后重领的行为。公开业务键仍为(owner,requestId)，真实执行身份采用已有lease_generation UUID（全局唯一），作为市场ownerOperationId，每个执行代次的协议generation固定1。一次重新领取会产生新的执行身份与新凭据，不能复用上一轮。该映射是实现裁决，不新增角色或用户业务。

V15为每个执行身份建立独立comparison_market_use，保存原owner/request仅供本库查找，外部证明不返回这些字段。比较selector必须是原stock/evidenceId配对，不选当前头；实际RAW全集由市场原子解析，读取与计算前核对完整集合。

完成/失败与当前业务结果同事务写入本轮不可逆终态。重领旧过期执行时，在父请求行锁下先保存旧执行FAILED，再替换lease UUID；proof若发现租约到期而未重领，也必须先替换父行执行UUID，形成真正的写入隔离，再持久失败证明。父请求保持可重领的RUNNING/expired语义，不能为了释放凭据删掉原重试能力。仅看到过期时间不是释放依据；旧UUID被替换后，原ownedLease检查永远拒绝旧写入，即便时钟回拨。

原登记响应未知只查询原传输requestId，不更改selector或计算；恢复先轮转再解码，最多25条。管理员动作/全局门禁仍关闭，持仓与通知随后接入。

## 19. Task5B持仓消费者细化

采用原portfolio lease UUID为全局内部执行ID，generation=1。原请求不重领，deadline/lease与固定历史契约不变。非空快照验证后登记规范股票全集，引用凭据只传市场报价，完整RAW集合匹配再估值；空仓不登记。操作终态/fencing在原本地事务提交，proof锁父行后读取时钟，先物理过期再证明；不因租期推测释放。恢复保留原selector/request，仅查原回执，绝不使用会话令牌或持仓数量/成本。全局能力仍PARTIAL，新增默认false开关，FR-11保持开放。

## 20. Task5C预警评价消费者细化

原全局operationId为实际执行身份，generation=1，不重领。固定引用元数据接口仅投影evidenceId/instrumentId/rawVersionId，内部认证，不返回内容或新增引用；内容GET继续受凭据门禁。评价先持久准入，再存原引用选择和登记编号，只登记一次；未知响应原编号GET。实际终态、触发、站内信、邮件渠道决策同事务。proof通过owner guard后锁实际操作并检查期限，失败映射FAILED，其余完成映射SUCCEEDED；旧证据仍与当前边界核对，过期不补发。恢复不重读证据或账号、不重新评价，开关默认false。市场本地生产者/worker及全局动作仍待后续。

## 21. Task5D1调度明确拒绝语义

执行请求收到409明确版本/凭据拒绝，或503 MARKET_USE_NOT_READY，以原attemptId记录失败，原冻结RAW/请求不变；不扩大自动重试白名单。超时、未知登记、MARKET_USE_UNAVAILABLE/RESPONSE_INVALID等继续只查原回执，receipt本身的失败不伪造成终态。Task5D2市场本地派生接入仍待完成，不能提前启用全局动作。

## 22. Task5D2市场比较本地owner细化

内部token认证后，由比较服务先保存原刷新操作，再登记原RAW，不由调度器冒充市场owner。作用域覆盖fixedWindow/采集/最终事务，原指纹/期限不变；父真实操作与结果同事务fencing，证明锁后物理过期再返回。登记未知只查原request，恢复不采集或登记。仅local comparison开关与protocol/evidence gates同时就绪时，comparison-refresh POST准入责任转到服务，其他门禁不变；组合错误拒绝启动。原比较receipt RAW外键与生命周期use表保留本地关系，不改变跨服务固定引用报告owner枚举。可信同库owner仓储证明可直接整组释放，HTTP对外仍只返回九字段内部终态。其余生产者与Task6—8未完成，全局PARTIAL/default false。

## 23. Task5D3市场预警派生owner细化

原alert_evidence_operation只有request_hash，新使用表保存canonical原请求JSON供实际父行hash和RAW双重核对，不伪造旧请求。prepare先持久准入再绑定RAW，读取/采集/发布在scope内；终态fencing与发布同事务，proof锁实际父行后物理过期，原号恢复不重新登记/采集。仅该默认false开关与protocol/evidence gates就绪时，认证后prepare准入转服务；正文等其他门禁不变。通知侧原当前边界核验不变，历史提醒不补发。其余入口和Task6—8仍待完成，全局PARTIAL。
