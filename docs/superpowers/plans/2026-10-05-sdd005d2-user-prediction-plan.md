# SDD-005D2 Implementation Plan

Goal：用户同步五日预测、输入自动准备、不可变结果与本人历史。Spec docs/superpowers/specs/2026-10-05-sdd005d2-user-prediction-design.md。作者顺序执行，唯一最终独立审查，执行superpowers:executing-plans。

## Review Focus

1. 同请求并发与到期重启不会重复执行或允许旧执行覆盖成功快照。
2. 会话伪身份、越权历史、Origin/CSRF、未知owner/价格字段拒绝。
3. 输入缺失与依赖失败区分，当前RAW头和发布模型不暗换，成功五点全身份核验。
4. HTTP完整响应大小/期限和代理超时匹配，未配置或失败不产生成功曲线。
5. UI网络未知重试复用同请求，账号切换不复用另一人请求/结果，未校准区间与未接解释明确。

## Task1 后端完整同步切片

Produces：forecast目录ForecastRecords(Input,Record,Claim)、ForecastRepository、ForecastDependencies与HttpForecastDependencies、ForecastService、ForecastController/Configuration，V3__forecast_requests.sql。GET记录和分页、POST请求/hide，均会话本人。依赖Fetch方法window/prepare/infer；有Published模型但输入不可用保存FAILED而非成功。
- [x] 在既有modelcatalog测试目录写ForecastMysqlTest，沿用隔离ModelDatabaseFixture；测试真实DB请求重放/冲突/其他账号404/隐藏保留快照/clock过期旧写失败；写HTTP fixture测试prepare链/错model/hash/timeout，写MockMvc请求权限测试。先运行RED，缺类或路径必须匹配目标。
```java
var first=service.create(actor,input);var replay=service.create(actor,input);
assertEquals(first.predictionId(),replay.predictionId());assertEquals(1,inferenceCalls.get());
code("PREDICTION_REQUEST_CONFLICT",()->service.create(actor,new Input(input.operationId(),input.modelId(),"SH600276",5)));
```
- [x] 实现guard本地事务claim、到期检查、唯一(user,operation)，成功CAS(state=RUNNING AND expires_at>now AND snapshot_json IS NULL)，禁止重放重新计算；依赖调用在事务外，result严格校验。
```java
if(claim.execute())try{var window=dependencies.prepare(id,operation);var result=dependencies.infer(model,window);repository.finish(id,result,clock.instant());}catch(BusinessException failure){repository.fail(id,failure.code(),clock.instant());}
```
- [x] 当前输入已有效则复用，只有FORECAST_INPUT_NOT_AVAILABLE准备；collectionOperation来自predictionUUID；固定版本复核成功后save。真实业务HTTP采用有限BodySubscriber完整deadline，Java不修改权重。新增README。
- [x] 扩安全matcher、精确网关路由、独立ForecastPublicClient配置185秒/预测Nginx190秒，未知字段拒绝。跑完整scripts/check-auth-task1.ps1 GREEN，提交仅本切片后端/部署配置。

## Task2 页面与真实部署验收

Produces：api/predictions.ts，pages/prediction/PredictionPage.vue、pages/prediction-history/HistoryPage.vue与README；行情入口/导航/路由；图表可复用ECharts，提供表格。
- [x] 写页面/请求键测试RED；mock推荐模型与返回固定五点，无模型明确拒绝、响应未知保留键、失败无曲线、账号变化清结果。
```ts
await wrapper.get('[data-test="predict"]').trigger('click');expect(createPrediction).toHaveBeenCalledWith(expect.objectContaining({horizon:5}));
```
- [x] 实现标签/等待/错误/版本明细，原输入与成功结果不可编辑；GET历史分页、详情、隐藏确认不删除。API401触发既有session失效流程。参考既有白底蓝色风格。
- [x] 运行frontend npm test与npm run build，重建MS03/gateway/frontend；实际无认证/伪身份/未发布拒绝，受认证正向需用户真实审阅，不自动发布。验收边界如实记录。
- [x] 单次独立只读审查，两任务合并范围，Important/Critical一轮TDD修复，Minor登记；文档同步status/pending/evidence与Rulings，按功能提交。

## Pre-flight

基线ac1aeb1工作树干净。已核对MS03身份/Origin/CSRF、MS02forecast接口、D1固定输入/发布校验、原需求FR05/06及同步不进训练队列。新表仅stock_prediction，本地REF无跨库FK。模型版本从本库读取，不信任客户端hash；原预测HTTP D1支持五日。重复请求先查记录再查发布状态，保证下线后的旧成功仍可读；当前只支持模型实际60/5槽，不把未来周期写死全部系统。

Task1：缺forecast类RED；真实数据库与依赖8 GREEN；所选股票错配补充RED（错误SUCCEEDED）→绑定请求股票后GREEN。全后端191项，失败/错误/跳过均0。网关精确预测路由缺类RED→GREEN，独立185秒Feign/190秒Nginx。代码与准备链完成，实际部署待Task2统一验收。
Task2：页面缺文件RED→两项GREEN；历史旧详情响应覆盖新选择RED→选择序号校验GREEN。完整前端27项GREEN，严格构建发现恢复变量never类型→改为显式返回Input|null后构建GREEN，原包体积提示仍TODO03。未自动发布候选。
Ruling: 同步预测首次输入准备由固定HTTP地址调用MS02，独立FORECAST_MARKET_URL配置 — 既有MARKET_URL为空用于Feign注册发现，不能错误当成有效HTTP基础地址 — 代价是Compose多一个受信服务地址，必须一并维护。
Ruling: 网络结果未知保留原请求，确定4xx拒绝释放选择 — 避免重复执行与界面卡死 — 代价是用户需要再点击重试或重新选择。

补充HTTP正式推理五字段正向fixture与200头后正文停顿测试；预测服务/网关完整回归GREEN，全后端累计193项失败0（其余模块沿用本批完整191项中对应结果），停顿完整请求约10秒内失败。前端29项GREEN，构建正在最后确认。实际UI自动化内核无法初始化，不冒充已登录浏览器验收。

唯一独立审查2项Important：跨账号延迟403自动重放、再次失败仍展示旧成功结果。三项复现均RED→认证代次/等待CSRF与重试校验、开始提交清结果→完整32项前端GREEN且严格构建GREEN。Critical/Minor均0，一轮修复不重复审查。后端未变，193项结果沿用本批实测。
Ruling: 认证变化终止旧异步调用而不撤销已被服务端受理的原账号操作 — 已受理操作仍受原会话/本人边界与幂等保护 — 代价是原用户稍后需从历史或原请求查看结果。当前未实测跨其他浏览器改变会话行为。

Task1 and Task2 implementation/verification records complete. Live authenticated positive remains unverified until administrator reviews and publishes a candidate; no automatic publication. Final review two Important fixed, zero deferred Minor. Preserve current branch, original weights, plan and evidence per prior user autonomy/fixed-folder ruling. No remote push.
