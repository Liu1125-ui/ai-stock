# SDD-005C 模型登记与审核发布 Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL superpowers:executing-plans，作者顺序执行、最后一次独立审查。

Goal：MS-03登记真实候选和评价，受管理员审核的发布/下线/推荐/回滚具备权限、事务、审计，前端可查看与操作。
Architecture：Python受控inspect → MS-03不可变候选+本地发布事务 → Cookie身份/CSRF+Feign网关 → 管理页。预测任务仍后批。
Tech Stack：现有Spring Boot3.2.5/JDBC/Flyway/MySQL8、FastAPI/PyTorch、Vue3/TypeScript/Element Plus。
Spec：docs/superpowers/specs/2026-10-03-sdd005c-model-release-design.md。

## Global Constraints

固定aiStock/current codex，13业务/5服务/2角色不减；原权重/计算实现不改，候选不自动上线或初始化管理员。只在stock_prediction事务写入；内部先鉴权再64KiB，管理员写验证Cookie/ADMIN/Origin/CSRF，不信客户端角色。回顾误差不包装未来准确性；正式infer仍未接任务。

## Review Focus

1. 路径分号/编码变体或伪角色、内部token通过网关绕过：规范路径与controller身份双层拒绝。
2. 重放/并发/事务异常：状态/head/审计整体一致，失效revision409、重放原回执。
3. 服务/文件失败或descriptor错配：发布拒绝且保持原head/版本。
4. 推荐切换/下线/回滚：不得重写历史事件或未曾发布的版本进入rollback。
5. 极端评价/未知字段/超大体：有界拒绝，不能伪装优异指标或改变支持股票。

## Task1：inspect与模型登记基础

Files：algorithm/app/inference/{http.py,worker.py}和tests/test_inference_http.py；prediction/modelcatalog/{ArtifactInspector,AlgorithmArtifactInspector,ModelRecords,ModelRepository,ModelCatalogService}.java与README；prediction/resources/db/migration/V1__model_catalog.sql及README；pom/config；tests/modelcatalog/{ModelDatabaseFixture,ModelCatalogMysqlTest}.java与README；test-db脚本。
Consumes：既有ArtifactStore.load安全三包与原研究JSON。Produces：inspect(artifactId,manifestHash)返回已验证manifest；register(Registration)返回ModelVersion；list/published/head只读。
- [x] 写inspect鉴权/真实制品元数据与坏结果测试，缺路由RED；实现共享有界slot/worker，不改safe加载与计算哈希；Python全回归GREEN。
- [x] 写MySQL登记幂等/源hash与数值/不匹配/不同评价冲突测试，观察类缺失RED；实现字段有界validator、Flyway及锁head登记，已有模型数据immutable。
```java
var first=service.register(candidate);assertEquals(first.modelId(),service.register(candidate).modelId());assertTrue(service.published().isEmpty());
```
- [x] 准备仅stock_prediction_test的真实MySQLfixture，在test runner增加限定URL/密码环境；全后端auth-integration GREEN，小步提交。

## Task2：管理员发布生命周期与接口安全

Files：modelcatalog/ModelReleaseService.java及Controller；prediction/security/{SessionIdentityClient,PredictionSessionFilter,PredictionSecurityConfiguration,ModelRequestFilter}.java及README；tests model生命周期/HTTP；gateway/{ModelApiProxyController,PredictionPublicClient,ProxyHeaderPolicy}.java及测试/config。
Consumes：ModelRepository版本/manifest、ArtifactInspector验证、MS01 sessions.resolve。Produces：mutate(actor,modelId,action,ActionInput)原回执；读admin版本/head/events与publicPUBLISHED摘要。
- [x] 写发布→推荐→下线→回滚、未发布rollback拒绝、回执重放、stale CAS、并发与失败事务测试RED。
```java
assertEquals("PENDING",catalog.register(candidate).state());assertThrows(BusinessException.class,()->release.mutate(actor,id,"rollback",input));
```
- [x] 实现lock-head一致顺序、版本CAS、operation+actor+payloadhash回执、追加审计；检查inspect结果与immutablemanifest一致，发布失败不更新。
- [x] 写401/403、Origin/CSRF、token/路径变体/size、网关白名单与正确服务路由测试RED；实现安全链/请求限额与业务role二次校验，GREEN完整后端后小步提交。

## Task3：管理页面、真实候选导入和验收

Files：frontend/api/models.ts、pages/admin/{ModelsPage.vue,modelPolicy.ts,ModelsPage.test.ts}、router/App/README；scripts/register-model-candidates.ps1/prepare-model-registrations.py及README；Compose/config/docs。
Consumes：Task2稳定版本/head/events/ActionInput接口。Produces：管理员页面查看/显式局限确认动作；内部脚本导入实际三个PENDING。
- [x] 前端状态/局限确认/错误和版本请求测试RED，实现卡片详情与理由对话框，列表冲突重载、无头显示未推荐；类型检查/测试/构建GREEN。
- [x] 脚本读取原三报告提取rmse6p2数值及SHA，与实际catalog/inspect对应，凭据不打印，register重复同一UUID且仍PENDING。
- [x] 重建相关镜像，部署真实库迁移，导入三候选、重放、重启持久化；无管理员/普通访问拒绝实际核验；仅隔离测试发布，不自动初始化或正式发布。
- [x] 一次最终独立审查、重要项RED→GREEN一轮修复；记录回归及现用PENDING状态，更新status/pending与计划，小步提交。

## 执行记录

基线840b1e1，工作树干净。三任务依赖顺序执行，沿用设计Rulings；若新业务取舍改变既有行为或需付费，才向用户确认。

Task1 complete：inspect缺路由2项RED→GREEN，Python完整46/46；模型登记缺类RED→实现5项真实MySQLGREEN，完整后端162项无失败。登记重复与并发同一UUID、metadata冲突不改原版、极端指标在网络前拒绝、descriptor/依赖失败不入库。只在stock_prediction_test写测试，现用模型库尚未部署。Hash计算先递归排序JSON字段，保证跨重启幂等。

Task2：生命周期与HTTP/网关用例先RED后GREEN；网关无映射路由原500修正为统一404。补充chunked超大体测试原500→413，网关读取最多65537字节而非先缓冲完整体。Task3：前端缺模块RED→23项GREEN，严格构建通过；真实报告转换缺脚本RED→3项GREEN，三个登记数据已生成。完整后端最终回归及部署验收进行中。

Task2 complete：051c5d1，完整后端174项无失败；Task3 complete：18b7cc0，前端23项及严格构建、转换3项；2026-10-05实际登记/重放/重启同UUID/hash，3PENDING/3REGISTER/head0无推荐。最终独立审查2Important一次RED→GREEN修复1bc7036，完整后端177项、Python46项、前端23项/构建和转换3项通过；实际网关Origin503→403/no-store。验收verification-sdd005c.md、审查sdd005c-review-and-fixes.md、脱敏evidence/sdd005c-20261005.json。

Ruling：保留本批计划、研究资料及忽略runtime证据，不执行技能建议的scratch删除；这是用户保留重要旧文档的要求，代价为少量本机存储。保持固定checkout和codex分支、无远端，不合并或虚构push。最终审查未判定事项与代价已写入审查处置文档。
