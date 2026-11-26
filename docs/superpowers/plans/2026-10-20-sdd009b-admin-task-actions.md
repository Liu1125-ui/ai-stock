# 管理员任务受控重试与取消 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有管理员任务页实现三类任务受控重试、取消与操作留痕，重复提交不重复执行，并发变化不被旧页面覆盖。

**Architecture:** MS-04本库使用单调revision、行锁和持久操作编号，在一个短事务中变更任务并保存终态回执；现有worker异步执行业务。MS-01权威解析管理员身份，网关仅开放精确契约，Vue使用不自动重发的请求通道并按原编号恢复未知结果。

**Tech Stack:** Java 17、Spring Boot 3.2.5、JdbcTemplate、Flyway、MySQL 8、Spring Cloud OpenFeign、Vue 3、TypeScript、Pinia、Element Plus、JUnit 5、Vitest。

**Spec:** E:/aiStock/项目代码/stock-system/docs/superpowers/specs/2026-10-20-sdd009b-admin-task-actions-design.md

状态：2026-10-20用户已确认详细设计；用户已确认优化计划并授权当前对话逐项执行；Task 1—6完成，Task 7完整回归、独立审查修复与默认关闭部署完成；新页面人工验收已通过，真实重试已验收，真实取消待自然任务验收。产品代码基线f2aa95a；原计划文档提交ed276e4。沿用当前对话逐项执行的方式，不重复询问执行模式；计划审核后才编码。

## Global Constraints

- 固定E:/aiStock/项目代码/stock-system与codex/sdd004-market-refresh分支，不新建工作树，不引入天猫电商约束。没有配置远程仓库，不push/PR/merge。
- 保留13业务、五业务服务、ADMIN/USER两角色、任务书顺序。只覆盖MARKET_REFRESH/PREDICTION_VERIFY/COMPARISON_UPDATE。
- FAILED且符合白名单/预算/周期/开关/租约条件才可重试；QUEUED/RETRY_WAIT且无未核对尝试才可取消。RUNNING/RECONCILING不可操作。取消不关闭后续计划。
- 版本revision使用BIGINT且JSON为字符串。所有scheduled_task业务UPDATE递增，幂等空更新不递增；不能仅依赖updated_at。
- 重试不删除历史；行情/验证开启新本轮，人工重试间隔默认60秒；比较累计次数和原周期不重置、不补预算。
- 本库事务完成操作登记、行锁判断、状态修改和终态回执；数据库异常全回滚。不得事务内调用业务HTTP或跨库读写。
- 公开写入口默认关闭。aistock.admin-task.actions-enabled映射ADMIN_TASK_ACTIONS_ENABLED=false；人工冷却配置映射ADMIN_TASK_RETRY_COOLDOWN_SECONDS=60，允许正整数，不允许0取消保护。既有worker及MaaS/邮件配置不改。
- 所有新接口每次校验权威ADMIN会话；POST必须精确Origin、JSON<=4096字节、X-Aistock-Admin-Action:1；拒绝缺失/null/外站Origin。默认允许127.0.0.1:15173和localhost:15173。
- 响应{code,message,data,traceId}及no-store；409业务拒绝保留data中的回执。操作超时先GET原编号，无自动POST重试；再次确认后可恢复同编号、相同完整正文的原请求，不索取密码或伪造管理员。
- 原始备份、Token、密码、Cookie、完整请求正文不提交；新代码目录附README；每个独立功能选择性小步提交。
- 本轮实施不自动批准生产写操作、外部行情调用、MaaS或邮件验收；需要另行限定具体对象和调用预算。

## Review Focus

1. 通用auth.request在403后自动重发且丢弃409回执；任务POST必须不重发并保留回执，Task 5/6测试覆盖。
2. 同一毫秒任务被worker改变或两个管理员竞争；revision及真实MySQL行锁必须阻止旧确认生效，Task 1/3覆盖。
3. 比较任务在午夜跨日或累计预算用完；不因手动按钮重置预算，Task 2/3覆盖。
4. POST已提交但响应丢失，随后GET暂时404；不得显示“未执行”或换编号重发，刷新后仍能查原编号，Task 3/6覆盖；查不到时可人工确认恢复同编号，不能永久卡住。
5. 任一区域403或账号切换后迟到回执；共享taskAccess清空所有敏感区，不能被相同身份的新profile对象解锁，Task 4/6覆盖。

## 文件职责与交付顺序

任务1修改既有TaskRepository的版本更新及V9迁移，交付可验证并发基础。任务2在scheduler/admin增加纯政策和契约。任务3实现本库命令与历史。任务4实现scheduler公开HTTP安全。任务5实现网关和前端专用传输。任务6接任务页。任务7全回归、独立审查、默认关闭写入口部署及验收归档。

不复制009A游标类或另造身份客户端。复用现有AdminTaskCursorCodec的安全解析约束，操作历史使用独立类型避免混淆list/attempt游标。旧内部retry/cancel契约不变，但所有状态修改递增revision。下面各Java新类型均在com.aistock.scheduler.admin包，除非路径另指。

---

## 隔离测试准备与清理（首次RED前执行）

已有scripts/start-auth-test-db.ps1与scripts/check-auth-task1.ps1、scripts/stop-auth-test-db.ps1可复用。测试容器只能aistock-sdd002-auth-test-mysql，标签aistock.sdd002.auth-test=true，tmpfs /var/lib/mysql，端口127.0.0.1:13316；不得挂生产数据卷。先检查Docker上下文和inspect的标签、Mounts/HostConfig.Tmpfs、端口，任何不符立即停止。不要输出完整inspect环境或.env。

在项目根目录PowerShell 7执行：

```powershell
& ./scripts/start-auth-test-db.ps1
if ($LASTEXITCODE -ne 0) { throw 'Test database startup failed' }
& docker inspect --format '{{index .Config.Labels "aistock.sdd002.auth-test"}}' aistock-sdd002-auth-test-mysql
& docker inspect --format '{{json .HostConfig.PortBindings}}' aistock-sdd002-auth-test-mysql
& docker inspect --format '{{json .HostConfig.Tmpfs}}' aistock-sdd002-auth-test-mysql
```

启动脚本建立user/notification/market/scheduler/prediction五个_test库。秘密仅从忽略的runtime/auth-test-db-secret.txt读入进程环境。定向测试用下面包装，不把密码放命令参数、日志或提交文件：

```powershell
$testBindings = @{
 AUTH_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_user_test?serverTimezone=UTC'
 MAIL_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_notification_test?serverTimezone=UTC'
 MARKET_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_market_test?serverTimezone=UTC'
 SCHEDULER_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_scheduler_test?serverTimezone=UTC'
 PREDICTION_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_prediction_test?serverTimezone=UTC'
}
$testSecret = [IO.File]::ReadAllText((Join-Path (Get-Location) 'runtime/auth-test-db-secret.txt')).Trim()
$testPrevious = @{}
foreach ($key in @($testBindings.Keys)) {
 $passwordKey=$key.Replace('_URL','_PASSWORD')
 $testPrevious[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
 $testPrevious[$passwordKey]=[Environment]::GetEnvironmentVariable($passwordKey,'Process')
 [Environment]::SetEnvironmentVariable($key,$testBindings[$key],'Process')
 [Environment]::SetEnvironmentVariable($passwordKey,$testSecret,'Process')
}
try {
 Push-Location backend
 try {
  & mvn -B '-Pauth-integration,cli-build' -pl stock-scheduler-service -am test '-Dtest=TaskRevisionMysqlTest' '-Dsurefire.failIfNoSpecifiedTests=false'
  if ($LASTEXITCODE -ne 0) { throw 'Targeted tests failed; inspect recorded RED/GREEN result' }
 } finally { Pop-Location }
} finally {
 foreach ($key in $testPrevious.Keys) { [Environment]::SetEnvironmentVariable($key,$testPrevious[$key],'Process') }
 $testSecret=$null
}
```

各任务仅替换包装中的Maven目标参数；预期RED仍保存退出码及失败原因，确认由目标行为/缺类型导致，而不是凭证/连接失败。完整回归从根目录运行已有scripts/check-auth-task1.ps1，核对脚本已覆盖五库且Maven在PATH（本机Java D:/java、Maven C:/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin）。前端使用现有pnpm，不能因PATH缺失临时安装新版本。

测试之间按现有测试类@BeforeEach清理本次_test表；不并发跑共享库Maven，不在生产执行DELETE/TRUNCATE。需要全新测试库时，先确认没有其他测试占用，再使用scripts/stop-auth-test-db.ps1停本标签容器，重新启动使tmpfs重建；不删除生产容器/卷，不输出秘密。全部回归结束后同脚本停止测试容器，保留忽略的凭证文件供复用。

## Task 1：单调任务版本和操作存储迁移

**Files — Modify:** backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/task/TaskRepository.java；backend/stock-scheduler-service/src/main/resources/db/migration/README.md。
**Create:** backend/stock-scheduler-service/src/main/resources/db/migration/V9__admin_task_actions.sql。
**Test:** backend/stock-scheduler-service/src/test/java/com/aistock/scheduler/admin/TaskRevisionMysqlTest.java。

**Interfaces — Produces:** scheduled_task.revision BIGINT默认0；admin_task_operation数据结构。保持TaskRepository.Task既有record签名，新增政策仓储单独读取revision，避免无关调用者批量改构造器。

- [x] **Step 1 — RED：** 复用TaskMysqlTest隔离MySQL装配，写如下断言，并对领取/超时核对/完成/自动失败/比较过期和预算耗尽/内部retry/cancel分别触发实际路径，不能只扫描SQL字符串。

```java
long revision(String id) {
 return jdbc.queryForObject("select revision from scheduled_task where task_id=?",Long.class,id);
}
@Test void cancelChangesRevisionEvenAtSameMillisecond() {
 var now=Instant.parse("2026-10-20T12:00:00Z");
 var t=repo.create("SH600030",LocalDate.of(2026,10,20),"revision-test",now);
 assertEquals(0,revision(t.taskId()));
 repo.cancel(t.taskId(),now);
 assertEquals(1,revision(t.taskId()));
 assertEquals("CANCELLED",repo.get(t.taskId()).state());
}
```

另建claimChangesRevisionEvenAtSameMillisecond实际验证领取；两个路径均使用相同毫秒的now比较版本。重复create原键和009A读接口不改变版本。

- [x] **Step 2 — 运行RED：** backend执行mvn -B -Pauth-integration,cli-build -pl stock-scheduler-service -am test -Dtest=TaskRevisionMysqlTest -Dsurefire.failIfNoSpecifiedTests=false；必须注入隔离_test数据库，预期缺revision列导致失败，不得接生产数据库。
- [x] **Step 3 — 写迁移和递增SQL：** 执行前确认最高版本仍V8。操作表不加跨库外键；task_id采用本库逻辑关联，由命令事务查验存在，避免登记命令时外键共享锁升级造成额外竞争。

```sql
ALTER TABLE scheduled_task ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_task_revision CHECK (revision >= 0);
CREATE TABLE admin_task_operation (
 operation_id CHAR(36) PRIMARY KEY,
 task_id CHAR(36) NOT NULL, actor_user_id CHAR(36) NOT NULL,
 action VARCHAR(16) NOT NULL, request_hash CHAR(64) NOT NULL,
 expected_revision BIGINT NOT NULL, reason_code VARCHAR(32) NOT NULL,
 outcome VARCHAR(16) NOT NULL,
 result_code VARCHAR(64) NULL,
 before_state VARCHAR(24) NULL, after_state VARCHAR(24) NULL,
 before_revision BIGINT NULL, after_revision BIGINT NULL,
 requested_at DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL,
 KEY ix_admin_task_history(task_id,requested_at,operation_id),
 KEY ix_admin_task_retry(task_id,action,outcome,finished_at,operation_id),
 CHECK(action IN ('RETRY','CANCEL')),
 CHECK(outcome IN ('PENDING','APPLIED','REJECTED')),
 CHECK(expected_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```

每条实际任务业务UPDATE增加revision=revision+1，参数次序与原SQL一致；不改任何状态转移条件、不改幂等空更新。PENDING只在未提交事务内存在，不添加禁止其插入的CHECK。

- [x] **Step 4 — GREEN：** TaskRevisionMysqlTest及TaskMysqlTest、ComparisonTaskMysqlTest、TaskVerificationMysqlTest全部通过；确认旧Flyway迁移未修改，迁移仅在测试库执行。
- [x] **Step 5 — 提交：** 选择性暂存本任务四个文件；commit `feat: 增加任务并发版本和管理员操作存储`。

## Task 2：操作契约、严格输入与共用政策

**Create:** backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/admin/AdminTaskActionRecords.java、AdminTaskActionInput.java、AdminTaskActionPolicy.java；相同test包的AdminTaskActionInputTest.java、AdminTaskActionPolicyTest.java。
**Modify:** backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/admin/README.md。

**Interfaces — Produces:** 下列为真实Java record定义的核心接口，嵌套于AdminTaskActionRecords；时间UTC、版本响应字符串。

```java
public enum Action { RETRY, CANCEL }
public record Command(UUID operationId,String expectedRevision,String reasonCode) {}
public record PolicySnapshot(String taskType,String state,String errorCode,
 boolean activeLease,boolean unresolvedAttempt,boolean workerEnabled,
 Instant retryAvailableAt,LocalDate comparisonCycle,Integer comparisonLimit,
 long totalAttempts,boolean contextValid) {}
public record Decision(boolean allowed,String reasonCode,String message,Instant retryAvailableAt) {}
public record Options(String taskId,String revision,Instant queriedAt,boolean actionsEnabled,Decision retry,Decision cancel) {}
public record Receipt(String operationId,String taskId,String action,String outcome,
 String resultCode,String message,String beforeState,String afterState,
 String beforeRevision,String afterRevision,Instant requestedAt,Instant finishedAt) {}
public record CommandResult(int status,String code,Receipt receipt) {}
public record HistoryItem(String actorUserId,String reasonCode,Receipt receipt) {}
public record HistoryQuery(int limit) {}
public record HistoryKey(Instant requestedAt,String operationId) {}
public record HistoryCursor(int version,String taskId,int limit,HistoryKey upper,HistoryKey after) {}
public record HistoryPage(List<HistoryItem> items,String nextCursor,boolean hasMore,Instant queriedAt) {}
```

AdminTaskActionInput.parse(byte[],Action):Command严格解析；validateId(String):UUID；revision(String):long。AdminTaskActionPolicy.evaluate(Action,PolicySnapshot,Instant,boolean actionsEnabled):Decision为唯一政策来源。reasonCode字符串有限枚举，与操作原因和拒绝原因区分。

- [x] **Step 1 — RED：** 测试重复JSON字段、未知字段、4097字节、错误reason/action、负数/前导零/超过Long.MAX_VALUE/非字符串revision、非法UUID及note未知字段拒绝。纯政策固定时钟，使用参数化测试逐状态/类型验证。

```java
@Test void comparisonCannotReplenishAnExhaustedBudget() {
 var now=Instant.parse("2026-10-20T15:59:59Z");
 var s=new PolicySnapshot("COMPARISON_UPDATE","FAILED","SOURCE_TIMEOUT",
 false,false,true,null,LocalDate.of(2026,10,20),3,3,true);
 var result=new AdminTaskActionPolicy().evaluate(Action.RETRY,s,now,true);
 assertFalse(result.allowed());
 assertEquals("COMPARISON_BUDGET_EXHAUSTED",result.reasonCode());
}
```

- [x] **Step 2 — 运行RED：** mvn -B -Pcli-build -pl stock-scheduler-service -am test -Dtest=AdminTaskActionInputTest,AdminTaskActionPolicyTest -Dsurefire.failIfNoSpecifiedTests=false，预期缺新类型。
- [x] **Step 3 — 实现：** 独立JsonMapper启用严格重复检测，检查完整正文只有一个JSON对象、类型正确；读至4097字节即拒绝，不能先无限readAllBytes。revision规范正则0|[1-9][0-9]*后Long.parseLong。reason只允许四项规格枚举，第一版不提供自由备注，拒绝note及所有未知字段。政策按规格三类型白名单，context无效/残留租约/未核对尝试默认拒绝；取消不检查workerEnabled，重试要检查。所有Decision.message是固定本地文字。
- [x] **Step 4 — GREEN：** 除示例外测上海午夜前后、budget剩余0/1、冷却59/60秒、全局关闭、worker关闭、UNKNOWN尝试、非恢复错误与缺失原因。运行本任务全部测试。
- [x] **Step 5 — 提交：** commit `feat: 定义管理员任务操作契约和状态规则`，仅本任务文件。

## Task 3：原子命令、持久幂等与操作查询

**Create:** backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/admin/AdminTaskActionRepository.java、AdminTaskActionService.java、AdminTaskOperationCursorCodec.java；test包AdminTaskActionMysqlTest.java、AdminTaskOperationCursorTest.java。
**Modify:** backend/stock-scheduler-service/src/main/resources/application.yml；admin/README.md。

**Interfaces — Consumes:** Task 1数据及Task 2记录/政策；原TaskRepository的worker状态语义。
**Produces:** AdminTaskActionService.options(UUID task,Instant now):Options；execute(UUID actor,UUID task,Action action,Command input,Instant now):CommandResult；receipt(UUID actor,UUID task,UUID operation):Receipt；history(UUID task,HistoryQuery query,String cursor,Instant now):HistoryPage。

仓储公开usecase方法与Service同名且相同参数；Service负责调用仓储和全局写开关策略，不把数据库事务拆到多个对象各自提交。仓储构造注入JdbcTemplate、AdminTaskActionPolicy及配置，单一TransactionTemplate，禁止REQUIRES_NEW。操作历史页只读REPEATABLE_READ、游标固定首屏上界，limit默认20/max50。

- [x] **Step 1 — RED：** 测试数据库中制造三次可恢复失败得到FAILED任务，然后执行固定Command；断言一个回执、一笔状态变化及所有旧attempt保持。使用JUnit线程池、CountDownLatch控制两连接并发，不用睡眠猜顺序。

```java
var input=new Command(UUID.randomUUID(),Long.toString(revision(id)),
 "RETRY_TRANSIENT_FAILURE");
var first=service.execute(actor,UUID.fromString(id),Action.RETRY,input,now);
var replay=service.execute(actor,UUID.fromString(id),Action.RETRY,input,now.plusSeconds(1));
assertEquals(200,first.status());
assertEquals(first.receipt(),replay.receipt());
assertEquals("QUEUED",repo.get(id).state());
assertEquals(3,jdbc.queryForObject("select count(*) from task_attempt where task_id=?",Integer.class,id));
```

- [x] **Step 2 — 运行RED：** 隔离MySQL执行mvn -B -Pauth-integration,cli-build -pl stock-scheduler-service -am test -Dtest=AdminTaskActionMysqlTest,AdminTaskOperationCursorTest -Dsurefire.failIfNoSpecifiedTests=false。
- [x] **Step 3 — 最小命令事务：** 按规格顺序登记operation→同键重放/冲突→锁任务→政策/版本→修改→终态。登记INSERT使用普通唯一键，由DuplicateKeyException分支只查询原记录，不能ON DUPLICATE悄悄覆盖原请求。事务回滚后才映射锁/死锁异常503，不返回不确定APPLIED。查无目标抛404使未提交登记回滚；规范拒绝返回CommandResult(409,reason,receipt)正常提交。

```sql
UPDATE scheduled_task
SET state='QUEUED',attempt_count=0,error_code=NULL,version_id=NULL,
    next_at=?,updated_at=?,revision=revision+1
WHERE task_id=? AND revision=? AND state='FAILED';
```

此语句仅用于行情/验证；比较版本不重置attempt_count；取消语句仅改state/updated_at/revision。受行锁及version约束影响行数必须1；0不能记录APPLIED。no current_attempt及no lease条件通过锁内政策判断，不能只写SQL后依赖前端。

请求哈希为规范化JSON字段序列的UTF-8 SHA256，包含actor/task/action/operationId/expectedRevision/reason。回执消息由固定resultCode映射，日志不输出原始请求正文。事务内PENDING必须终态或回滚，不允许提交PENDING。操作查询限定actor；历史返回脱敏actorID而非跨库个人资料。内部TaskRepository原retry保持其既有兼容语义，公开入口始终走更严格政策。

- [x] **Step 4 — GREEN并发矩阵：** 同编号同内容双请求一次生效；首次请求根本未到达后人工同编号恢复只执行一次；首次已提交后恢复仅返回旧回执；同编号不同内容/actor冲突；两个编号同旧version最多一次生效；worker先领取/取消先提交两种锁顺序；同毫秒ABA版本变化；回执响应丢失后GET；REJECTED可重放且不后来执行；SQL失败回滚任务与记录；比较剩余预算不重置、跨日拒绝；普通任务冷却60秒；历史超过50/同时间键/游标绑定错task。断言所有HTTP业务客户端无调用。
- [x] **Step 5 — 配置及提交：** 写开关默认false、冷却60秒、拒绝非法冷却配置；mvn运行本任务及既有TaskMysqlTest、ComparisonTaskMysqlTest、AdminTaskMysqlTest；commit `feat: 实现管理员任务原子操作与历史回执`。

## Task 4：管理员HTTP接口和直连写保护

**Create:** backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/admin/AdminTaskActionController.java；security/AdminTaskPrincipal.java；test包AdminTaskActionHttpTest.java、AdminTaskActionAccessTest.java。
**Modify:** security/AdminTaskAccessFilter.java、AdminTaskSecurityConfiguration.java；对应README。

**Interfaces:** AdminTaskPrincipal(UUID userId,long credentialVersion)，request属性名为AdminTaskPrincipal.class.getName()，仅鉴权过滤器设置；Controller读取，无浏览器actor字段。GET action-options、GET operations与GET operations/{op}、POST retry/cancel使用Task 3服务。原009A GET仍兼容。

- [x] **Step 1 — RED：** MockMvc测试真实装配过滤器及Controller：合法Origin/JSON/header但USER为403且仓储零调用；ADMIN成功body包含完整APPLIED；业务409保留REJECTED；身份故障503无写入。错误正文大小和重复字段必须先安全拒绝。

```java
var envelope=new ApiResponse<Receipt>(result.code(),result.receipt().message(),
 result.receipt(),TraceIds.normalize(request.getHeader("X-Trace-Id")));
return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore()).body(envelope);
```

已核对公共ApiResponse顺序为code/message/data/traceId，示例构造与其一致；不可使用data恒为null的failure工厂丢弃409回执。

- [x] **Step 2 — 运行RED：** mvn -B -Pcli-build -pl stock-scheduler-service -am test -Dtest=AdminTaskActionHttpTest,AdminTaskActionAccessTest -Dsurefire.failIfNoSpecifiedTests=false。
- [x] **Step 3 — 实现精确安全边界：** 保留原GET身份逻辑，解析成功后绑定唯一principal；写路径仅/{id}/retry或cancel，POST必须Origin精确匹配、固定header、application/json非宽泛text/plain，body有界读取。新GET具体路径仅准taskId单段/op规范UUID；未知方法或子路径404。预检允许指定方法/头且不触发身份或仓储；仅为精确新POST路由增加POST预检，不能对整个任务前缀开放任意POST。
- [x] **Step 4 — GREEN矩阵：** Origin缺失/null/尾缀伪造/外站、重复Cookie/header、伪造actor/role/内部token、POST到list、GET到retry、HEAD/DELETE、撤权/账号禁用、直接访问调度端口均覆盖。ApiResponse无原始异常/attemptId/Token；no-store含错误响应。检查读取history时global flag关闭仍可用，POST关闭时拒绝，旧操作只读回执可查。
- [x] **Step 5 — 提交：** 运行SchedulerWiringTest、TaskSecurityTest与全部admin相关测试，commit `feat: 开放受控管理员任务操作接口`。

## Task 5：网关精确代理与前端不重发传输

**Modify:** backend/stock-gateway/src/main/java/com/aistock/gateway/AdminTaskApiProxyController.java、SchedulerPublicClient.java、ProxyHeaderPolicy.java、AdminTaskCorsConfiguration.java；frontend/src/api/auth.ts。
**Create:** gateway测试AdminTaskActionProxyHttpTest.java；frontend/src/api/adminTaskActions.ts、adminTaskActions.test.ts。
**Modify test:** frontend/src/api/auth.test.ts；保留既有auth及其他页面请求行为。

**Interfaces — Gateway:** SchedulerPublicClient.post(String path,byte[] body,Map<String,String> headers):ResponseEntity<byte[]>；路径仅两个动作，正文有界4096字节转发，不反序列化后丢字段/重复key。
**Frontend:** 对auth.ts仅做兼容的增量：ApiError新增可选第4参数data?:unknown保持前三参数兼容；新增requestWithoutRetry<T>(path:string,method:'GET'|'POST',body?:unknown,headers?:Record<string,string>):Promise<T>，共用现有authenticationGeneration校验及401事件，绝不进入request原有403自动重发/refreshCsrf分支。该专用函数只由adminTaskActions调用，不重构或改变原request/CSRF流程及其他业务。POST固定动作头，由浏览器自动发送Origin，不伪造Origin。

adminTaskActions导出getTaskActionOptions(taskId):Promise<ActionOptions>、submitTaskAction(taskId,action:'RETRY'|'CANCEL',command:TaskActionCommand):Promise<ActionReceipt>、getTaskActionReceipt(taskId,operationId):Promise<ActionReceipt>、listTaskOperations(taskId,limit?,cursor?):Promise<OperationPage>。TS属性与Task 2/3相同，ActionOptions对应Options，ActionReceipt对应Receipt，HistoryItem/Page一致。submit仅在HTTP200返回Receipt；409通过ApiError.data保存验证过的回执，UI区分key冲突无回执。

- [x] **Step 1 — RED：** 真Feign stubHTTP验证精确POST正文/Origin/header透传，409 code/data回执保留但原始异常剥离；非法上游回执不可当成功。Vitest fetch mock返回403，断言只发一次且不访问/csrf；409断言ApiError.data含绑定的operationId；延迟返回前切换认证generation应抛AUTH_CONTEXT_CHANGED。

```typescript
it('never retries an administrator command on 403',async()=>{
 const fetchMock=vi.fn().mockResolvedValue(new Response(JSON.stringify({
  code:'ACCESS_DENIED',message:'无权限',data:null
 }),{status:403,headers:{'Content-Type':'application/json'}}))
 vi.stubGlobal('fetch',fetchMock)
 await expect(submitTaskAction(taskId,'RETRY',command)).rejects.toMatchObject({status:403})
 expect(fetchMock).toHaveBeenCalledTimes(1)
})
```

测试固定数据在同文件定义，不进入生产构建：

```typescript
const taskId='00000000-0000-0000-0000-000000000001'
const command={operationId:'00000000-0000-0000-0000-000000000002',
 expectedRevision:'0',reasonCode:'RETRY_TRANSIENT_FAILURE'}
```


- [x] **Step 2 — 运行RED：** gateway执行mvn -B -Pcli-build -pl stock-gateway -am test -Dtest=AdminTaskActionProxyHttpTest -Dsurefire.failIfNoSpecifiedTests=false；frontend执行pnpm test -- src/api/adminTaskActions.test.ts src/api/auth.test.ts。
- [x] **Step 3 — 实现：** 请求头仅现有安全名单加Origin/Content-Type/固定动作头，仍不转发外部内部Token/actor；额外查询参数转发以供后端拒绝，不静默丢弃。安全error map明确新增action/revision/cooldown/budget/key/availability错误；只允许可信409结构包含绑定Receipt，不用catch所有RuntimeException丢掉合法回执。限制上游响应大小，所有错误no-store。
- [x] **Step 4 — GREEN：** POST timeout不重发；GET Receipt404原样可识别；numeric revision/越界/错task/op或action响应拒绝；401触发会话事件；专用通道仅增量加入，不改变原request所有既有行为，回归无变化；网关跨域预检无下游调用，旧泛admin路由不接管task路径。
- [x] **Step 5 — 提交：** 两端相关测试和前端build通过后commit `feat: 接入任务操作网关与单次请求通道`，不修改其他业务传输语义。

## Task 6：任务详情操作面板与结果恢复

**Create:** frontend/src/pages/admin/tasks/TaskActionsPanel.vue、TaskOperationHistoryPanel.vue、taskActionPending.ts及三个同名测试文件。
**Modify:** TasksPage.vue、TaskDetailPanel.vue、README.md及TasksPage.test.ts、TaskDetailPanel.test.ts。

**Interfaces:** TaskActionsPanel props taskId:string；emit changed无参数，父页面根据当前已应用查询刷新列表与详情。TaskOperationHistoryPanel props taskId:string、refreshKey:number。taskActionPending导出savePending(userId,marker)、loadPending(userId,taskId)、clearPending(userId,taskId)；marker为{taskId,operationId,action,expectedRevision,reasonCode,createdAt}，包含全部固定业务请求字段，不含凭证或自由文本。

- [x] **Step 1 — RED：** 组件真实嵌套taskAccess验证能力失败禁写、按钮旁原因、确认取消零请求、重复点击一次POST、409已拒绝不当未知、网络0/502/503/无法解析响应进入未知、GET404继续未知、人工确认恢复同编号且完整正文一致、恢复重复点击最多一次POST、成功查回执不重复POST、账号变化迟到响应全部丢弃。

```typescript
it('keeps an unknown result pending after receipt 404',async()=>{
 vi.mocked(api.submitTaskAction).mockRejectedValueOnce(new ApiError('NETWORK_UNAVAILABLE','连接失败',0))
 await confirmRetry(wrapper)
 vi.mocked(api.getTaskActionReceipt).mockRejectedValueOnce(new ApiError('OPERATION_NOT_FOUND','未找到',404))
 await wrapper.get('[data-testid="query-task-operation"]').trigger('click')
 await flushPromises()
 expect(wrapper.text()).toContain('操作结果尚未确认')
 expect(api.submitTaskAction).toHaveBeenCalledTimes(1)
})
```

confirmRetry在同测试文件实现为依次点击重试、选择RETRY_TRANSIENT_FAILURE、确认按钮并flushPromises，使用真实Vue/Element表单事件，不只触发未挂载DOM按钮；data-testid属于测试定位，不显示给用户。

- [x] **Step 2 — 运行RED：** pnpm test -- src/pages/admin/tasks/TaskActionsPanel.test.ts src/pages/admin/tasks/taskActionPending.test.ts src/pages/admin/tasks/TaskOperationHistoryPanel.test.ts。
- [x] **Step 3 — 实现：** 所有组件script setup；复用taskAccess及请求代次。任务变化清当前区域并加载新能力/历史；同任务未知请求阻止新操作，切换其他任务仍能只读。sessionStorage仅userId分区完整固定marker，刷新默认只GET；未知时不创建新编号、不自动补发。GET未确认后可点“恢复原请求”，确认框说明可能现在执行；每次人工确认使用相同编号/动作/版本/原因最多发一次POST，不重新取revision替换原请求。存储损坏或原请求不完整时禁止恢复。恢复不要求旧动作能力仍allowed，但必须ADMIN、能力响应actionsEnabled为true，服务端同键回执优先，未登记则按旧版本及当前政策判断。403共享清空、相同profile新对象不解锁。历史游标默认20/max50，刷新失败保留同scope旧页和游标栈。
- [x] **Step 4 — GREEN：** 迟到能力/回执/历史对其他任务不生效；取消不关后续计划文案可见；成功只提示入队/取消及回执时间，随后GET新状态；比较预算满显示原因；数值revision不被Number转换；确认框焦点/Tab/Enter语义、窄屏历史横滚；完整pnpm test和pnpm build通过。
- [x] **Step 5 — 提交：** commit `feat: 增加管理员任务操作与回执历史页面`。

## Task 7：全回归、独立审查、默认关闭部署与验收

**Create:** docs/verification-sdd009b.md、docs/evidence/sdd009b-verification.json。
**Modify:** docs/implementation-status.md、pending-items.md、project-completion-audit-20261020.md及两个superpowers目录README。运行证据与私密备份保存在runtime/sdd009b，不提交原始数据库内容。

**Interfaces:** 前六任务接口及现有Compose；不引入新生产业务API，不使用生产数据库运行测试。

- [x] **Step 1 — 环境与矩阵：** 先核对desktop-linux原容器/卷、git改动及V8；隔离MySQL仅五个_test库、tmpfs/localhost13316，验证标签与端口后注入测试连接。按Task 1—6列实际测试类及spec需求映射。后端执行mvn -B -Pauth-integration,cli-build clean verify，前端完整test/typecheck/build；保存失败/错误/跳过实际计数。不得把847/237旧数字复制为本片新结果。
- [x] **Step 2 — 一次独立审查：** 覆盖本片完整代码差异，重点revision所有写入、行锁次序、回执409、POST不重发、CSRF直连、身份切换及旧镜像兼容。依executing-plans流程请求一次独立审查；重要问题先RED重现再修复并跑相关全套，不在审查前部署。
- [x] **Step 3 — 部署前保护：** 配置校验仅docker compose config --quiet，不打印展开的.env。备份MS-04结构数据并核验SHA256，保留原scheduler/gateway/frontend镜像。新操作开关默认false，旧供应商/MaaS/邮件及worker开关不改；只新增.example模板和Compose映射，不提交私密.env。核对只有新调度实例使用目标数据库，不混跑旧worker。
- [x] **Step 4 — 受控部署：** 只构建并up -d --no-deps scheduler/gateway/frontend；迁移V9增加字段和操作表，无删除。保持公开写入口关闭；检查七应用健康、前端HTML/新资源与build一致、旧009A列表/详情/尝试正常；匿名/普通账号/外站POST被拒绝。停服、重置Docker、移动VHDX不属于本片步骤，异常先诊断保留原数据。
- [ ] **Step 5 — 隔离写验收：** 固定测试任务及mock下游证明真实入队/领取/取消、历史、重复请求/并发/超时恢复。页面由用户本人账号验收关闭入口的原因提示、操作历史显示、窄屏和键盘；没有生产操作记录不造假填数据。三阶段分别记录隔离写验证、本机部署核验、真实写链路验收；完成前两阶段只能标“已部署，真实重试已验收，真实取消待自然任务验收”，不标全片验收完成。真实写链路需要另行授权具体任务及可能的外部请求预算，授权后验收通过才开放入口，不让入口无限期关闭却声称功能完成。当前不能据本计划确认便自动启用生产写入口。
- [ ] **Step 6 — 回退与归档：** 故障先关写入口再回旧三镜像，新增表列和备份保留，不覆盖业务数据库。完整回退未演练必须说明。记录实现/自动测试/部署/人工验收分别状态；FR-13仍部分完成，其余13业务、预警适配、训练与统计待办不删。选择性commit `test: 记录管理员任务受控操作验证结果`；保留原分支，不push。

## 自审记录

- 规格1—3由Global Constraints、Task 2/3状态和预算覆盖；规格4—5由Task 1/3版本、原子命令及操作存储覆盖；规格6由Task 4/5安全公开契约覆盖；规格7由Task 5/6不重发传输和页面恢复覆盖；规格8由文件职责及各Interfaces覆盖；规格9由每任务RED/GREEN及Task 7部署边界覆盖；规格10由本计划审核状态和归档覆盖。
- Review Focus五项均落到具体测试。修正通用request自动403重发与丢弃409数据的风险，不将其直接复用于管理员命令。
- 明确普通内部retry原兼容语义与新公开严格政策不同；revision所有写路径必须补齐。比较重试不重置累计预算，不把全部FAILED当可重试。
- 审计使用本库逻辑task关联，命令事务查不存在则整笔回滚；无跨库外键，不新增强依赖。不承诺防篡改审计。
- 所有共享DTO、TS字段、路径、方法、revision字符串和错误data层次已相互核对；测试示例中的帮助函数在对应任务明确要求本地定义。无占位章节。
- Task 7明确部署默认关闭、只读验收与真正写链路分开；没有生产写操作授权就保留待验收，不擅自增加外部调用。

实施前审核本计划。确认后沿用当前对话逐项执行、测试先行和小步提交方式；本计划不是已实现证明。

2026-10-20优化修订：用户已采纳五项建议，本计划与规格同步修订；七步顺序不变，等待最终计划审核。

2026-10-21进展：一次独立审查两项重要问题均RED→GREEN修复，网关53项、前端261项及构建通过；V9默认关闭部署与只读核验完成。Step5隔离写测试完成，页面人工检查已由用户确认通过，限定生产写仍待验收；Step6回退策略与证据已保存，未演练完整回退。

2026-10-21追加记录：用户授权自主完成后，一次真实重试成功，旧三次失败记录及操作回执保留；关闭入口后历史仍可读取。当前没有可取消自然任务，取消生产链路待验收，Task7不标全部完成。
