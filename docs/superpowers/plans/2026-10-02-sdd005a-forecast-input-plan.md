# SDD-005A 版本化预测输入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。用户已指定由本代理负责，直接按任务执行，最终一次独立审查。

**Goal:** 提供真实来源、固定版本、兼容六维模型的60行输入及未来五个目标交易日。
**Architecture:** Python仅产生配对候选；MS-02独立不可变快照绑定原始版本、转换和未来日历，不改原始head。发布/回执本地事务，查询只经内部接口。
**Tech Stack:** FastAPI/Baostock、Java17/JdbcTemplate/Flyway/MySQL8。
**Spec:** docs/superpowers/specs/2026-10-02-sdd005a-forecast-input-design.md。

## Global Constraints

- 五服务各拥有自己的库；不跨库读写。
- 未来实际日线仍禁入；未来日历为单独冻结清单。
- 60行六维量价、五个目标交易日；未复权价不代替复权输入。
- 同来源定义与版本绑定；数据不足、错配、失效明确拒绝。
- 不发布模型、不生成用户成功预测，不保存凭据。
- 固定目录/现有codex分支、测试数据库独立、小步提交。

## Review Focus

1. 采集返回部分或错配日线：Python严格配对，Java与已发布版本核对。
2. 日历有缺口或目标恰逢长假：逐自然日验证，按源开市日选五日。
3. RAW更新后旧输入误配：默认只取当前head，显式旧input固定原版本。
4. 并发同内容/同键不同内容：本地事务、唯一键、操作锁和稳定哈希。
5. 认证前解析/大请求：新路由加入前置过滤，Python先验证身份和体积。

## Task 1：配对来源候选

**Files:** algorithm/app/forecast/{provider.py,worker.py,README.md}；algorithm/app/main.py；algorithm/tests/test_forecast_source.py。
**Consumes:** 已核验 Baostock query_history_k_data_plus/query_trade_dates。
**Produces:** ForecastSource.collect(request) 返回 `{state,candidate,errorCode}`；`AISTOCK_FORECAST:`唯一工作器标记。

- [x] 先测试新HTTP路由未认证畸形请求401、SDK配对价与单位、部分/错配来源拒绝、未来日历连续、超时与单槽失败。
```python
r=TestClient(app).post('/internal/v1/market/forecast-collect',content='{')
assert r.status_code == 401
```
- [x] Docker内pytest观察RED，记录runtime/sdd005a。
- [x] 实现只读候选/认证/限额/进程边界，保留旧采集路由。
- [x] 全Python测试GREEN；小步提交此功能。

## Task 2：不可变快照与查询

**Files:** market/forecast/{ForecastCandidate.java,ForecastInputService.java,ForecastInputController.java,README.md}；migration/V5__forecast_inputs.sql；CandidateRequestFilter.java；test/forecast/ForecastInputMysqlTest.java、README.md；MarketDatabaseFixture.java。
**Consumes:** Task1候选，已有MarketVersionRepository及RawBar/TradingDay。
**Produces:** publish(candidate) 返回Publication(inputVersionId,inputHash,replayed)；window(instrumentId,inputVersionId,window,horizon) 返回Window，内部路由遵循规格。

- [x] 编写真实MySQL测试发布60行/五目标日、拒绝错配/未来价/缺日历/缺因子、重放与并发去重、head更新不改固定快照、默认STALE拒绝；增加新路由过滤测试。
```java
var w=service.window("SH600030",publication.inputVersionId(),60,5);
assertEquals(60,w.bars().size());
assertEquals(5,w.targetDates().size());
```
- [x] RED应因新增服务不存在而失败编译、过滤测试因路由未覆盖失败；不是因数据库误配置。
- [x] 实现迁移、验证、事务存储与新鲜度；更新测试清理顺序仅针对隔离库。
- [x] 定向及全后端GREEN；小步提交。

## Task 3：真实联调、审查与文档

**Files:** scripts/prepare-forecast-input.ps1；docs/verification-sdd005a.md；docs/implementation-status.md；docs/pending-items.md。
**Consumes:** 实际MS-02当前版本日线内部读取/固定版本ID，Task1候选，Task2发布与查询。
**Produces:** 三股真实快照/五交易日查询证据；不生成模型预测。

- [x] 脚本只在本机安全读取忽略的SERVICE_TOKEN，禁止打印；选固定RAW head的60行边界，调用源候选、发布、查询，保存不含凭据证据。
- [x] 编译/打包，仅重建MS-02及algorithm；三股逐项验证五交易日，重复请求复用，RAW head不变。
- [x] 运行全后端与Python回归；读证据、做一次独立审查，重要问题TDD修复。
- [x] 文档明确输入已接通/模型尚未发布，记录统计与局限；小步提交，保留现有分支/无remote状态。

## 执行记录

基线 f46a7d9。开始前工作树干净。规格自查：输入/未来日历/原始行情边界分别清晰，模型发布在后批；没有改变既有13项业务。三任务顺序依赖与字段契约一致。Ruling：使用固定工作目录与现有功能分支，由本代理直接执行，沿用用户授权；若需调整可回滚本批提交。
Task 1 complete：7项新增测试RED（路由/模块未实现）→完整Python25项GREEN；提交见本批feat采集记录。首次试用错误镜像名称未运行测试，已修正为本地aistock/algorithm:sdd001后取得真实RED。外部Starlette/AnyIO弃用提示1条保留，非失败。
Task 2 complete：新增快照服务缺失的编译RED已观察；恢复Docker与隔离测试库后核心11项GREEN，全后端152项GREEN（0失败/错误/跳过，2026-10-03 11:10）。原始行情约束未放宽，快照与日历仅在MS-02事务保存。
Task 1补充：慢采集阻塞健康路由的测试RED→线程池执行GREEN，全Python26项通过（c16bab7）。
Task 3 Ruling：实测发现既有日线是用户Cookie接口，计划误认为有内部读取；新增受服务凭据保护的forecast-raw-window读取当前head最后60行，不绕过用户认证，也避免默认三个月不足60行。代价为新增一项内部API与回归测试。PowerShell Data关键字解析RED已修为ReadApiData，语法GREEN。
Task 3 Ruling：真实源后复权close最多含10位小数，V5的20,6不适配；扩至28,12，新增V6保留已执行V5校验和，不舍弃来源精度。精度测试确认FORECAST_INPUT_INVALID RED。代价为一项兼容字段扩展迁移。
Task 3 实测：三股真实发布各60行，源日历125天；默认查询FRESH；两轮同前缀重放返回相同inputVersionId和replayed=true；RAW版本/revision2/各424行均未变。精度与内部读取13项定向通过；Python26项通过。全后端最终回归执行中，完成后记录统计；之后一次独立审查。
Task 3 回归完成：2026-10-03全后端154/154，Python26/26（外部弃用提示1条）；实际内部身份缺失401，正式infer503未发布。真实证据与验收文档已保存；一次独立审查正在执行。
Final review：Critical路径变体鉴权绕过和Important哈希前指数展开按实际影响接受，一轮修复，相关测试RED→GREEN，定向7项通过。Final minor (deferred)：SDK退出错误诊断未实现，子进程退出降低影响，已登记审查文档。Final Ruling：模型/未来准确性、因子全量点时语义、源长期可用性均未核验；本批只交付输入，代价为后续仍需业务实现及扩展实验，不作这些完成声明。完整回归与部署复核执行中。
Task 3 complete：真实三股快照/重复执行/RAW保留均通过；一次独立审查重要项完成一轮TDD修复，全后端157/157、Python26/26，实际鉴权/体积/指数与三股重放再次通过。Final fixed：路径变体鉴权与哈希前指数展开均RED→GREEN，suite157/157。Ruling：按用户固定目录与自主推进授权保留当前分支，正常checkout无工作树清理、无remote不push、不合并；代价为将来需要另行整合版本。运行证据保留用户项目runtime与提交文档，不清除其他计划数据。
