# SDD010B2A 行情版本引用检查实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans，逐任务TDD，一次最终fresh代码审查。

**Goal:** 管理员对固定行情版本主动查看三个引用服务的有界候选引用行数，明确未知状态，不修改业务数据。

**Architecture:** common定义契约、有界JDK JDBC扫描和内部Token过滤器；业务白名单分别属于prediction/scheduler/notification；market校验版本归属并聚合，gateway转发，Vue详情按需查询。

**Tech Stack:** Java17/Spring Boot3/MySQL8、Vue3/TypeScript、现有权威会话与独立库。

**Spec:** docs/superpowers/specs/2026-10-27-sdd010b2a-market-version-references-design.md。2026-10-27 fresh builder check：nothing important left。计划于2026-10-28保存；此前保存命令因自动审批额度失败未执行，不冒充先前已落盘。

## Global Constraints

- 用户授权自主规划、编码、测试、部署，不再询问可行性；沿现有分支原地选择性提交，不推送。
- 无迁移、无版本停用/删除或canDisable，不改变13项业务、五服务、两角色。候选行数不是业务/用户数，不能加总判断停用安全。
- 三个服务分别只读RR快照，不作分布式原子/在途安全承诺。表/列静态白名单，本库查询，不暴露正文、账号、持仓金额或记录ID。
- 每表最多1000行/8MiB JSON，SQL每条1秒、扫描预算3秒、每owner许可1；超限/损坏/忙/失败UNKNOWN，不能伪造0。连接等待受现有池限制。
- 内部每owner一次逻辑GET、连接1秒/请求4秒/正文64KiB/禁重定向，失败保留其他owner；不转用户Cookie。
- 公共GET固定股票/版本路径，零参数正文，权威ADMIN、no-store、精确无副作用OPTIONS。数量用规范字符串。
- 目录附README；保留原卷/配置及旧结果，不调用供应商、MaaS或邮件。B1真实验收与预算单独记录。

## Review Focus

1. 精确JSON UUID与子串、同表多列去重（Task2真实MySQL）。
2. 服务故障/大表/损坏不能算0或“可停用”（Task1—4）。
3. 错归属不访问远端，网关/直连普通角色拒绝（Task3）。
4. 多服务时差与在途竞态必须明确，无安全许可字段（Task3/4/5）。
5. 角色撤销和迟到响应不能保留/回填旧账号数据（Task4）。

### Task 1：公共契约与有界扫描

**Files:** common/marketrefs/{MarketReferenceRecords,MarketReferenceInput,ReferenceScanner,ReferenceTokenFilter}.java、README和相应契约/过滤器测试。

**Interfaces:** Source(category,label,table,directColumns,jsonColumns)静态标识；Group(category,label,state,rows,reasonCode)；OwnerResult(owner,versionId,queriedAt,state,reasonCode,groups)；VersionMeta/Report按spec字段。Input严格UUID/stock/路径、Owner/Report一致性；Scanner(DataSource,owner,sources,Clock[,Limits]).scan(versionId)；TokenFilter(token,mapper)仅内部新子树GET。

- [x] 写RED：格式/状态/数量不一致、错owner/version、重复类别、非法SQL标识、内部匿名/重复头/GET正文/方法拒绝，不执行查询。
- [x] Run `mvn -f backend/pom.xml -B -Pcli-build -pl stock-common -am test`，Expected目标类型缺失或行为断言失败。
- [x] GREEN：独立只读RR连接，Semaphore1，LIMIT1001统计行数/JSON字节/合法性，再精确COUNT(OR)；deadline/timeout、回滚关闭和许可释放。JDK JDBC，不给common新增数据库starter。
- [x] 同公共完整测试PASS，选择性提交 `feat: 增加行情引用查询公共契约与有界扫描`。

### Task 2：各owner本库引用接口与真实SQL

**Files:** prediction/scheduler/notification各marketrefs/{MarketReferenceOwnerController,MarketReferenceOwnerConfiguration}.java及README、内部HTTP测试；market测试support/ReferenceScannerMysqlTest.java。

**Interfaces:** 静态表/列严格按spec清单；各controller仅GET /internal/v1/market-version-references/{versionId}，零参数正文、Token、OwnerResult。内部filter注册，不创建公开身份入口。

- [x] RED：缺controller、清单错列；真实MySQL覆盖精确JSON与子串、同表OR计一次、损坏LONGTEXT、行数/字节/无引用0/SQL失败及读前后数据不变。
- [x] Run `mvn -f backend/pom.xml -B -Pauth-integration,cli-build -pl stock-market-service,stock-prediction-service,stock-scheduler-service,stock-notification-service -am test '-Dtest=ReferenceScannerMysqlTest,MarketReferenceOwnerHttpTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，Expected RED。仅既有*_test库，不并发清理。
- [x] GREEN：归属服务配置自己的Scanner/静态Sources和过滤器，结果只有聚合数。查询无HTTP与写操作。
- [x] 专项及受影响既有非DB测试PASS，提交 `feat: 提供各业务服务的行情版本引用概览`。

### Task 3：行情聚合和安全公开入口

**Files:** market/marketrefs/{MarketVersionReferenceRepository,MarketVersionReferenceClient,MarketVersionReferenceService,MarketVersionReferenceController}.java、README与测试；market既有AccessFilter/CORS/application；gateway既有AdminMarketDataProxyController/CORS/ProxyHeaderPolicy及MarketReferenceProxyTest；compose和.example仅三内部URL。

**Interfaces:** repo.meta(stock,version,now)验证本库存在、非demo和归属；client.read(owner,version)控制地址/token/超时/响应界限；service.inspect构造Report，HTTP不在数据库事务。公开GET /api/v1/admin/market-data/{stock}/versions/{version}/references。现有三个市场读资源和B1调度四资源分流不变，返回validateReport后的安全包络。

- [x] RED：错归属不调远端，部分成功、错owner/版本、超时/越界/重定向；ADMIN/USER/匿名/身份503；GET正文/重复参数拒绝及旧路由回归。
- [x] Run market/gateway专项(相应MarketVersionReferenceTest、MysqlTest、MarketReferenceProxyTest)，Expected RED。
- [x] GREEN：本地验证先行、远端逐owner UNKNOWN，保留成功结果；不提供停用许可。严格参数/认证/no-store/CORS和安全错误白名单。
- [x] 专项及既有market/gateway回归PASS，提交 `feat: 聚合行情版本引用并提供管理员只读接口`。

### Task 4：版本详情引用检查面板

**Files:** frontend/api/marketVersionReferences.ts及test；admin/data/VersionReferencesPanel.vue及test；DataPage、README及必要既有test。

**Interfaces:** getMarketVersionReferences(stock,version):Report用requestWithoutRetry；Panel props instrumentId/versionId/actorUserId，emit accessDenied(error)，父页复用refreshAccessDenied。

- [x] RED：默认无GET/轮询/POST；重复点击一次，UNKNOWN非0，COMPLETE也非停用许可，503旧结果标未刷新，换版本/账号/迟到响应及401/403清理。
- [x] Run对应API/Panel/DataPage Vitest，Expected缺模块RED。
- [x] GREEN：沿用蓝白和原按钮/键盘/窄屏规则，每owner折叠展示记录行数及未知原因，明确时点与在途限制，不显示停用按钮。
- [x] 前端全套test/typecheck/build PASS，提交 `feat: 增加行情版本引用检查页面`。

### Task 5：最终审查、部署与验收

**Files:** docs/verification-sdd010b2a.md、review-and-fixes、evidence/sdd010b2a-verification.json及status/pending/部署说明。

- [x] 完整后端scripts/check-auth-task1.ps1 clean verify和前端完整测试/类型/构建；无变化不无谓重复。
- [x] 一次fresh全切片审查，重要问题一轮RED→GREEN及受影响回归，minor记录。
- [x] 备份受影响行情/预测/调度/通知/网关/前端旧镜像及数据基线；无迁移。先owner再market再gateway/frontend，保留账户/算法/原卷及开关，不消费MaaS/邮件。
- [x] 实测健康、产物hash、匿名与内部Token拒绝、三owner规范返回、只读操作数和行情指纹、管理员页面/窄屏/键盘；未做不声称验收。
- [x] 记录FR-11仍部分完成、后续停用互锁，选择性提交核验记录；实际完成才task-done，不清其他计划workspace，不推送。

## Preflight自审

Task1→2共享scanner和OwnerResult；Task1→3同输入/响应校验；Task2→3静态owner标识和内部路径一致；Task3→4同Report字段与GET故障语义；Task2→5先内部部署避免误判零。B1未闭环门槛独立记录，不因新计划伪完成。

Task3执行裁决：原stock-market-service网关readTimeout5秒无法覆盖3×4秒聚合；使用MarketReferencePublicClient/MarketReferenceFeignConfiguration与market-reference-public独立上下文18秒、64KiB，单独精确ProxyController，不改旧查询控制器。增加真实服务发现、超过旧5秒响应、超大体/重定向不重试测试。
