# SDD-005D3 预测事后验证设计

日期：2026-10-05。状态：D3A内部实现/部署验收完成，D3B1本人页面已实现/部署，D3B2每日自动调度已实现/部署，修订专项D3B3已实现/部署。依据需求V1.9 FR-06/AC-06及SDD-005D2不可变预测快照。

## 目标与范围

让用户知道已经生成的五交易日预测后来表现如何；实际数据未到、停牌、来源故障都不能算作预测正确。原预测、模型、输入和目标日期不可覆盖。使用真实目标日行情计算结果，不以历史研究评价冒充本人预测评价。

沿用用户授权的“先计划、检查、再做”和常规细节自主处理。业务仍13项、五服务、ADMIN/USER。采用架构设计路径：跨MS-02行情、MS-03预测、MS-04调度，先明确证据和评价，再接调度与界面。不自动发布现有模型、不改变权重、不训练新模型、不接公告或新付费源。

## 设计建立时实现核对（后续进展见末节）

1. D2成功快照包含固定input、prediction、originDate、originRawClose、targetDates、referenceBasis=ORIGIN_SHARE_BASIS及模型/输入哈希；MS-03历史只读本人。
2. MS-02 forecast-window是60日预测输入接口，其发布校验严格要求60行。事后验证不能缩短窗口后复用该发布表，也不能因历史输入不再FRESH就否定当时预测。
3. Python ForecastSource已经配对获取Baostock RAW与后复权收盘价，definition为baostock-backward-close-over-raw-v1。可复用采集器；新增业务验证不能伪装成另一份预测输入。
4. MS-04现有TaskWorker仅执行行情刷新，不能直接宣称已经支持预测验证。扩展任务类型时保持既有刷新、租约和回执恢复行为。
5. 三候选当前PENDING、实际预测0条。真实正向联调依赖管理员实际审核发布；未到来的目标交易日只能等待。2026-09-30起点当前五目标日为10月8、9、12、13、14日，不能提前造出实际值。

## 方案选择

采用同源配对证据和追加验证版本。直接用未复权价格比较虽然简单，但除权时会产生错误评价；采用新调整后的模型输入重新预测会改变原预测，不符合FR-06。故保留预测，换算真实目标价格到同一股本口径，比较两者。

第一步D3A完成MS-02真实证据版本、MS-03纯计算和内部验证回执，可独立测试内部闭环。第二步D3B扩展MS-04统一任务类型和每日计划、接本人历史与详情。D3A单独完成不等于自动验证业务完成。

## 价格与指标定义

定义P0为原成功快照的originRawClose，Pt为固定referencePrice。一次真实配对采集中，R0/Rt为起点/目标日RAW收盘价，B0/Bt为同次后复权收盘价。q0=B0/R0，qt=Bt/Rt，真实同口径价At=Rt×qt/q0。必须使用同一配对证据中的两个因子；禁止拿原预测输入旧q0与新采集qt拼接。保存源RAW、后复权值及换算规则，便于复核。

计算使用BigDecimal及明确精度；内部除法保留18位HALF_UP，At与预测价比较前统一6位HALF_UP，显示2位只用于展示。规则编码origin-share-close-v1绑定全部计算、平盘与精度定义，不由前端传入。原P0不因事后行情修订而变动；证据R0若与P0不同，记录originRawRevised诊断，展示“起点行情后续被修订”，方向参照仍是生成时固定P0。

1. 逐日价格误差=Pt-At，绝对误差=abs(Pt-At)，原点归一化误差=abs(Pt-At)/P0。
2. ratioMAE=有效点的原点归一化绝对误差均值；ratioRMSE=相应平方均值开根。价格MAE/RMSE用于单条记录，不能将不同股价股票直接混算后宣称模型优劣。比例指标显示百分数，并明确不是准确率，也不是MAPE。
3. 方向使用sign(Pt-P0)和sign(At-P0)，统一6位价格下相等为平盘0。两个符号相同算命中，包括同为0；上/下/平三分类。directionAccuracy=命中数/有效点数，有效点为0时指标为null，不显示0%或100%。
4. 同一有效日期计算独立朴素基线Pt_baseline=P0，显示同样口径误差。基线只作对照，不写回Transformer预测结果。相对改善=(baselineMAE-modelMAE)/baselineMAE，仅baselineMAE>0时计算；基线为0时显示无法按比例计算。
5. 固定五目标日期不因验证时日历改变而后移。后来确认休市或停牌时标明CALENDAR_CHANGED/SUSPENDED，不补价格、不移步长。

## 真实证据契约与数据归属

MS-02新增独立realization模块/表，保存realizationVersionId、instrumentId、provider、definition、originDate、五个固定目标日、dueThrough、rawVersionId/rawContentHash、sourcePayloadHash、calendarHash、contentHash、capturedAt及配对点/日历。MS-02仅写自己的库。MS-03只保存版本引用和验证快照，MS-04只保存任务引用，不跨库联表或建立外键。

内部POST /internal/v1/market/realizations/prepare 接收operationId、instrumentId、originDate、targetDates、dueThrough、leaseUntil；dueThrough必须不晚于北京时间已到日线发布截止日期，targetDates恰好5个、唯一递增且晚于起点。使用既有20点发布时点配置；不能仅因自然日已过就认为日线存在。根据当前RAW版本可用范围配对获取起点至已到目标日的RAW/后复权/日历；未到日不请求未来价格。

采集HTTP在事务外，来源唯一Baostock。配对RAW日期/身份/数值/交易状态与被引用的不可变RAW版本逐行一致后才发布证据；不一致返回REALIZATION_RAW_MISMATCH，等待行情刷新，不混用版本。缺少起点或非正价格/因子拒绝；目标日缺失可逐点记录，不伪装全量AVAILABLE价格。日历完整性仍校验自然日连续、日期范围及开市值。tradeStatus=0的行即使有收盘数字也不纳入评价。真实来源正文不返回用户，凭据不入日志。

GET /internal/v1/market/realizations/{versionId}读取固定证据；GET /internal/v1/market/realizations/operations/{operationId}核对执行回执。重复同operationId和参数复用结果、变更参数409；同内容证据去重。原证据不UPDATE，仅增加新版本。操作回执与证据发布同事务，过期租约在写入前最终检查，过期操作不得新发布。所有内部接口在解析JSON前验证SERVICE_TOKEN，网关不公开。

D3A传输采用已验证的有界HTTP模式：配对采集35秒，固定证据读取10秒，MS-03执行总预算90秒，租约120秒；禁止重定向，候选体最大128KiB、请求4KiB。金额/价格以十进制字符串传输。参数和版本校验在读取与计算双方执行。

## 验证状态与不可变存储

每个点状态：WAITING（未到发布时点）、VERIFIED（有效真实交易数据）、MISSING（已到但源未返回）、SUSPENDED（停牌）、CALENDAR_CHANGED（固定目标日后来不再开市）。除VERIFIED外均不进入误差和命中分母。

记录进度：PENDING=有效0点，PARTIAL=有效1至4点，COMPLETE=有效5点；同时返回dueCount、verifiedCount、missingCount和具体原因，不以PENDING掩盖五日均已到但数据缺失。失败属于执行回执FAILED，不替换最后一份有效验证版本。缺失不自动永久关闭，后续更新可补验证。

MS-03新增verification模块，读取本库SUCCEEDED且快照完备的forecast_request，保存append-only forecast_verification_version及verification_operation回执；唯一(predictionId,realizationVersionId,ruleVersion)，固定结果hash。新版本允许因子/数据修订后重新评价，但不覆盖旧版本、原预测或原输入。current验证指针在同库短事务内更新，读页面说明是否发生修订；过期或旧尝试不能覆盖较新指针。禁止旧dueThrough回退当前进度。

内部POST /internal/v1/predictions/{id}/verify 接收operationId、dueThrough、leaseUntil。隐藏记录仍可验证。重复参数重放，未知结果先查GET /internal/v1/prediction-verifications/operations/{operationId}；非SUCCEEDED拒绝，不调用模型、不因模型后来下线而拒绝历史验证。数据来源读取失败不改变成功预测，不产生假的验证快照。

尚无目标日到期时，不调用采集、不创建真实证据或评价版本；操作回执返回SKIPPED_NOT_DUE及null验证版本。页面由固定目标日派生WAITING，不把空评价伪装来源成功。

D3A提供上述内部流程，不开放用户手动传实际价格或规则。D3B本人GET /api/v1/predictions/{id}/verifications?page=1及GET /api/v1/predictions/{id}/verifications/{versionId}；必须以当前会话查本人预测，ADMIN普通接口也限本人，隐藏不删除。

## D3B统一调度与页面设计边界

MS-04增加PREDICTION_VERIFY任务类型，与MARKET_REFRESH共用持久任务/尝试和领取机制；旧任务迁移保留类型及payload，禁止只给现有表加字段却让worker仍按股票刷新执行。每日计划从MS-03内部有界分页取成功待验证记录，MS-02刷新可触发同一验证任务。唯一键predictionId+dueThrough+ruleVersion；重启、重复触发只产生一个任务，数据修订需要新证据且保留原尝试。完成五点后普通日计划停止；相关RAW版本更正任务使用predictionId+新rawVersionId+ruleVersion独立去重键。仅后复权值修订但RAW版本未变时，由受控重核使用独立operationId采集，再按realization内容hash去重；不声称RAW事件能自动发现所有因子修订。

调度HTTP在事务外，复用UNKNOWN→RECONCILING→回执核对，不盲目重复派发；60/120秒退避、最多3次默认重试，可配置且有界。服务故障可重试；参数错误和不合法预测不自动重试。采集限并发1，与已有Baostock会话锁和推理槽协调，不能增加无限线程。

页面沿用历史详情：五点同口径预测/实际明细、待验证原因、验证进度、比例误差与方向命中分开、同日期基线对照、验证数据版本/采集时间/规则版本。验证失败保留最后一次有效结果并提示更新时间，取消“事后验证未接通”文案只能在真实路由已接通后进行。账号切换、旧详情响应、隐藏行为沿用D2保护；误差区间、MaaS仍独立，不能删除尚未接通提示。

## 必须验证的边界

1. 无公司行动：q相同比较与RAW一致；拆股示例P0=100、Rt=50、q0=1、qt=2，At=100，不能算作下跌50%。该示例是数学测试，不是来源真实市场证据。
2. 部分到期、未到发布时点、全缺失、停牌有价格、日历修订，计数与分母一致。
3. 0样本指标null，平盘规则、基线0误差、精度边界、逐日指标和合计一致。
4. RAW与配对采集不一致、不同证券/版本/日期/哈希、缺起点、来源失联，拒绝且保留原预测和旧验证。
5. 同操作重放/参数冲突、同证据去重、数据修订追加、不回退指针、隐藏可验证、重启未知核对、租约过期不能写。
6. 真实隔离MySQL，不在业务库插入模拟预测；内部未授权/伪角色拒绝及网关404，D3B用户跨账号404与CSRF策略。
7. 真实成功预测生成后，在目标日实际到来前展示WAITING；到期后用真实来源验收。历史fixture不声称未来盲测或模型提升。

## 自查与开放验收

本设计保持13业务、五服务和双角色，新增证据不混用预测输入，原预测不可覆盖，失败不当正确，基线独立。20点是既有配置默认，未来可改；五日是本批模型契约，10/20日仍须另行模型/日历/评价兼容验证。配对来源定义沿用现有实现，不新增文献或准确性承诺。

D3A已实现并通过236项后端/8项采集/1项真实跨服务读取与部署核验，见docs/verification-sdd005d3a.md。D2已有实际管理员发布及用户成功预测。D3B1本人API与页面已实现并部署，最终后端243项/前端40项通过，见docs/verification-sdd005d3b1.md；D3B2每日自动调度已实现/部署，修订专项D3B3已实现/部署，当前五目标未到期，FR-06整体未验收。

D3B2普通每日计划已启用，最终263项后端/40项前端及真实空扫描、SKIPPED/重复/重启验收通过，见docs/verification-sdd005d3b2.md。D3B3 RAW事件和受控因子重核已部署，真实目标到期评价继续开放。

2026-10-07 D3B3实际验收：六事件消费、受控重核去重SKIPPED和重启保留通过，最终后端279项；见docs/verification-sdd005d3b3.md。同RAW变化仍需受控请求，真实五目标未到期，不声明准确率。
