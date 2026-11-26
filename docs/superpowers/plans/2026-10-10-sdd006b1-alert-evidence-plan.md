# SDD006B1 固定预警证据 Implementation Plan

使用writing-plans与executing-plans，沿用用户已确认的SDD006设计及“下一步”授权，在现有分支内联执行。本片只完成固定证据，不宣称自动触发/消息已完成。

Goal: 为价格及复权日变化规则提供可重放、可追溯的最新应到期日证据，价格可用性与涨跌幅证据可用性分开。
Architecture: MS-02独立保存预警证据及操作回执；使用算法专用alert-collect历史RAW/后复权配对入口，复用已有SDK配对逻辑，但不创建预测输入或预测请求。固定RAW版本必须是开始核验时的当前已发布版本；固定日历确定截止日和紧邻上一交易日。采集在数据库事务外，提交受120秒以内租约约束。MS-04/05后续消费版本，不跨库。

## Constraints and contracts
- Request: operationId UUID, instrumentId, rawVersionId UUID, tradeDate YYYY-MM-DD, leaseUntil ISO instant。仅内部令牌，4096B请求；HTTP采集响应128KiB，连接2秒/总30秒；算法原子进程/共享采集槽不改。
- POST /internal/v1/market/alert-evidence/prepare；GET /operations/{uuid}及 /versions/{uuid}。统一ApiResponse；同操作同内容返回已有回执，不重新采集；改内容409。RUNNING到期FAILED，不盲目复活。
- 仅最新应到期交易日允许AVAILABLE；历史请求SKIPPED_STALE，未来/非法日期拒绝。未知日历/不可用RAW/租约到期为受控FAILED；不猜测开市，不给旧行情贴今日标签。
- priceState VERIFIED/MISSING/SUSPENDED；changeState VERIFIED或MISSING_PREVIOUS_CALENDAR/MISSING_PREVIOUS_RAW/SUSPENDED/SOURCE_UNAVAILABLE/SOURCE_INVALID。只有VERIFIED才提供对应有效数字。复权值必须来自同一次来源、严格核对RAW OHLC/量额/状态/日期及日历，不能跳过应有缺失日。采集失败仍可发布已核验RAW价格证据。
- Evidence固定instrument/provider/definition/tradeDate/previousTradeDate/rawVersionId/rawContentHash/calendarVersionId/calendarHash/calendarDays/priceState/rawClose/changeState/previousBackwardClose/currentBackwardClose/sourcePayloadHash/capturedAt/id/contentHash。通过精确BigDecimal保存字符串，不用展示舍入值比较；这里只提供分子/分母，不做阈值触发。
- 独立日历默认7天TTL，不足时仅可回退完整RAW日历；来源时间距当前5分钟内且允许前漂5秒。配对必须恰好两个紧邻交易日；停牌不计算日变化。保留证据，RAW/预测/模型/规则和邮件不修改。

## Review Focus
未复权价与除权可比价混用；上一交易日缺失/停牌、长假跨期；源日历不一致/重复/截断/数值非正及非法精度；源失败不阻断价格；过期/未来请求及租约提交；并发重复操作、冲突回执、服务代理；鉴权和请求响应界限；不意外发布行情/预测或发送邮件。

## Task 1 — Validation and collector
- [x] 写AlertEvidenceValidatorTest：手算除权样本RAW100→50但后复权100→100；缺日/停牌、阈值消费者所需精确数字、日历不一致、非法候选身份/时间/价格；RED后实现Records/Validator/Collector接口。
- [x] 写真实HTTP collector测试：发送正确范围/令牌、响应非法类型/过大受控；RED后实现refresh/AlertEvidenceCollectorClient，使用独立历史alert-collect并回归原forecast-collect。
- [x] 市场非MySQL测试通过，小步提交。

## Task 2 — Persistence and internal API
- [x] V10独立证据/操作表；Store/Service/Controller。MySQL测试先RED：幂等/冲突、未知/过期回执、最新与过期、失败保留价格、固定版本读回/并发；证据和AVAILABLE回执同事务，禁止过期提交。
- [x] HTTP先RED覆盖匿名/错误令牌、严格字段/UUID/日期、4096B；扩展现有过滤器仅新路径限额。测试夹具只清理隔离测试库。
- [x] 受影响全模块及MySQL回归通过，小步提交。

## Task 3 — Review/deployment/evidence
- [x] 一次独立全切片审查，Important先RED再修复并回归，Minor记入待办。
- [x] 全后端verify；只部署market和algorithm。真实三股最新日期配对、同操作重放/重启读回、日变化独立Decimal核对；来源暂不可用如实记录，不造真实成功。
- [x] 原RAW头/预测/模型/邮件/规则保留；六服务健康，内部401及网关404；更新文档/执行账本/下一片B2调度与通知规划，仅本地提交。

Preflight: Task1日期/数字字符串契约供Task2持久化，客户端读取ForecastCandidate但只核验两日，必须显式拒绝缺日/多日/非法身份。Task2发布永不改RAW头；历史Evidence不因HEAD变化改写。原有Calendar GET审计契约不变。

## 审核纠正
对照verification-alert-source-20261010.md后，取消继承预测接口未来日历要求。专用alert-collect仅查询上一交易日至当日的历史日历；ForecastSource新增请求解析扩展点，原预测默认校验与行为不变。计划中的MISSING_CURRENT_RAW补充到changeState枚举，其他失败语义不变。审查Important三项均使用实际RED测试复现后修复：数字精度、RAW完整日历回退、响应体总时限。

验收：verification-sdd006b1.md。389项后端/76项算法、三股真实配对、重启一致及原数据保留通过；Minor无新增，既有事项保留。
