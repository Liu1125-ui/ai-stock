# SDD005E3 单股有依据问答设计

日期：2026-10-07。状态：用户已确认设计，2026-10-09复核补充实现细节；2026-10-09已实现并部署V7；328项后端/78项前端及构建通过，浏览器只读验收通过，真实问答待授权，未调用MaaS。

## 1 用户目标与现有依据

用户已同意在完成行情与预测综合分析后，增加能继续询问原因、指标、风险与验证情况的AI问答。回答应自然、简洁，关键数据与系统一致。需求FR-09/AC-09仍包括股票比较、本人持仓和已有预测；本阶段只落地单股问答，后续范围保留，不因本设计缩减13业务、五微服务或ADMIN/USER。

已完成的E2a/E2b提供本人保存的分析、不可变行情事实、可选本人预测及生成时验证依据。正式代码位于backend/stock-prediction-service的analysis/explanation/maas及frontend/src/pages/market。现有ExplanationTemplate严格核验三段模板、占位符与refs，MaasOperationGate在MS03数据库统一限制付费请求；当前只有PREDICTION_EXPLANATION与STOCK_ANALYSIS两种操作。

实际代码由Codex实现；CodeArts代码生成来源未经核实。需求正文中的历史工程状态不能当作当前实现状态。

## 2 方案比较与推荐

方案A：只提供固定问题按钮并用程序返回说明。实现简单，可保证固定事实，但不能充分满足自由提问和自然回答的要求。

方案B（推荐）：用户自由输入或点击常用问题，每次绑定本人一份已保存分析，调用MaaS组织回答，程序替换并核验事实。沿用现有证据、权限、输出核验和调用槽，增加独立问答记录；容易追溯问题与答案依据。

方案C：直接提供长对话、任意股票或全网问答。需要额外的上下文治理、数据权限与外部信息核验，会扩大本阶段范围；暂不实施。后续可在独立设计下扩展，不把本阶段策略写成永久限制。

选择B。每次独立问答，不将历史回答当作新事实，不引入搜索、自动训练或重新预测。用户可继续提问，但每个问题仅使用所选分析和当前问题。

## 3 页面与用户流程

在股票详情页已保存分析下增加独立StockQuestionPanel组件，父面板传入当前analysisId和instrumentId。没有成功分析时显示“先生成或选择一份已保存分析，再提问”；现有行情、预测入口正常使用。

用户可以点击四类常用问题：指标说明、预测走势与原点的关系、风险与限制、是否已有实际验证。按钮只填写输入框，点击“发送问题”才发起生成。默认不自动调用，查看或刷新记录只GET。

输入为1—300个Unicode码点，trim并统一NFC，拒绝控制字符；不截断用户问题。页面注明回答基于所选保存分析及截止日期，输入提示避免提交密码和密钥。问题发送后固定analysisId/问题/operationId，正在处理或状态未确认时不能以新问题替换原请求。发送新问题必须形成明确的新操作。

回答显示用户原问题、简短回答、依据说明及限制；关键数字来源可展开，标注分析生成时间、行情截止日期、预测原点和生成时验证版本。正文不堆UUID和模型编号，详细依据面板保存这些信息。历史问答按当前analysisId每页10条，最新优先；切分析或股票不显示上一对象的回答。账号变化和卸载使旧异步响应失效。

选择另一份保存分析时提示对应的问答依据变化；历史问答保持原归属，不迁移到新分析。新行情或验证版本发布不会改写既有回答；想使用新证据，需选择新生成的分析再提问。不能以“当前验证”指代旧分析冻结的验证版本。

## 4 证据与问题范围

服务端通过AnalysisRepository.get(userId,analysisId)读取本人记录，校验context、股票与结构。ADMIN同样只能引用本人分析；不能借管理员权限取普通用户分析、持仓或预测。

从分析context复制facts、行情/公式/预测/验证版本、生成时模型信息和hash，不依赖当前行情头、模型推荐指针或新预测结果；不得再次获取实时数据悄悄替换。问答context记录sourceAnalysisId/sourceContextHash、sourceCreatedAt、独立questionPromptVersion、实际文本模型和问答facts。新增问答提示版本grounded-stock-question-v1，不改E1/E2既有提示含义。

允许回答：已保存行情数值与指标位置；MA/MACD/RSI的基础含义；已有预测五目标、相邻预测走势与相对原点区别；分析保存时的实际验证状态；来源、日期、口径和风险限制。尚无验证样本时只能说明不能评价准确性，不能推断准确率或将指标当准确率。

程序补充版本化固定知识事实QA_MA_DEF、QA_MACD_DEF、QA_RSI_DEF，解释平均收盘价、短长指数平均差及信号线、近期涨跌强弱。不能由单日位置判断金叉/死叉；不在本阶段补造未保存的时间序列事件。固定知识文本须单元验证并与tech-v1定义核对，不让模型自行改公式。

超出范围：其他股票比较、持仓盈亏、新闻公告、实时行情、未保存周期预测、个性化交易指令、未来收益保证。保留总体业务中的比较/持仓后续计划，只在本轮明确资料未接入。请求他人数据或要求忽略规则不会改变服务端取数或权限。

用户自由问题属于不可信输入，单独放用户消息，不拼入系统提示、不接受提示词/调用地址/事实参数。发送MaaS的内容限当前问题和白名单股票事实，排除邮箱、会话、用户ID、密码、内部服务令牌及原始私有记录。问答原文只保存在本人业务记录，不打印在应用日志。

## 5 模型输出与科学边界

一次MaaS调用输出严格根对象status/topic/paragraphs。status为ANSWERED或OUT_OF_SCOPE；topic为INDICATOR/PREDICTION/VERIFICATION/RISK/LIMITATION。ANSWERED的paragraphs结构继续使用overview/readout/risk，每段kind/template/refs，提取为仅paragraphs的对象送ExplanationTemplate；OUT_OF_SCOPE必须topic为LIMITATION且paragraphs为空，返回程序固定的范围提示，不展示未经核验的模型拒绝正文。字段多余、类型错误、未知status/topic均拒绝。

知识与数字、日期、代码、价格、方向、验证指标全部使用服务器事实占位符，refs精确对应。使用现有500字符模板限制和安全过滤，问答额外禁止未经核验的金叉、死叉、突破、穿越及实时断言。问题原文不作为答案事实占位符，避免借替换机制绕过输出核验。

各主题必须有对应事实：INDICATOR至少一个QA指标定义及一个当前指标状态；PREDICTION必须有PRED_ORIGIN_DATE/PRED_TREND/PRED_RELATIVE_ORIGIN/PRED_LIMIT；VERIFICATION必须有PRED_VERIFICATION/PRED_LIMIT；RISK与LIMITATION至少BASIS_LIMIT/FRESHNESS_LIMIT。所有可接受回答说明AS_OF；涉及预测必须说明PRED_CONTEXT_RELATION。不引用预测的分析缺相关事实时，模型须返回合法OUT_OF_SCOPE，页面显示程序固定的资料不足说明，不能补取未选择的预测；模型错误返回ANSWERED则按AI_OUTPUT_INVALID拒绝，不将非法正文转换成成功结果。

服务端可在调用前拒绝严格结构、权限、明显缺失条件，但不声称能仅靠关键词穷尽任意语义或完全阻止幻觉。MaaS的主题分类和回答是否贴题仍需人工案例评价。即使数字核验通过，也不能将其包装为事实正确性全部保证；错误主题或不贴题在评审样例中记录并修正。

OUT_OF_SCOPE是成功处理的范围提示，与调用超时、输出非法的FAILED区分。MaaS输出非法时不保存可读答案，只记录安全errorCode和失败尝试；原分析、预测和问答历史不变。

## 6 API与权限

MS03新增/api/v1/stock-questions路由：

- POST根路径：严格字段operationId、analysisId、question。UUID合法；UTF-8请求体不超过2048字节；question规范化后1—300码点。客户端不得指定userId/instrumentId/市场版本/facts/model/调用地址。成功返回operationId、state、answerId及安全errorCode。
- GET根路径：analysisId与page，页码1—10000，每页10条；返回本人该分析的问答摘要、latestAttempt、configured。latestAttempt包含原analysisId、规范化question和operationId，便于恢复固定请求。
- GET /{answerId}：本人答案、问题、status/topic及保存证据。
- GET /operations/{operationId}：本人原操作状态，只读。状态未确认时优先该GET，不新增云调用。

所有接口采用现有Cookie会话、统一ApiResponse和追踪格式；POST执行CSRF/Origin校验。不存在与非本人资源一致返回404，避免泄漏归属；非法输入400，冲突409；网关不透传伪身份头，不开放内部路径。

同op、同规范化question、同analysisId重放原回执，优先于模型配置及依赖检查；同op变问题或分析409。终态失败不自动重试，用户明确再次发送才用新op保留失败历史。相同问题、源context、问答提示版本和文本模型的成功结果可仅在同用户内复用；不同版本不能缓存合并，不跨用户共享。

## 7 存储、调用槽与部署

MS03迁移V7__stock_questions.sql新增stock_question_result与stock_question_operation，两表只归MS03。

result保存answer_id、sequence_id、user_id、analysis_id、question、question_hash、context_hash、context_json、status、topic、paragraphs_json、created_at；同用户context_hash唯一。operation保存operation_id、sequence_id、user_id、analysis_id、question、request_hash、context_hash、固定context_json、state、answer_id、error_code、created_at、lease_until、finished_at。question_hash及request_hash取确定性规范化UTF-8值；缓存context包括实际模型/提示版本/问题和源分析hash。源analysis FK只在MS03本库；不跨服务联表或建外键。历史结果不可覆盖，失败不伪造answer_id。

MaasOperationGate增加STOCK_QUESTION并在过期恢复映射到问答操作表，保持E1/E2/问答共用全局一个槽及50秒租约。事务仅领取和提交，HTTP在事务外；完成时校验槽类型、op及未过期租约，迟到响应不能覆盖新操作。客户端保留35秒完整响应截止、128KiB响应上限、TLS地址限制及禁止重定向。

网关新增明确方法/路径白名单和StockQuestion代理；Nginx为新路由设置与现有分析一致的190秒预算，其他API预算不扩大。部署只涉及MS03/gateway/frontend，保留数据库卷和原记录；迁移后检查E1/E2原哈希不变，六健康、匿名401及问答范围正常。

## 8 文件组织

新建backend/stock-prediction-service/src/main/java/com/aistock/prediction/question/README.md，说明业务作用、实现方法与证据边界；类按职责分QuestionInput、QuestionFacts、QuestionAnswerTemplate、QuestionRepository、QuestionService、QuestionController。

复用AnalysisRepository的本人证据查询和不可变context；MaasClient增加明确generateQuestion(question,facts)入口，HttpMaasClient单独提示与用户消息；MaasOperationGate增加类型与恢复。避免复制HTTP安全传输、数字渲染或认证整套实现。

网关新增QuestionApiProxyController及相关契约测试，沿用ForecastPublicClient与ProxyHeaderPolicy。前端新建api/questions.ts、pages/market/StockQuestionPanel.vue及测试；StockAiAnalysisPanel把当前保存分析传给子面板，旧分析生成行为不改。新建目录配README，已有目录补充模块说明，不堆空类与目录。

## 9 验收与实施顺序

先写事实/输出合同与MySQL持久化测试，再接用户接口、共享槽、网关和前端，最后顺序完整回归、独立审查、部署验收。隔离MySQL测试不可并行，模型HTTP测试用假服务，不在自动测试里付费调用。

必须覆盖：本人/跨用户/管理员越权；同op冲突与只读恢复；仅行情时无预测依据；零实际样本、已有验证和旧验证版本；NFC码点边界及控制字符；未知事实/数字/方向/JSON字段；恶意问题无法改权限或提示；OUT_OF_SCOPE独立展示；新行情/模型变化不改原依据；三种MaaS操作共用槽、过期恢复和迟到写入；前端换账号/分析/股票丢弃旧响应，输入恢复不变，刷新不重复收费。

自然语言案例分别核对贴题、数字、日期、方向、验证口径与易读性，不能只跑字符串断言就称语义完全正确。真实云验收需用户明确批准调用数量后再执行；本次设计阶段不调用。固定问题先本地测，若实际云调用失败，记录原因并修正，未经授权不连环付费重试。

## 10 自查结论与下一步

已对照FR-09/AC-09、E2设计及当前AnalysisService、ExplanationTemplate、MaasClient、MaasOperationGate核对。本设计保持单一证据、本人权限、旧问答不改写、数值由程序提供；补齐自由提问的输入隔离、语义限制、持久化、GET恢复、统一付费并发和新Nginx路由。没有把旧行情或旧验证称为最新，没有把通俗说明等同于预测正确性。

设计已自查，代码尚未开始。用户审阅这份设计后编写逐任务实施计划，再按已有本会话直接执行方式落实。实验、每周模型更新、比较/持仓、预警与统计仍在原范围，不因本轮问答验收宣称整个系统完成。

## 2026-10-09计划复核同步

没有修改业务范围。补充实现约束：①未知状态GET优先，暂时404保留原op，用户明确恢复仅重放同原POST；②过期请求可明确结束，原POST只做租约失败清理，不重新调用模型；③同标签页恢复快照仅存当前用户的固定问题和请求号，终态/退出/切账号清除，存储失败阻止新发送；④新问答路由同步前端401身份失效处理；⑤指标定义与同类状态匹配，任意预测引用须带原点、验证及口径限制；⑥同op跨用户404、本人不同参数409；⑦新接口局部拒绝重复JSON字段与尾随内容；⑧缓存必须包括sourceAnalysisId避免答案挂错分析。规范化、测试命令和对应测试详见实施计划2026-10-09复核补充，不把文档审核记成产品验收。

## 实际验收补充（2026-10-09）
v1首次真实问答失败；提示v2修正并经128项受影响测试及部署核验后，已验证一份v2真实成功回答的保存、记录刷新和整页重载。原预测和分析不变，本轮未重复云调用。详见docs/verification-sdd005e3.md，历史规格中的v1提示为设计基线，运行版本为grounded-stock-question-v2。
