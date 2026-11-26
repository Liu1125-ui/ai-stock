# SDD005E2 单股AI行情分析设计

日期：2026-10-07。状态：E2a已实现验收；E2b已实现部署并完成一次综合生成、保存与刷新真实验收，见各阶段核验文件。
依据：需求分析正文V1.9的FR-02/FR-09、现有MarketAnalysisPage与SDD005E1。保持13项业务、五业务服务、ADMIN/USER，代码由Codex实现，不冒称CodeArts产出。

## 1 目标与阶段

在现有单股“查看分析”页，针对用户选定的三个月、半年或一年行情生成通俗、可核对、可保存的分析。默认三个月，行情图和指标明细继续可独立查看。分析解释已经保存的数据，不重新预测，不修改模型权重，不给交易指令。

交付分两段：E2a先完成行情、MA/MACD/RSI与风险说明；E2b补用户主动选择的本人已有预测及当前验证引用。E2b属于同一业务的后续扩充，并未从原需求删除。开放问答、股票比较和持仓分析按既有需求后续推进，不声称E2a实现整个FR-09。

## 2 用户流程

首页与自选股已有“查看分析”入口 → /market/:id → 加载并展示行情日期、来源、RAW口径、版本和图表 → 用户主动点击“生成AI分析” → 服务端取同版本同范围行情、整理事实、调用MaaS、核验并保存 → 展示行情概览、指标解读、风险与限制。

不进入页面就自动扣费调用。刷新分析记录只读数据库；切股票/范围后清空上一对象内容并丢弃旧请求回包。无有效数据时禁用生成并说明原因。旧分析可阅读并标记其行情版本与范围，不能把旧版日期标成今天。

## 3 数据与科学边界

服务端只接受股票代码、查询范围、行情版本及操作UUID，不接受价格、指标、用户ID、提示词或调用地址。MS02统一计算指标；MS03使用这些结果，不在前端重算或让MaaS重算。

选定范围中的交易日必须有序、唯一，数值合法，股票、版本、范围与请求一致。引用最后一个有效交易记录的收盘、MA5/10/20、DIF/DEA/MACD与RSI14；字段为空时明确样本不足，不能以零替代。指标公式版本tech-v1随快照保存。范围涨跌如展示，由程序用有效起止价计算；不能把它称为持有实际收益。

RAW历史行情受除权除息影响，分析必须引用这一限制；不与统一原点股本口径预测拼成连续实盘收益曲线。STALE/UNKNOWN行情可以解释已保存历史，但必须引用截止日期和新鲜度限制；不能称为实时行情。该政策不改变新预测需有效近期输入的既有规则。

指标信号仅解释确定性关系：收盘与MA、DIF与DEA、MACD正负、RSI的数值位置。不能由一日数值推断金叉/死叉事件；若要说明穿越，必须同时核对相邻有效日并单独定义规则。RSI70/30仅为常用阈值解释，不是买卖规则，不保证效果。

E2b必须验证所选预测属于当前用户、股票一致、成功且未隐藏；不自动选管理员可见的其他用户记录。预测原点与当前行情日期不同的情况分别注明。缺少预测不影响E2a历史分析。

## 4 服务与接口边界

MS02拥有行情，MS03拥有分析记录，无跨库联表或外键。新增GET /internal/v1/market/analysis-evidence/{instrumentId}?from=YYYY-MM-DD&to=YYYY-MM-DD&versionId=UUID，由X-Service-Token认证，复用MarketQueryService.daily固定版本取数；匿名与错误内部令牌均拒绝。响应为统一ApiResponse，提供同现有DailyResponse字段。MS03设置完整响应截止与大小上限、禁重定向，仅访问配置的MS02地址；用户不能指定URL。

MS03公开接口：POST /api/v1/stock-analyses，body为operationId、instrumentId、from、to、marketVersionId；GET /api/v1/stock-analyses?instrumentId=...&from=...&to=...&page=1；GET /api/v1/stock-analyses/{analysisId}。列表每页10条，页码接受1至10000（当前可配置版本策略）。服务端从会话取userId，GET只查本人，POST有CSRF及Origin校验，网关仅透传既有会话与追踪信息。

POST返回operationId、state(RUNNING/SUCCEEDED/FAILED)、analysisId和安全errorCode。相同操作号相同请求回放已有状态；相同操作号不同条件返回409。输入JSON额外字段拒绝，股票及UUID、日期、范围上限明确校验。内部行情接口不接收用户自定义提示词。

## 5 MaaS与结果核验

复用既有Huawei客户端的TLS、35秒截止、128KiB响应上限、错误分类和密钥配置。分析采用独立提示版本grounded-stock-analysis-v1，不能修改grounded-prediction-v2的预测解释语义。实现采用同一MaasClient的generateAnalysis(facts)明确入口，保留旧generate(facts)与预测提示，复用同一有界HTTP传输；通过独立分析与E1回归核验。

固定三段overview/readout/risk。关键数字、日期、股票代码、方向与指标关系使用服务端事实占位符；程序校验结构、引用、数字与不支持的判断，纯文本渲染。正文不堆模型编号，依据面板保存数据来源、范围、截止日期、RAW口径、公式版本、提示版本、文本模型、上下文hash。自然语言质量人工按案例检查，不能以模板通过宣称完全消除幻觉。

云端失败、冻结、限频、超时或核验不通过，只记录安全错误码，不展示未经核验正文，不覆盖已有分析。无自动连环重试。未知状态复用原操作号查询/回放，禁止重复点击生成新收费请求。

## 6 存储与并发

MS03新增迁移V6。stock_analysis_result保存analysis_id、user_id、instrument_id、range_from/to、market_version_id、context_hash、context_json、paragraphs_json、created_at（提示版本与文本模型随不可变context_json保存，避免重复字段不一致）；成功结果不可覆盖。

stock_analysis_operation保存operation_id、user_id、request_hash、context_hash、固定context_json、state、analysis_id、error_code、created_at、lease_until、finished_at。跨库市场版本只作为引用UUID，不建立外键。user_id来自已验证会话。

预测解释与行情分析共享同一全局MaaS调用槽，避免各自一个槽导致实际并发超限。需把既有槽及租约处理抽成通用MaasOperationGate，存操作类型和ID，保留E1旧操作查询与过期恢复行为；数据库事务只做领取/保存，网络调用在事务外，完成时校验租约所有权防止迟到响应覆盖后续操作。

同一用户相同固定上下文可缓存成功结果，必须包括股票/范围/版本/公式/提示版本/模型以及引用预测与验证版本（E2b）。不能跨用户缓存私人分析结果。失败后用户明确重试创建新尝试并保留原失败证据。

## 7 文件组织与实施顺序

MS02 quotation/新增内部Evidence控制器及契约测试，复用统一计算实现；MS03新增analysis/目录及README：AnalysisFacts、AnalysisService、AnalysisRepository、AnalysisController、AnalysisMarketClient分别负责事实、编排、存储、公开接口、内部取数。现有explanation/共享调用槽逻辑抽至maas/目录并配README，避免复制整套客户端/锁。

前端新增api/analyses.ts和pages/market/StockAiAnalysisPanel.vue及测试，嵌入MarketAnalysisPage。网关按现有认证转发方式新增analysis路由。不另造一个分析首页，不在行情首页堆全文。

先做事实/指标状态与模板测试，再做内部取数、调用槽与本人记录API，最后完成页面和真实部署验收。每个完整功能小步提交，不提交.env或密钥，无remote不push。

## 8 验收检查

- 固定指标样例：全部数值来自MS02；空指标、平盘、零MACD、RSI边界、陈旧行情与RAW跳变提示准确。
- 身份：未登录401，缺CSRF拒绝，普通用户和管理员均不能读别人记录，内部证据接口错误令牌拒绝，注入内容不能改变数据归属。
- 一致性：版本切换期间结果绑定请求版本；同操作回放不重复调用，冲突409；晚响应不能写新操作，过期租约可恢复，E1与E2不会并发占槽。
- 故障：无数据/不可用版本在调用前阻止；云端冻结/限频/慢响应/超大响应/坏JSON/独立数字/错误方向拒绝并保留旧结果。
- 页面：股票或范围切换丢弃旧回包；纯文本；刷新只读；默认三个月；证据可展开；失败可见，历史数据继续可读。
- 真实验收：使用已登录页面受控生成一份，核对日期、来源、指标、正文及保存后读取；单次成功不代表准确率或持续可用保证。

## 9 自查结论

与FR-02/FR-09一致，阶段分工未删除既有业务；已补内部取数认证、固定行情版本、RAW口径、用户隔离、共享并发控制、收费调用边界与来源核验。E2a不冒称已实现开放问答或预测融合解释。用户已确认设计与实施计划；实际实施状态与测试结果见sdd005e2a-progress.md及verification-sdd005e2a.md。
