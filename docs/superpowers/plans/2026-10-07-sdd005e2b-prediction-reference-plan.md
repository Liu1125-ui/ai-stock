# SDD005E2b 预测引用实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** 单股行情分析可主动引用本人已有五日预测与固定验证版本，保留纯行情模式。
**Architecture:** MS03本地读取本人预测/验证，合成固定事实后复用E2a共享槽、MaaS客户端与结果存储；Vue主动选择，网关精确增加候选GET。
**Tech Stack:** Spring Boot/MySQL/JUnit5、Vue3/TypeScript/Vitest、既有华为MaaS。
**Spec:** docs/superpowers/specs/2026-10-07-sdd005e2b-prediction-reference-design.md，用户以“下一步”确认继续该设计。用户已确认本计划，在当前会话直接执行。
**Execution:** 当前会话直接顺序执行，保持codex/sdd004-market-refresh，不新建工作树或子对话；无remote不push。生产卷与现有用户保留。

## Global Constraints

13业务/五业务服务/ADMIN和USER不变；不重新预测或训练；默认不引用预测。最大行情范围十二个月，RAW/tech-v1。MaaS35秒完整响应预算、128KiB、共享50秒租约，网络不占数据库事务。候选每页10条、页码1至10000。POST最大2048字节。纯行情提示grounded-stock-analysis-v1，预测引用grounded-stock-analysis-prediction-v1。密钥只读取忽略配置，不打印或提交。

## Review Focus

- 旧五字段请求hash与旧操作可恢复，新增null预测不能改变原请求身份。
- 同一原点下预测序列上行但全部低于原点，可同时成立，不写成已上涨收益。
- 选择期间行情/验证更新与预测隐藏，旧操作固定依据继续可读，新请求重新检查权限。
- 只过滤最近一页会漏可用预测；必须数据库按本人和股票过滤后分页。
- 未确认操作恢复原预测选择，切换账号/股票的旧回包不得显示给新对象。

## Task 1 本人候选和固定预测事实

**Files:** Create MS03 analysis/AnalysisPredictionSource.java、AnalysisPredictionFacts.java；Create tests analysis/AnalysisPredictionSourceTest.java、AnalysisPredictionFactsTest.java；Modify analysis/README.md。源码与测试根为backend/stock-prediction-service/src/main/java/com/aistock/prediction和src/test/java/com/aistock/prediction。

**Interfaces:** `AnalysisPredictionSource.options(UUID user,String instrumentId,int page):JsonNode`；`AnalysisPredictionSource.read(UUID user,String instrumentId,UUID predictionId):Reference`；Reference保存ForecastRecords.Record和JsonNode currentVerification。`AnalysisPredictionFacts.combine(AnalysisFacts.Facts market,UUID predictionId,Reference source,String provider,ObjectMapper mapper):AnalysisFacts.Facts`。

- [ ] 在隔离MySQL样例设置A/B用户、两股票、hidden/FAILED/RUNNING和足够分页成功记录；断言options只返本人当前股票的有效成功记录，total按同过滤条件。ADMIN按自己userId查询。测试read隐藏/他人404、错股票/失败/非五日拒绝，provider未调用。
- [ ] 事实测试用既有五点fixture，断言PRED_TREND与PRED_RELATIVE_ORIGIN独立；null验证、零样本、部分/完整核验、指标缺失不变0；行情原点日期差异和口径限制可见。相同固定context hash稳定，验证版本或预测ID变化hash不同。
- [ ] 运行`./runtime/focus-d3b3.ps1 -Modules 'stock-prediction-service' -Tests 'AnalysisPredictionSourceTest,AnalysisPredictionFactsTest' -Log runtime/e2b-task1-red.log`，确认实现缺失RED。
- [ ] Source用MS03 JdbcTemplate执行同条件COUNT及分页SELECT（所有筛选在SQL）；读取按user和predictionId检查完整快照，复用VerificationQueryRepository当前版本；不调用市场或算法服务。Facts复用ExplanationFacts.build，全部预测facts以PRED_前缀命名并保留不可变原context，新增清晰时间/口径关系事实。外部异常转换BusinessException。
- [ ] 同命令保存green日志，检查无失败；更新README，小步提交Task1文件。

## Task 2 扩展输入、幂等恢复与模板

**Files:** Modify analysis/AnalysisInput.java、AnalysisController.java、AnalysisService.java、AnalysisRepository.java、AnalysisConfiguration.java、README.md；Modify explanation/HttpMaasClient.java；Modify tests modelcatalog/AnalysisHttpTest.java、modelcatalog/AnalysisMysqlTest.java、analysis/AnalysisServiceTest.java及explanation/MaasClientTest.java。

**Interfaces:** AnalysisInput追加nullable UUID predictionId，保留旧五参数构造以兼容现有测试和调用。Service注入AnalysisPredictionSource，暴露options(user,id,page)。POST只接受五必填字段及一个可选predictionId，明确null/缺省规范化。回执追加predictionId，旧回执为null；固定context为完整恢复依据。

- [ ] RED：旧五字段、null、有效六字段兼容，非法UUID/空字符串/未知字段400，匿名401/缺CSRF403，隐藏和越权调用计数0。候选路由身份先于SQL，错误股票/页码400。
- [ ] RED：同op相同选择一次调用，不同选择409；旧E2a请求hash保留，重复请求在取依赖前返回；当前验证更新后原op返回旧终态，新op捕获新版本；旧行情/E1记录保持。
- [ ] 输入解析明确检查字段集合与类型，保持2048限制。Service先replay再依赖，预测为null用原Facts，否则Source.read+combine。hash纯行情保留原拼接算法，预测模式追加规范化UUID；回执从context预测对象读取原选择。数据库V6结构不变，记录不覆盖。
- [ ] RED模板：引用预测但遗漏原点、趋势、原点关系或验证引用拒绝；risk缺PRED_LIMIT拒绝；独立预测数值/错误方向/交叉/交易建议拒绝。HttpMaasClient本地假HTTP服务器证明E1、纯行情和引用预测三提示不串用，无真实收费。
- [ ] 固定预测模式system提示，必需引用PRED_ORIGIN_DATE/PRED_TREND/PRED_RELATIVE_ORIGIN/PRED_VERIFICATION；保留行情readout至少一指标关系，risk原两限制及PRED_LIMIT。分段长度沿用模板，正文允许引用事实而不生成新数值。
- [ ] 顺序运行聚焦`-Tests 'Analysis*Test,Explanation*Test,MaasClientTest'`保存green日志，检查新旧合同；更新README提交Task2。

## Task 3 页面选择、原请求恢复与网关

**Files:** Modify backend/stock-gateway/src/main/java/com/aistock/gateway/ProxyHeaderPolicy.java和AnalysisApiProxyController.java及测试AnalysisProxyTest.java；Modify frontend/src/api/analyses.ts、pages/market/StockAiAnalysisPanel.vue及.test.ts、README.md。

**Interfaces:** `predictionOptions(instrumentId,page=1):Promise<{items:PredictionOption[],total:number,page:number,size:number}>`，PredictionOption含predictionId、originDate、createdAt、artifactId、targetFrom/to。POST input增加可选predictionId；result context增加可选prediction引用对象，兼容旧记录。

- [ ] RED网关：GET /stock-analyses/prediction-options可转发，仅page/instrumentId白名单；POST该路由404，伪身份头丢弃，内部路径不开放。
- [ ] RED Vue：初次/选项切换/刷新只GET；显式生成携带选择；没有候选保留纯行情；候选失败不暗中清空选择；pending恢复原predictionId，不能用新选择修改同op。切股票/账号/卸载使旧异步响应失效。
- [ ] 添加选择器及候选分页，SQL结果只含本人同股。生成未确认时禁用选择更改，状态未知保留原输入；恢复RUNNING用回执原选择。生成失败保留历史分析。证据显示预测原点、五目标参考点、模型/输入/验证版本及原预测链接；三段引用模式标题调整，旧E2a正常读取。当前选择和保存结果不同提示，不伪装选择即已生成。
- [ ] 运行`pnpm --dir frontend test`和`pnpm --dir frontend build`，聚焦网关`./runtime/focus-d3b3.ps1 -Modules 'stock-gateway' -Tests 'AnalysisProxyTest' -Log runtime/e2b-gateway.log`。检查退出码，提交Task3。

## Task 4 审查、回归和实际验收

**Files:** Create docs/sdd005e2b-progress.md、verification-sdd005e2b.md、evidence/sdd005e2b-20261007.json；Modify implementation-status.md、pending-items.md。

- [ ] 一次独立只读审查按上述Review Focus检查；如有问题先RED复现再一次集中修复，不循环复审。记录实际开发者、工具及限制，不称CodeArts已生成。
- [ ] `./scripts/check-auth-task1.ps1 *> runtime/e2b-final-backend.log`顺序完整回归，不和其他MySQL测试并行；前端test/build顺序运行，仅汇总XML根计数，不输出环境属性。真实调用不放进测试。
- [ ] 部署前保存原预测/模型及E2a结果哈希；只构建/重启MS03、gateway、frontend，不重置DB。验证六健康、匿名401、候选本人隔离、nginx长预算及迁移不变，部署后哈希比较。
- [ ] 已登录页面选SH600030本人成功预测，检查原点和五目标展示，显式生成一次；核对三段事实、验证样本不足/现有指标、实际与参考口径区分，刷新只读保存。失败保存安全错误、先定位，不连环付费重试；若额外付费范围未获授权先交用户决定。
- [ ] 保存可追溯脱敏证据、真实验收缺口与待办，更新已实现状态；最终小步提交，不push。开放问答、比较/持仓、预测误差区间、账号警告专项排查仍保持待办。

## 自查与执行入口

本计划基于已有E2a流实现扩展，先事实/权限、再恢复/模板、再页面、最后验收。保持纯行情旧hash与不可变结果，预测隐藏不删除分析。用户审阅此计划后，本会话直接执行，不要求重选已确定技术或重复确认普通细节。
