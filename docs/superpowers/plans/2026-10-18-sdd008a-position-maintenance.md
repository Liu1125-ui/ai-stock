# SDD-008A 持仓维护与MyBatis-Plus接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. 已保留Native执行方式，不逐任务分派。

**Goal:** 提供本人手动持仓的持久新增、修改、移除与请求恢复，并在真实业务中验证MyBatis-Plus与既有JdbcTemplate共用事务。

**Architecture:** 只扩展用户服务及其网关代理、前端页面。用户服务拥有position_book/position/position_operation_receipt；请求编号防重放、记录revision防覆盖、集合锁保护条数上限和bookRevision。估值及AI仍按008B/FR-09后续实施。

**Tech Stack:** 既有Spring Boot3.2.5/Java17、MySQL8/Flyway、Vue3/TypeScript/Element Plus、JUnit5/Vitest；仅用户服务新增com.baomidou:mybatis-plus-spring-boot3-starter:3.5.6，既有JdbcTemplate保留。

**Spec:** `docs/superpowers/specs/2026-10-18-sdd008-holdings-design.md`。用户在看到书面设计后回复“下一步”，本轮进入实施计划；本文件待审核后编码。

**Execution:** 延续当前会话Native实施、整片一次独立审查；固定现有checkout/分支。计划确认不等于已实现或已部署。

## Global Constraints

- 工作目录E:/aiStock/项目代码/stock-system；设计基线1ed7a13，13业务/五服务/USER和ADMIN不变；不套用TianMao电商规则，不读取聊天中的密码，不伪造用户会话。
- 只实现008A。008B的估值、占比、集中度和不可变分析，以及FR-09持仓AI问答仍在范围内但不作为本片交付；页面不得显示伪造的市值/盈亏或“已完成估值”。
- quantity为1至1,000,000,000整数股；averageCost为0至1,000,000人民币元、最多6位小数；每人最多100条有效持仓。这些属于服务端配置版本策略。
- 数量/成本/revision以JSON字符串传输。平均成本BigDecimal/DECIMAL(20,6)，数量BIGINT；不能用JS Number或Java double处理金额。金额不套用其他项目的Integer分单位约束。
- 本人授权必须落到WHERE user_id；原始数据只在用户库。用户身份来自现有会话，不能由客户端提交userId，ADMIN无他人持仓越权通道。
- 每笔有效变动同事务递增bookRevision与对应记录revision；重放/无变化不递增。跨服务网络校验在数据库锁外，先查原回执再决定是否需访问行情身份接口。
- 响应{code,message,data,traceId}；未登录401、他人/不存在资源404、格式400、业务范围422、冲突409、依赖503。日志不包含数量、成本、持仓正文、SQL、会话和密钥。
- 只增用户库V3迁移；不改历史迁移，不跨库SQL/FK。新目录随创建写README；选择性小步提交；无remote不push；Maven只串行，测试只连13316明确隔离库。
- 本轮设计/计划不安装依赖。版本3.5.6来自本机缓存的不可变官方发布POM：Boot3 starter、mybatis-spring3.0.3、Boot BOM3.2.0；不是“最新版本”声明，也不是已验证与现项目运行兼容的声明。Task1真实启动/依赖树/事务测试才决定能否落地；若不兼容须停止并修订计划，不能静默换版本。
- 本轮真实供应商调用0、MaaS0、邮件0；既有后台计划不因本片改变。真实用户持仓由用户本人录入，不在其账号塞测试数据。

## Review Focus

1. 成功写入但响应丢失：Task2/4测试同requestId恢复原结果，重复不加仓、不二次增版本。
2. 删除元数据被网关丢弃：Task3以实际HTTP传输测试DELETE JSON体、UUID和revision完整到达，旧自选DELETE不带体行为不变。
3. 最后一条名额被两个新请求同时占用：Task2真实MySQL并发测试99→100，另一请求明确拒绝，不超过100。
4. MyBatis和JDBC表面共用数据源却没有共用事务：Task1真实MySQL内同时MP插入持仓/JDBC写book，故意异常后两者均回滚。
5. 两标签页覆盖、账号切换和晚返回：Task2拒绝旧revision；Task4旧账号加载/提交响应不能重新显示到新账号页面，未知结果先恢复而非换requestId。

---

## 文件与接口地图

新增用户服务`src/main/java/com/aistock/user/position/`：README.md；PositionEntity.java（仅映射原始持仓）；PositionMapper.java（BaseMapper及严格owner/revision/锁SQL）；PositionRecords.java（不可变输入/输出）；PositionRules.java（规范化/范围/载荷hash）；PositionRepository.java（单事务状态机）；PositionService.java（本人身份与远程证券校验）；PositionController.java（严格协议）；PositionPersistenceConfiguration.java（仅扫描本包@Mapper）。完整路径前缀为`backend/stock-user-service/`。

新增`backend/stock-user-service/src/main/resources/db/migration/V3__positions.sql`；修改同模块pom.xml、application.yml（无密钥的默认上限）、测试AuthDatabaseFixture.java（清理新表必须先于user_account）。不创建重复DataSource、事务管理器或全局MyBatis插件。

修改网关现有ProxyHeaderPolicy.java、UserApiProxyController.java、UserPublicClient.java及对应测试，精确放行新增路由；无新业务服务。

新增frontend/src/api/positions.ts、positions.test.ts；frontend/src/pages/portfolio/README.md、PortfolioPage.vue、PortfolioPage.test.ts、PositionEditor.vue、positionInput.ts、positionInput.test.ts。修改frontend/src/router/index.ts、App.vue及导航测试；不新增无实际使用的全局Pinia store。

新增用户测试包position/README.md、PositionRulesTest.java、PositionRepositoryMysqlTest.java、PositionServiceTest.java、PositionControllerTest.java、PositionPersistenceContextMysqlTest.java。现有测试资源/README按改动同步，不能依赖执行顺序或生产库。

协议名称固定：
```java
record PositionView(String positionId,String instrumentId,String quantity,
 String averageCost,String revision,Instant createdAt,Instant updatedAt) {}
record PositionBook(String bookRevision,List<PositionView> items) {}
record PositionMutation(UUID requestId,String action,UUID positionId,
 String instrumentId,String quantity,String averageCost,Long expectedRevision) {}
record PositionReceipt(UUID requestId,String action,String state,int httpStatus,
 String code,String message,String positionId,String bookRevision,PositionView position) {}
```
PositionMutation是内部已解析对象，controller不能直接反序列化它放过额外字段。所有对外revision转换为字符串。方法固定：`PositionService.list(Identity)`、`apply(Identity,PositionMutation)`、`operation(Identity,UUID)`；Repository提供同名owner UUID版本、`findReceipt(owner,requestId)`与`apply(owner,input,canonicalHash,now)`。Service校验证券后由Repository重新锁定并查回执，不能把锁外查询当防重保证。

HTTP：GET/POST `/api/v1/users/me/positions`；PUT/DELETE `/api/v1/users/me/positions/{positionId}`；补充GET `/api/v1/users/me/positions/operations/{requestId}`，仅查询本人回执。最后一项是落实设计中“未知结果先查原请求”必需的契约细化，不新增业务。

POST恰含requestId/instrumentId/quantity/averageCost；PUT恰含requestId/expectedRevision/quantity/averageCost；DELETE恰含requestId/expectedRevision。ID均UUID，instrumentId仅SH/SZ+六位数字。请求体上限4096字节，拒绝重复JSON键、未知字段、尾随JSON及非字符串数值；GET不接受用户ID或额外参数。

## Task 1：真实MyBatis持久层和混合事务

**Files:** 上述PositionEntity/Mapper/PersistenceConfiguration、V3迁移、用户pom、application.yml、PositionPersistenceContextMysqlTest、AuthDatabaseFixture及新目录README。

**Interfaces:** Mapper extends BaseMapper<PositionEntity>；仅Repository可调用。数据源使用现有Spring DataSource，Mapper通过MyBatis-Spring加入同一个PlatformTransactionManager。

- [x] Step1：写真实隔离库上下文测试，注册实际Mapper配置而非mock Mapper；测试MP insert/select保留`123.456789`成本，数据库读出仍精确；同一事务MP insert和JDBC修改book后抛异常，两表都无变化。
```java
assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{
 mapper.insert(position);
 jdbc.update("update position_book set revision=revision+1 where user_id=?",owner);
 throw new IllegalStateException("rollback probe");
}));
assertEquals(0,mapper.selectCount(new QueryWrapper<PositionEntity>().eq("user_id",owner)));
assertEquals(0L,jdbc.queryForObject("select revision from position_book where user_id=?",Long.class,owner));
```
这里owner和position由测试在stock_user_test创建；tx用上下文实际事务管理器。增加Flyway迁移从V2到V3和重复启动不丢旧自选测试。先观察缺少Mapper/迁移导致的失败，区分编译RED与行为RED。
- [x] Step2：仅用户pom加入固定Boot3 starter；保存`dependency:tree`证据，检查无Boot2 starter、无两个不同mybatis-spring版本，非用户服务不引入依赖。
```xml
<dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring-boot3-starter</artifactId><version>3.5.6</version></dependency>
```
- [x] Step3：实现V3与真实Entity/Mapper。position_book(user_id PK,revision BIGINT,updated_at DATETIME(3))；position(position_id CHAR36 PK,user_id CHAR36,instrument_id ascii_bin,quantity BIGINT,average_cost DECIMAL(20,6),revision BIGINT,created_at/updated_at DATETIME3,UNIQUE(user_id,instrument_id))；receipt(user_id,request_id,action,payload_hash CHAR64,state,http_status,error_code,message,position_id,book_revision,result_json,created_at,PRIMARY KEY(user_id,request_id))。本服务user_id可FK user_account；receipt不FK已可删除的position；CHECK约束数量/成本/revision下界，动态上限由业务规则执行。
- [x] Step4：@MapperScan只扫描position包且只匹配@Mapper，禁止把Service接口当Mapper。Mapper提供严格owner条件的list/get/update/delete及book/receipt SQL。AuthDatabaseFixture先清receipt/position/book，再清既有表；旧用例必须继续通过。
- [x] Step5：运行Task1定向真实MySQL测试、全部旧账号测试，确认既有登录/自选仍工作；代码与依赖未通过不得进入下一任务。提交`feat: 接入持仓MyBatis持久层并验证混合事务`，只add本任务文件。

## Task 2：持仓输入与幂等并发状态机

**Files:** PositionRules/Records/Repository/Service及其Rules/RepositoryMysql/ServiceTest。

**Interfaces:** 上文Service/Repository签名。依赖既有MarketIdentityClient.get(instrumentId,token)；新增请求若已有相同成功回执不应因行情服务离线而失去恢复能力。

- [x] Step1：先写规则测试：数量0/-1/1.5/1e3/上限+1、成本负数/NaN/1e2/7位小数/超限/JSON数字拒绝，0成本与非整手1股接受。哈希按固定action、target、证券、整数数量、scale6成本、expectedRevision顺序编码，不包含requestId或时间；`20.0`与`20.000000`语义相同。
```java
assertEquals("20.000000",PositionRules.cost("20.0",new BigDecimal("1000000")).toPlainString());
assertThrows(BusinessException.class,()->PositionRules.quantity("1e3",1_000_000_000L));
assertThrows(BusinessException.class,()->PositionRules.cost("-0.1",new BigDecimal("1000000")));
```
- [x] Step2：写真实库测试覆盖三种变更/重放/异载荷、两用户同requestId独立、跨owner positionId拒绝、旧revision冲突、无变化更新UNCHANGED不增revision、删除重放返回原DELETED。并发用CountDownLatch协调提交，Future异常/超时必须失败，不能sleep后假设已提交。
```java
var saved=service.apply(owner,create);
assertEquals(saved,service.apply(owner,create));
assertEquals("1",service.list(owner).bookRevision());
assertEquals(1,service.list(owner).items().size());
```
测试fixture提供owner/other Identity、actual Spring repository，行情只mock远端边界。并发99条时提交两只不同证券，必须总100且恰一成功；同revision两次不同修改只能一成功。回执成功后模拟客户端丢响应，operation及重放恢复原结果。
- [x] Step3：实现单事务：使用INSERT ... ON DUPLICATE KEY UPDATE user_id=user_id确保book存在，再SELECT ... FOR UPDATE锁定book行→查询本人requestId回执→同hash返回原终态、异hash409→读取本人记录/校验revision及limit→变更记录与book→写完整回执。插入重复证券409，不自动累加。成功写入和回执必须同事务提交；捕获唯一键作为可识别业务错误而非原始SQL。请求形状/鉴权失败不占用回执；入锁后的确定性业务拒绝保存REJECTED回执，并在事务提交后由Controller返回对应状态；基础设施故障整事务回滚，不保存伪成功。
- [x] Step4：Service锁外先检查回执，再对新CREATE核验证券（不能接受demoOnly/错误ID）；Repository入锁后再检查一次以处理竞争。远端503无持仓写入、原记录读改删不访问行情。新增/更新平均成本统一scale6并用value equality判定无变化。
- [x] Step5：增加故障注入：变更后、回执写前抛出异常，重建Repository后记录/版本/receipt均无半提交。新用户GET无book时返回bookRevision="0"及空items，不通过GET创建业务记录；全列表在一致性事务读取bookRevision+有限全集，测试并发写不能产生集合与版本错配。运行定向测试通过后提交`feat: 实现本人持仓幂等维护与并发保护`。

## Task 3：严格HTTP接口及网关端到端转发

**Files:** PositionController/ControllerTest；ProxyHeaderPolicy/UserApiProxyController/UserPublicClient及UserProxyContractTest、PositionProxyHttpTest（新测试文件）。

**Interfaces:** 请求/响应严格遵守本计划协议；operation不存在404仅表示尚无已提交终态，不能证明同一请求未在执行。前端仍只可用原requestId重放，不能换编号“补一次”。成功CREATE201、修改/删除/重放200；REJECTED使用回执httpStatus，不将失败塞进成功响应。

- [x] Step1：HTTP测试未登录401、OTHER位置404、伪X-User-Id/X-Role不提权、CSRF/Origin失败403、重复键/尾随/未知字段/非字符串400、4096以上413。删除后通过本人operation恢复，另一用户operation404。
- [x] Step2：实现Controller严格JSON解析，位置UUID与requestId规范化，身份取现有Identity。DELETE读取两字段JSON体。回执GET的bookRevision为原操作时版本；GET列表是当前版本，不能混为相同时间。
- [x] Step3：先用真实本地HTTP测试复现旧网关DELETE丢体，再新增UserPublicClient.deleteWithBody(...,@RequestBody byte[])，仅position UUID DELETE分支调用；旧watchlist DELETE继续调用既有delete方法。验证Cookie/Origin/XSRF转发、伪身份头移除和no-store；额外路由/未知动词仍404。
```java
assertTrue(ProxyHeaderPolicy.allowed("DELETE","/api/v1/users/me/positions/"+id));
assertFalse(ProxyHeaderPolicy.allowed("PATCH","/api/v1/users/me/positions/"+id));
```
- [x] Step4：本地HTTP接收端实际读到requestId和expectedRevision原字节，不只mock Feign的Java方法；包括明确JSON Content-Type。验证409/404经网关不变成误导性503、超时受控503、内部接口不会被前端代理暴露。完成网关和用户定向回归，提交`feat: 开放持仓接口并完整转发删除请求`。

## Task 4：我的持仓页面与未知结果恢复

**Files:** frontend/src/api/positions.ts及测试；pages/portfolio上述文件；router/index.ts、App.vue及导航测试。

**Interfaces:** `listPositions():Promise<PositionBook>`、`createPosition(input)`、`updatePosition(id,input)`、`deletePosition(id,input)`、`positionOperation(requestId)`使用现有request封装；PositionBook、PositionView、PositionReceipt的字段与Java协议一致，字符串原样保留。编辑器只负责字段与表单事件，页面负责持久请求/版本冲突/账号代际，避免重复业务校验。

- [x] Step1：先写API测试确认DELETE携带体而非query、操作查询路径正确、金额不转Number；positionInput只做与后端一致的字符串形状校验，范围由后端裁定。至少断言：
```ts
expect(positionInput({quantity:'1',averageCost:'123.456789'}).averageCost).toBe('123.456789')
expect(()=>positionInput({quantity:'1e3',averageCost:'1'})).toThrow()
```
- [x] Step2：页面测试空列表、新增/编辑/移除成功后刷新保持、重复证券409、旧revision409后保留输入并要求重新加载、行情503仍能查看已有记录、移除必须确认。正常页面只读加载，不能自动新增或调用预测/AI/行情更新。
- [x] Step3：实现/portfolio导航，仅登录可见；股票选择复用现有证券搜索API，支持未进自选的已核验证券，创建不改变自选。编辑时证券固定，数量与成本用字符串输入，zero成本有说明；清楚标明“手动记录，估值分析将在后续接入”，不展示0市值冒充计算。
- [x] Step4：写操作开始前在内存按owner保存原requestId/原载荷/目标revision；不向sessionStorage持久化数量和成本。网络未知时保持原输入、锁住改写该次载荷，先查询operation，404时仅允许重发同编号同载荷。终态REJECTED后用户修改必须新编号。刷新导致内存请求丢失时，提示先核对已保存列表；服务端唯一证券/expectedRevision仍防重复，不宣称刷新后能恢复丢失的requestId。
- [x] Step5：账号变化或卸载递增页面generation并清数据、输入和pending；延迟的旧load/mutation结果不能写入新账号页面。测试实际切换Pinia身份，并在请求resolve后确认无旧成本/旧持仓显示。表单loading不吞409；当前busy时禁连续提交/取消带来第二笔写入。
- [x] Step6：定向测试、完整pnpm test、pnpm build通过；键盘Tab能到达按钮，窄屏不截断金额/冲突提示。提交`feat: 增加本人持仓维护页面`。

## Task 5：整体验证、部署和交接

**Files:** docs/verification-sdd008a.md、docs/evidence/sdd008a.json、pending-items.md、implementation-status.md；现有tests/scripts README。不写全量CI成功的虚假结论。

- [x] Step1：启动现有专用测试MySQL前检查容器label及无生产volume，严格只用127.0.0.1:13316/stock_user_test及配套测试库。测试密码从ignored runtime读取只注入子进程，不打印；若端口被别的容器占用先核对，不停止未知容器。先验证stock_user_test已存在且TCP就绪，不能误把MySQL临时初始化进程当就绪。
- [x] Step2：通过现有根工作流env加载五库URL/PASSWORD，使用CLI实际命令；不并行跑Maven。依次进行新增用户定向测试、网关定向测试、完整回归、前端全套/构建。
```powershell
# 子进程已注入五个严格隔离测试库环境，JAVA_HOME=D:/java
& C:/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin/mvn.cmd -B -Pauth-integration,cli-build clean verify
pnpm --dir frontend test
pnpm --dir frontend build
```
Maven工作目录backend，pnpm工作目录工程根；任何非0立即停止。读Surefire XML统计tests/failures/errors/skipped，不沿用669/155历史数量作本次成功证据。隔离探针不能启动真实SMTP/MaaS/供应商更新。
- [x] Step3：一次整片独立代码审查，重点覆盖本人WHERE、幂等确定性拒绝、同源事务、DELETE真实HTTP、切账号及原认证回归；Critical/Important修复后跑受影响测试，必要时重跑全量，不以审查代替测试。
- [x] Step4：部署前备份用户库及旧前端/用户/网关镜像，仅采用该库应用专用账号或明确备份账号，不使用跨库root读取业务。备份落ignored runtime且不输出记录。备份必须校验存在、非空、包含迁移版本；恢复演练只到单独隔离库，不能向正式库试恢复。不记录个人持仓原文到证据文档。
- [x] Step5：只重建stock-user-service、stock-gateway、frontend，compose明确`--env-file .env -p aistock-sdd001 -f deploy/compose.yaml`；不连带重建其他四业务服务和生产MySQL。健康检查、用户Flyway V3、新旧API与静态资产一致性核对完成后才开放新导航。失败恢复旧镜像并隐藏新入口，新增表与回执保留，不DROP生产表。
- [ ] Step6：正常登录USER/ADMIN各一次验证本人空列表、可编辑性与隔离；真实用户数据由用户输入，若需要脚本CRUD仅使用明确独立验收账号并标明测试数据。没有账号不伪造cookie、不自动提权，先完成隔离验收并记录真人验收待办。
- [x] Step7：写实际测试计数、审查结论、备份/部署/回退边界及浏览器证据，小步docs提交，git状态无本片遗漏。008A完成声明只涵盖手动维护；008B估值和FR-09问答清楚保留，不改任务书开发顺序、不伪造CodeArts使用证据。

## 自查与执行交接

设计第1—4节及008A权限/页面/验收均映射Task1—5；第5—7节估值和对应008B协议是下一份独立计划的范围，未删除。五类Review Focus各有所属任务行为测试。新增GET操作查询补足设计里的恢复需求；DELETE真实转发、AuthDatabaseFixture删表顺序和MP扫描范围均已有明确任务。

设计要求未知结果先查原requestId，而持仓属于敏感原始输入：本片选择pending仅内存，不声称跨浏览器刷新恢复。刷新后依赖当前列表、唯一证券和revision保护；这是明确的恢复边界。若用户要求跨刷新恢复请求，应另设计不保存持仓正文的安全请求编号存储。

当前仅计划编写/自查；尚未编写产品代码、安装依赖或运行上述测试。用户审核本计划后按保留的Native方式执行。以后新增规格或关键行为冲突先修订设计，不能为了赶进度静默扩充范围。

## 2026-10-18 执行状态

用户回复“开始”后Native执行，Task1—4完成；Task5自动化、独立审查、备份/隔离恢复和部署/基础页面检查完成，Step6真人录入/普通账号浏览器核验待反馈。根工作流测试命令及五个隔离URL严格一致，本机密码使用现有ignored测试凭据而非硬编码CI凭据；不是远程CI执行。CREATE原回执重放保留201的裁定及两个审查Minor补测见核验记录。未将Task5或整个FR-04标记全完成。

### 用户保存刷新反馈

用户确认“已保存，刷新后仍在”，真实持仓录入/持久化这一验收项通过。Task5 Step6中的双角色正式浏览器完整覆盖仍未全部取得证据，保留未勾选状态；不因用户一次反馈扩大角色验收范围。下一片008B负责估值与分析。
