# 管理员只读任务工作区 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理员可筛选、分页查看三类持久任务及其详情/尝试历史，所有查询都不触发业务执行。

**Architecture:** 调度服务在自己的数据库上实现专用只读查询，并向用户服务逐请求解析会话、校验ADMIN。网关只代理明确的GET契约，Vue页面使用DTO和游标分页；现有内部接口、worker及计划任务不改变。

**Tech Stack:** Java 17、Spring Boot 3.2.5、Spring Cloud OpenFeign、JdbcTemplate、Flyway、MySQL 8、Vue 3、TypeScript、Pinia、Element Plus、JUnit 5、Mockito、Vitest。

**Spec:** E:/aiStock/项目代码/stock-system/docs/superpowers/specs/2026-10-20-sdd009a-admin-task-readonly-design.md

状态：2026-10-20用户已确认规格、计划及当前对话逐项执行；Task 1—6完成：自动化、独立审查修复、受控部署及用户实机验收均已记录。具体结果见docs/verification-sdd009a.md。

## Global Constraints

- 保留13项业务、五个业务微服务及ADMIN/USER两种角色，不调整任务书顺序，不把实验授权变成第三角色。
- 统一响应为{code,message,data,traceId}，所有响应Cache-Control:no-store。
- 仅展示MARKET_REFRESH、PREDICTION_VERIFY、COMPARISON_UPDATE，页面明确范围；不将预警/实验/全系统统计冒充已覆盖。
- limit默认20，范围1至50；默认最近30个自然日仅为界面默认值，不是保留期限。
- 不直接序列化内部Task对象，不返回currentAttempt、attempt_id、幂等键、凭证、SQL或堆栈。
- 累计次数来自task_attempt，当前周期次数来自scheduled_task.attempt_count，原ordinal不重编号。
- 游标使用不可变排序键，但不承诺任务状态跨页冻结；鉴权不能依赖游标或前端角色。
- 查询不触发业务写入、数据供应商查询、训练、MaaS或邮件；不开放重试/取消。
- 调度服务仅读取stock_scheduler，不跨库查询；新增索引DDL与只读业务查询明确区分。
- 使用固定仓库E:/aiStock/项目代码/stock-system与既有codex/sdd004-market-refresh分支；不在E:/TianMao实施，不创建新分支/工作树，不改其他项目。
- 各新建自有代码目录配README。每个可验证功能选择性提交；不git add -A、不提交runtime或私密配置。没有remote时不虚报push。

## Review Focus

1. 相同毫秒的多条任务、分页期间插入新任务：排序键不能仅用时间；Task 2真实MySQL测试覆盖不重复、上界和状态实时变化。
2. 注销/降权/切换任务时旧请求较晚返回：旧管理员数据不能重新出现；Task 5使用可控Promise验证。
3. 伪造或重复会话Cookie、异常身份响应、未知角色：不得放行或先查数据库；Task 3权限测试覆盖。
4. 累计尝试超过100且周期次数已重置：不能截断、重编号或错误显示次数；Task 2查询测试及Task 5展示测试覆盖。
5. 网关宽泛/admin路由抢占请求、重复参数被压平、错误响应含秘密：Task 4真实HTTP转发测试覆盖三类边界及拒绝路径。

## 执行环境与验证入口

所有相对文件路径均相对于E:/aiStock/项目代码/stock-system；命令执行时必须显式设置该工作目录。新计划自己的忽略工作区为.superpowers/sdd/2026-10-20-sdd009a-admin-task-readonly/，只在计划批准后创建，不能复用或清理SDD-008C的执行台账。

工具路径：Maven为C:/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin/mvn.cmd，JAVA_HOME为D:/java，pnpm为C:/Users/刘太荃/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/fallback/pnpm.cmd。

执行期封装上述工具为本计划本机测试入口；测试入口只将runtime/auth-test-db-secret.txt内容放入子进程环境，不打印。MySQL集成测试必须使用scripts/start-auth-test-db.ps1建立/检查的aistock-sdd002-auth-test-mysql，确认标签aistock.sdd002.auth-test=true、13316端口、tmpfs数据目录，无生产卷。

所有数据库测试URL固定jdbc:mysql://127.0.0.1:13316/stock_<service>_test?serverTimezone=UTC。使用SCHEDULER_TEST_DB_URL/PASSWORD等既有环境名；禁止沿用生产DB_URL或生产密码。测试串行，不能同时清理共享测试库。

Maven命令均在backend运行：

```powershell
$env:JAVA_HOME = 'D:/java'
& 'C:/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin/mvn.cmd' -Pcli-build -pl stock-scheduler-service -am test '-Dtest=AdminTaskQueryTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

mysql标签测试必须显式加-Pauth-integration；最终verify需同时启用cli-build,auth-integration并注入五个隔离测试库。不能把跳过的mysql测试计作通过。每次RED须确认是预期断言/未实现接口，而不是路径、环境或DB认证故障。

---

## Task 1：定义公开DTO、严格查询参数及游标

**Files — Create:**
- backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/admin/AdminTaskRecords.java
- 同目录AdminTaskQuery.java、AdminTaskCursorCodec.java、AdminTaskReasons.java、README.md
- backend/stock-scheduler-service/src/test/java/com/aistock/scheduler/admin/AdminTaskQueryTest.java、AdminTaskCursorCodecTest.java、AdminTaskReasonsTest.java、README.md

**Interfaces:** 所有类型放com.aistock.scheduler.admin；AdminTaskRecords公开嵌套record供后续任务导入。

```java
public record Reason(String code,String message) {}
public record TaskSummary(String taskId,String taskType,String instrumentId,
    LocalDate targetDate,String state,long totalAttempts,Instant createdAt,Instant updatedAt) {}
public record TaskDetail(TaskSummary summary,int cycleAttempts,Instant nextAt,
    String resultVersionId,String predictionId,String ruleVersion,Reason reason) {}
public record Attempt(int ordinal,String state,Instant startedAt,Instant finishedAt,
    Long durationMillis,String resultVersionId,Reason reason) {}
public record Page<T>(List<T> items,String nextCursor,boolean hasMore,Instant queriedAt) {}
public record Detail(TaskDetail task,Instant queriedAt) {}
public record TaskQuery(String type,String state,String instrumentId,
    LocalDate createdFrom,LocalDate createdTo,int limit) {}
public record AttemptQuery(int limit) {}
public record SortKey(Instant createdAt,String taskId) {}
public record ListCursor(int version,TaskQuery query,SortKey upper,SortKey after) {}
public record AttemptCursor(int version,String taskId,int limit,int upperOrdinal,int afterOrdinal) {}
```

AdminTaskQuery提供parseList(MultiValueMap<String,String>)、parseAttempts(MultiValueMap<String,String>)、validateId(String)。返回的Query不携带cursor；Controller分别取cursor交给服务。AdminTaskCursorCodec提供encodeList/decodeList与encodeAttempts/decodeAttempts，decode参数包含预期query或taskId/limit。AdminTaskReasons.safe(String)返回Reason，code仅允许有限映射中存在的值，未知code返回null和固定安全说明。

- [x] **Step 1 — RED测试：** 参数枚举、重复/未知参数、空字符串、倒序日期、非法UUID、limit=0/51、超长/畸形游标和跨任务/跨筛选游标均拒绝；示例断言如下。

```java
@Test void duplicateLimitIsRejected() {
    var query=new org.springframework.util.LinkedMultiValueMap<String,String>();
    query.add("createdFrom","2026-10-01");query.add("createdTo","2026-10-20");
    query.add("limit","20");query.add("limit","50");
    var error=assertThrows(BusinessException.class,()->AdminTaskQuery.parseList(query));
    assertEquals("TASK_QUERY_INVALID",error.code());
}
```

已核对BusinessException公开code()与status()，直接使用，不修改公共异常类。

- [x] **Step 2 — 运行RED：** 上述Maven命令分别选择三个新测试类，记录预期失败。
- [x] **Step 3 — 最小实现：** 使用固定枚举和参数名集合；规范化UUID小写，禁止任意sort。日期按Asia/Shanghai转换UTC边界，两端转换后均校验MySQL DATETIME可表示范围，createdTo次日运算溢出返回400。游标Base64URL、version=1、编码长度不超过4096、解码后不超过3072字节、拒绝额外JSON字段、键/序号次序异常。游标不是安全令牌，不引入新密钥。使用Jackson findAndRegisterModules保存Instant毫秒精度；query须完全相等，attempt游标绑定taskId和limit，0<=afterOrdinal<=upperOrdinal。
- [x] **Step 4 — 原因映射：** 从task包worker/client/repository实际产生的短码逐条归类；SOURCE_TIMEOUT/RESULT_UNKNOWN/COMPARISON_CYCLE_EXPIRED等覆盖。测试未知短码、含路径/SQL/Token文本和超长文本均不能回显输入；空码返回“暂无原因记录”。不要把未知码只做正则校验后直接输出。
- [x] **Step 5 — GREEN并提交：** 三组纯单元测试全部通过；选择性提交此任务文件，提交信息feat: 定义管理员任务查询契约与安全游标。

## Task 2：实现本库只读查询与真实分页

**Files — Create:**
- backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/admin/AdminTaskRepository.java
- 同目录AdminTaskService.java
- backend/stock-scheduler-service/src/main/resources/db/migration/V8__admin_task_query_index.sql
- backend/stock-scheduler-service/src/test/java/com/aistock/scheduler/admin/AdminTaskMysqlTest.java

**Modify:** 对应admin及migration目录README，仅说明新增职责/索引。

**Interfaces:** AdminTaskService构造器接收AdminTaskRepository、AdminTaskCursorCodec、Clock；提供list(TaskQuery,String):Page<TaskSummary>、detail(String):Detail、attempts(String,AttemptQuery,String):Page<Attempt>。Repository使用JdbcTemplate和TransactionTemplate，公开同职责查询，由Service负责游标和queriedAt；DTO与游标沿用Task 1，不能改名导致前后端漂移。

- [x] **Step 1 — RED真实MySQL测试：** 复用TaskMysqlTest隔离库校验与Flyway方式，写明测试夹具只清理stock_scheduler_test下自身任务表，按comparison_update_context→task_attempt→scheduled_task的依赖顺序。以三个UUID、同一created_at毫秒和limit=2建立任务，第一页后插入更晚任务，遍历验证无重复且不含新增项；改动一项state验证允许实时变化，不承诺冻结。另测上海零点前后、毫秒边界、空页、三类过滤、110次尝试及周期attempt_count=0。
- [x] **Step 2 — 运行RED：** 启动标签验证后的隔离MySQL，注入SCHEDULER_TEST_DB_URL/PASSWORD；运行-Pcli-build,auth-integration -pl stock-scheduler-service -am test -Dtest=AdminTaskMysqlTest -Dsurefire.failIfNoSpecifiedTests=false。
- [x] **Step 3 — 最小SQL实现：** 新迁移只增加以下索引，执行前核对V8尚未被其他工作占用；若已占用先核对顺序，不覆盖既有迁移。

```sql
CREATE INDEX ix_task_created_id ON scheduled_task(created_at,task_id);
```

动态SQL仅拼接固定片段，值全部绑定。列表按created_at DESC,task_id DESC排序，键条件为：

```sql
(t.created_at < ? OR (t.created_at = ? AND t.task_id < ?))
```

后续页还限制不超过upper，首屏upper取首条排序键。取limit+1后只返回limit条。针对当前页task_id集合一次GROUP BY task_id统计尝试数，不N+1，不读取凭证列。

详情用TransactionTemplate设readOnly=true、ISOLATION_REPEATABLE_READ，在同一连接读取任务及历史count。attempts先在同一只读事务验证父任务，再取首轮MAX(ordinal)或游标上界，查询ordinal>after且<=upper、升序limit+1。不能调用旧list()/attempts()代替分页。

- [x] **Step 4 — 耗时及只读测试：** durationMillis仅在finishedAt>=startedAt时计算；结束早于开始返回null及固定安全时间说明，未结束返回null。固定worker关闭，查询前后对任务与尝试关键字段逐行比较不变。110次记录全部可遍历，原ordinal保持；current_attempt、attempt_id、idempotency_key不进入DTO JSON。执行EXPLAIN验证索引可用，不把小数据优化器选择全表扫描直接判为失败。
- [x] **Step 5 — GREEN及回归提交：** AdminTaskMysqlTest及现有TaskMysqlTest/ComparisonTaskMysqlTest/TaskVerificationMysqlTest串行通过；记录迁移、索引、分页和只读证据。提交feat: 实现管理员任务只读分页查询。

## Task 3：调度服务独立会话鉴权与GET接口

**Files — Create:**
- backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/security/SessionIdentityClient.java
- 同目录AdminTaskAccessFilter.java、AdminTaskSecurityConfiguration.java、README.md
- backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/admin/AdminTaskController.java
- backend/stock-scheduler-service/src/test/java/com/aistock/scheduler/admin/AdminTaskHttpTest.java
- backend/stock-scheduler-service/src/test/java/com/aistock/scheduler/security/AdminTaskAccessTest.java、README.md

**Modify:** backend/stock-scheduler-service/src/main/resources/application.yml、application-compose.yml，deploy/compose.yaml及deploy/README.md。

**Interfaces:** SessionIdentityClient使用现有POST /internal/v1/sessions/resolve，ResolveInput(String token)的toString必须redacted；Identity字段userId:UUID、role:String、experimentAllowed:boolean、credentialVersion:long，服务Token只从服务端配置取得。AdminTaskController映射三个GET，与Task 2方法一一对应，使用@RequestParam MultiValueMap保留重复参数。

- [x] **Step 1 — RED：** 使用MockMvc+mock SessionIdentityClient及AdminTaskService。匿名401、失效Cookie401、普通USER403、ADMIN200、身份超时503、异常OK数据503、未知角色503、userId缺失503、空服务Token503。重复AISTOCK_SESSION Cookie无论相同/不同都拒绝401，不能采用“取最后一个”策略。以上拒绝情形verifyNoInteractions(service)。含伪造X-Role/X-User-Id/X-Service-Token但无有效管理员Cookie仍拒绝。

```java
mvc.perform(get("/api/v1/admin/tasks").header("X-Role","ADMIN"))
   .andExpect(status().isUnauthorized())
   .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
verifyNoInteractions(service);
```

- [x] **Step 2 — 运行RED：** Maven选择AdminTaskAccessTest,AdminTaskHttpTest；先确认断言失败而非启动配置失败。
- [x] **Step 3 — 最小实现：** 独立OncePerRequestFilter仅作用/admin/tasks边界，用户服务权威解析每请求执行；校验OK、身份、已知角色后仅ADMIN放行。resolve返回401视失效会话，其他异常统一503；认证必须先于参数解析/数据访问。GET之外404，OPTIONS在前置CorsFilter中受控处理且不读业务；内部TaskSecurityFilter保持不变。统一错误通过现有ApiResponse/ApiExceptionHandler，no-store覆盖成功/失败。
- [x] **Step 4 — 配置：** application.yml新增aistock.user.url=${USER_SERVICE_URL:}及stock-user-service Feign connectTimeout=2000/readTimeout=3000/followRedirects=false；复用现有schedulerClock，不重复声明Clock。compose配置USER_SERVICE_URL为http://stock-user-service:8080，网关后续用服务名发现。CorsFilter只注册本片公开路径，allowedOrigins取aistock.auth.allowed-origins，默认http://127.0.0.1:15173,http://localhost:15173，allowCredentials=true，仅GET/OPTIONS，允许X-Trace-Id/Content-Type；禁止*凭证跨域。拒绝不可信Origin，预检不调用resolve或数据库。
- [x] **Step 5 — GREEN及回归提交：** 直连调度接口也执行相同测试；ADMIN非法查询400、合法UUID缺失404、无权者非法查询先401/403；JSON不含敏感字段。覆盖一次ADMIN通过后下一次会话失效，不能缓存旧授权。SchedulerWiringTest、TaskSecurityTest与新测试通过，提交feat: 开放受管理员会话保护的任务查询接口。

## Task 4：网关精确路由与查询保真

**Files — Create:**
- backend/stock-gateway/src/main/java/com/aistock/gateway/SchedulerPublicClient.java
- 同目录AdminTaskApiProxyController.java
- backend/stock-gateway/src/test/java/com/aistock/gateway/AdminTaskProxyHttpTest.java

**Modify:** ProxyHeaderPolicy.java、application.yml、InternalRouteBoundaryTest.java、网关README。旧check-compose.ps1的模型未配置假设与停服测试不在本片改动范围。

**Interfaces:** SchedulerPublicClient使用Feign name=stock-scheduler-service、url=${aistock.scheduler.url:}，提供get(String path,MultiValueMap<String,String> query,Map<String,String> headers):ResponseEntity<byte[]>和options(String path,Map<String,String> headers)。专用Controller覆盖/admin/tasks及其子路径后在白名单限定三个GET；不能让宽泛UserApiProxyController抢占。

- [x] **Step 1 — RED HTTP测试：** 同时注册真实Feign测试客户端、AdminTaskApiProxyController及UserApiProxyController，上游用本机HttpServer捕获URI和headers；断言列表/详情/attempts到达scheduler而非user，重复limit保留两值，未知参数不被静默丢弃。不能只用mock证明Feign编码正确。
- [x] **Step 2 — 运行RED：** -pl stock-gateway -am test -Dtest=AdminTaskProxyHttpTest,InternalRouteBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false。
- [x] **Step 3 — 最小实现：** GET白名单只新增三条任务路径，POST/PUT/PATCH/DELETE、未知子路径及/internal均返回404。透传原参数名/多值，不将游标直接拼入URL，交由Feign编码；服务端继续严格校验参数。仅复用ProxyHeaderPolicy.request安全头，浏览器伪造身份头和服务凭证必须剥离。新增aistock.scheduler.url=${SCHEDULER_SERVICE_URL:}，Feign连接2秒/读取5秒/followRedirects=false，Nacos沿用现有配置。
- [x] **Step 4 — 异常和预检测试：** 401/403/400/404安全返回；账号校验故障503保留USER_SERVICE_UNAVAILABLE，调度连接故障503 SCHEDULER_SERVICE_UNAVAILABLE；不透传原始上游文本/堆栈。仅信任上游受控ApiResponse字段并屏蔽异常响应，成功/失败no-store。OPTIONS白名单及不可信Origin拒绝回归；HEAD不得自动通过GET路由返回任务数据。示例边界断言如下。

```java
assertFalse(ProxyHeaderPolicy.allowed("POST","/api/v1/admin/tasks"));
assertFalse(ProxyHeaderPolicy.allowed("GET","/api/v1/admin/tasks/"+id+"/retry"));
assertFalse(ProxyHeaderPolicy.allowed("GET","/internal/v1/tasks"));
```

- [x] **Step 5 — GREEN并提交：** 新测试和现有ModelProxyTest/UserApiProxyControllerTest/MarketApiProxyControllerTest回归通过。提交feat: 接入管理员任务查询网关路由。

## Task 5：管理员页面与只读交互

**Files — Create:**
- frontend/src/api/adminTasks.ts、adminTasks.test.ts
- frontend/src/pages/admin/tasks/TasksPage.vue、TaskDetailPanel.vue、TaskAttemptsPanel.vue
- 同目录taskPresentation.ts、taskPresentation.test.ts、TasksPage.test.ts、TaskDetailPanel.test.ts、TaskAttemptsPanel.test.ts、README.md

**Modify:** frontend/src/router/index.ts、frontend/src/router/guards.ts（仅如现有守卫缺测试所需则修改）、frontend/src/router/README.md、frontend/src/App.vue、App.test.ts、frontend/src/api/README.md；新增frontend/src/router/adminTasks.test.ts用于实际路由元信息及USER拒绝。

**Interfaces:** adminTasks.ts逐项对应Task 1 DTO，long计数按有界数据的JSON number显示，超过Number安全整数时接口数据校验失败而非四舍五入。提供以下方法：

```typescript
export const listAdminTasks = (query: TaskQuery, cursor?: string): Promise<Page<TaskSummary>>
export const getAdminTask = (taskId: string): Promise<Detail>
export const listAdminTaskAttempts = (taskId: string, limit?: number, cursor?: string): Promise<Page<Attempt>>
```

以上实际定义为export function或const箭头函数，不提交仅声明的代码。共用api/auth.ts的request，URLSearchParams编码字段和游标，空筛选不发出。类型名称、字段与Java契约一致，Detail的task.summary层次保持一致。

- [x] **Step 1 — RED API和文案测试：** 使用Vitest mock fetch验证仅GET、credentials:include/no-store、参数编码、分页游标、身份失败处理。taskPresentation覆盖全部9种任务状态与7种尝试状态、未知中性状态、中文类型、null耗时、上海日期与时间显示；默认30日使用Intl.DateTimeFormat(timeZone=Asia/Shanghai).formatToParts，而非浏览器本地时区或UTC日期截取。冻结2026-10-01T00:01+08:00，默认日期应为09-02至10-01（含当天30日）。
- [x] **Step 2 — 运行RED：** frontend目录执行pnpm test -- src/api/adminTasks.test.ts src/pages/admin/tasks/taskPresentation.test.ts。
- [x] **Step 3 — 最小页面实现：** TasksPage管理编辑条件与已应用条件、当前列表及游标栈；查询/刷新回首屏并清详情，上一页复用该页入参游标，列表无total页码。TaskDetailPanel接受taskId，独立GET详情并显示task.summary+cycleAttempts+原因；TaskAttemptsPanel接受taskId，独立加载/分页，刷新从ordinal初页重取上界。三组件均使用<script setup lang="ts">。新增/admin/tasks路由和管理员“任务管理”导航；不加入生成、重试、取消或AI入口。
- [x] **Step 4 — RED交互测试并GREEN：** 使用deferred Promise明确验证“点击A详情→点击B→B先返回→A后返回”仅B可显示；重复点击查询不发第二请求。未应用编辑条件不影响当前页，切换已应用条件/账号/角色清理列表详情；403清空数据，401沿用登录失效处理，404清空详情，503只保留同账号同条件的旧结果并提示未刷新。role改变即使userId相同也要使请求代次失效，不仅监听userId。所有子组件卸载或taskId变化必须忽略旧响应。

```typescript
import {it,expect,vi} from 'vitest'
import {reactive,nextTick} from 'vue'
import {mount,flushPromises} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TaskDetailPanel from './TaskDetailPanel.vue'
import * as api from '../../../api/adminTasks'
import type {Detail} from '../../../api/adminTasks'
const authState=reactive({profile:{userId:'admin-test',role:'ADMIN' as 'ADMIN'|'USER'}})
vi.mock('../../../stores/auth',()=>({useAuthStore:()=>authState}))
vi.mock('../../../api/adminTasks',()=>({getAdminTask:vi.fn(),listAdminTaskAttempts:vi.fn()}))
it('ignores a late response after administrator access is lost',async()=>{
  let resolveResult!:(value:Detail)=>void
  const pending=new Promise<Detail>(resolve=>{resolveResult=resolve})
  vi.mocked(api.getAdminTask).mockReturnValueOnce(pending)
  const id='00000000-0000-0000-0000-000000000001'
  const wrapper=mount(TaskDetailPanel,{props:{taskId:id},global:{plugins:[ElementPlus],stubs:{TaskAttemptsPanel:true}}})
  try{
    await flushPromises()
    authState.profile.role='USER';await nextTick()
    resolveResult({task:{summary:{taskId:id,taskType:'MARKET_REFRESH',instrumentId:'SH600030',targetDate:'2026-10-20',state:'SUCCEEDED',totalAttempts:1,createdAt:'2026-10-20T01:00:00Z',updatedAt:'2026-10-20T01:01:00Z'},cycleAttempts:1,nextAt:null,resultVersionId:null,predictionId:null,ruleVersion:null,reason:{code:null,message:'暂无原因记录'}},queriedAt:'2026-10-20T02:00:00Z'})
    await flushPromises()
    expect(wrapper.text()).not.toContain(id)
  }finally{wrapper.unmount();authState.profile.role='ADMIN';vi.resetAllMocks()}
})
```

另测试>100次尝试翻页仍显示原ordinal、累计110与周期0分别显示、no retry/cancel controls、空列表及每类错误分区。
- [x] **Step 5 — GREEN、构建并提交：** 新增页面组件测试全部通过，运行完整pnpm test与pnpm build。JSDOM验证Tab可聚焦/按钮语义，真实窄屏/键盘检验留Task 6。提交feat: 增加管理员任务列表与执行历史页面。

## Task 6：整体验证、独立审查、受控部署与验收

**Files — Modify:** docs/implementation-status.md、docs/pending-items.md、docs/project-completion-audit-20261020.md（追加当前事实，不擦除历史日期采样）。
**Create:** docs/verification-sdd009a.md、docs/evidence/sdd009a-verification.json。runtime下私有截图/数据库备份仅本机保存，不纳入Git；原始敏感输出不写入提交证据。

**Interfaces:** 消费前三个公开GET、Task 5/admin/tasks页面及现有部署脚本，无新增业务API。

- [x] **Step 1 — 验证矩阵登记：** 列出规格各项到实际测试类/测试名，五个Review Focus均有真实测试。记录RED/GREEN、源码提交及测试环境，未执行项标记未验收，不能据测试数量推算覆盖率。
- [x] **Step 2 — 串行回归：** 8模块Maven clean verify -Pcli-build,auth-integration，注入五个隔离_test库；前端完整test/typecheck/build；docker compose --env-file .env -f deploy/compose.yaml config --quiet只校验配置，不输出展开后的秘密。静态核对新增URL、用户服务发现和超时配置；不运行tests/check-compose.ps1：它仍假定模型未配置，并含停服故障注入，不适用于当前只读业务验收。适用测试失败时先诊断，不扩大无关重构。输出实际通过/失败/跳过数，mysql测试未运行则不发布。
- [x] **Step 3 — 独立审查：** 请求一次全改动审查，重点权限、查询保真、游标边界、身份切换、只读性、DDL兼容及隐藏内部标识；严重/重要问题修复并跑相关回归。未通过审查不部署。此阶段才按适用review/execution技能调用审查者，不在计划阶段分派开发。
- [x] **Step 4 — 部署前保护：** 核对当前desktop-linux原业务容器/原卷存在；不执行Docker reset、不改磁盘位置。备份stock_scheduler当前V7数据和结构（若环境已高于V7先查明，不套用旧假设），记录备份校验值；旧scheduler/gateway/frontend镜像各打before-sdd009a-时间标签。沿用原私密.env，禁止docker compose config把密钥打印出来。不得改运行中的供应商/MaaS/邮件开关。生产自然worker可能执行，说明只读验收与自然写入区别。
- [x] **Step 5 — 构建/部署：** 从deploy/compose.yaml只构建并更新stock-scheduler-service、stock-gateway、frontend，使用up -d --no-deps控制依赖重建。确认schema增加只读索引迁移，无业务表清空；检查三个服务及相关账号服务健康。路由未知或UI资源版本不一致时先核对镜像和浏览器刷新，不清除用户业务数据。
- [x] **Step 6 — 只读实机验收：** ADMIN打开http://127.0.0.1:15173/admin/tasks检查三类既有任务、筛选、详情、尝试分页和刷新；窄屏横滚及Tab/Enter可用。普通账号验证菜单隐藏、直达禁止；需要用户自行登录时只询问登录，不索取密码，不提权。没有可用普通账号会话则将该真实浏览器项保留待验，不以ADMIN或模拟测试代替。不得创建业务任务或额外调用MaaS/邮件来填数据。
- [x] **Step 7 — 故障回退及归档：** 部署失败可回旧三镜像并保留新增兼容索引；不要自动drop索引、倒灌旧数据或宣称完整灾备已实跑。记录实际完成项、仍缺的重试/取消、预警任务/统计/训练接入。选择性提交文档与脱敏证据，提交test: 验收管理员只读任务工作区。

## 自审与覆盖核对

规格1—2的范围与服务边界由Global Constraints及Task 3/4限制；规格3/8页面与身份变化由Task 5/6实现；规格4状态、次数、原因由Task 1/2/5覆盖；规格5分页/DTO/日期由Task 1/2/4覆盖；规格6认证、错误及no-store由Task 3/4覆盖；规格7索引与目录职责由Task 2/3/5覆盖；规格9测试矩阵由每项RED/GREEN和Task 6证据收口；规格10后续写操作不在本计划。

已核对Task 1到Task 5共享类型名称、API路径、DTO层次和游标字段一致，未计划暴露内部执行标识。已核对BusinessException访问器；执行前再次核对迁移序号及目录是否被其他工作改变；发现冲突先修订计划，不覆盖他人变更。

本计划不代表已经编码或测试。实施前由用户审核，并确认在当前对话逐项执行或选择逐任务分派方式。