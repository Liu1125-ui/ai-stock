# SDD007D 股票对比证据持续更新 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有三股的比较复权历史证据接入持久调度，新比较使用最新共同可用证据，旧比较和AI解释保持不可变，失败与未知结果可恢复。

**Architecture:** MS02只读生成固定RAW/一年窗口更新上下文，接收端持久回执保护有界采集和原子发布；MS04复用scheduled_task/task_attempt，增加COMPARISON_UPDATE与本库上下文表。Python现有ComparisonSource不承担调度，不增加新供应商或自动模型调用。

**Tech Stack:** Spring Boot、JdbcTemplate/TransactionTemplate、MySQL/Flyway、Java HttpClient、既有Python/Baostock比较采集、Vue3既有比较页面、JUnit5/真实隔离MySQL/本地HTTP/pytest。

**Spec:** `docs/superpowers/specs/2026-10-13-sdd007d-comparison-refresh-design.md`。用户2026-10-13明确确认书面设计并要求编写实施计划。

**Execution:** 保留已选择的Native方式：当前会话逐任务实施，整片一次独立审查；不逐任务分派、不再询问执行方式。用户已确认本计划并实施，当前交付状态见文末2026-10-17记录。

## Global Constraints

- 工作目录E:/aiStock/项目代码/stock-system，分支codex/sdd004-market-refresh；不使用E:/TianMao规则，不清理旧.superpowers。当前产品基线d44300c，设计提交498e36e。产品测试数量基线606/148只作历史对照，不当成本片实跑结果。
- 13业务、五服务、USER/ADMIN不变；跨服务仅HTTP和UUID/hash引用，无跨库SQL或FK。
- 同一baostock、BACKWARD及`baostock-comparison-backward-close-v1`，范围`targetDate.minusYears(1)..targetDate`，不拼接新增几天或填零。
- 自动计划北京时间20:00后、60秒只读扫描；三股SH600030/SH600276/SZ000002。固定cycleDate/证券日逻辑周期，超过原计划日的新领取停止，在途先核对原回执。
- Python既有25秒子进程/2MiB输出/4096字节请求/共享采集槽；MS02到Python连接2秒/请求与Future期限30秒；MS04比较POST45秒、状态GET3秒、就绪GET5秒，连接2秒、不重定向、不重试。不是整个分布式过程严格总墙钟证明。
- claim租约90秒、MS02接受上限120秒、毫秒精度；固定原编号不续租，不在GET采集或重生成。
- 默认单周期最多3次；新类型退避900/1800秒，原类型60/120秒不变。真实首验使用更严格attemptLimit=1，最大仍不超过设计的3；该值随周期上下文固定，不能事后放宽旧周期。
- 正常三股9次数据query方法调用，自动逻辑周期最坏27次；SDK login/logout另计。不是SDK底层网络包或自然日账单次数证明。
- 首验逐股、每股最多一组、总共最多9次数据query；任何源错误停止扩量，不真实自动重试。计划确认后该预算才可使用。MaaS0、邮件0。
- 所有业务响应`{code,message,data,traceId}`，内部服务身份先于JSON；不输出Token/密码/SQL异常，不读取登录凭据，不伪造用户会话。
- 新开关默认false；比较关闭不领取比较任务，原工作器关闭不领取RAW/验证任务。不连动其他开关。
- 旧用户结果/解释/模型/预测/行情版本保全。CodeArts证据仍待补，代码生成者如实记录；MyBatis-Plus仍规划，不能冒称已集成。
- 新代码目录随创建配README；本计划只扩充现有comparison/task目录，测试/部署工具按职责更新已有README。
- 单个功能选择性commit，无`git add -A`，没有remote不push或假称远程CI；Maven不并发，测试只用端口13316的明确隔离库。

## Review Focus

1. 末行停牌及周末扩日历：READY/CURRENT不能照搬AlertBoundary拒绝停牌或只比较RAW ID。Task1测试停牌、窗口投影与日历差异。
2. 发布线程等锁跨过租约、或成功后响应丢失：原子回执/证据，不留孤立成功、不重采。Task2真实MySQL锁等待与回滚测试，Task4丢响应恢复。
3. 同日RAW变化、跨午夜重试和人工retry：上下文不可变、周期预算不能重置，新周期不覆盖旧记录。Task3并发、累计尝试、跨日测试。
4. 一类工作器关闭而另一类开启，以及旧程序遇到新任务：过滤claim与pendingReceipts，不误消费；回退须排空。Task4四种开关组合，Task5隔离旧worker回退检查。
5. 两股新窗口已滚动、一股滞后：共同范围起点仍可从旧证据完整覆盖，旧链接不变。Task5选源/MySQL/真实用户结果保全核验。

## 文件和接口总览

MS02现有包`backend/stock-market-service/src/main/java/com/aistock/market/comparison/`：
- 新ComparisonUpdateRecords：就绪/刷新DTO；ComparisonUpdatePolicy：时间/窗口投影纯函数；ComparisonUpdateQuery：就绪与固定版本读取。
- 新ComparisonCollector/ComparisonCollectorClient：只调用已存在Python比较接口，严格有界协议。
- 新ComparisonRefreshRepository/Service/Controller/Configuration：执行回执、原子发布与三内部路由。
- 改ComparisonEvidenceRepository：保留现有手工发布，增加事务内guard重载；改CandidateRequestFilter的精确小请求上限；新增预计V12迁移。

MS04现有包`backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/task/`：
- 新ComparisonTaskRecords：跨服务独立DTO和固定Context；ComparisonTaskClient/HttpComparisonTaskClient：内部HTTP。
- 新ComparisonUpdatePlan：只读扫描和日任务创建。
- 改TaskRepository的比较任务创建/上下文/类型筛选/预算；改TaskWorker执行、恢复与开关；改TaskConfiguration和配置；新增预计V7迁移。

新类型结果沿用`MarketRefreshClient.Result(operationId,state,versionId,errorCode)`，其中versionId为evidenceId；旧两种任务语义不变。MS04不import MS02类。

## 测试运行工具（属于Task1）

Create `scripts/check-comparison-update.ps1`，用于各Task定向真实MySQL/单元测试。不要把测试口令作为命令参数或输出。内容：

```powershell
#requires -Version 7.0
param([Parameter(Mandatory)][string]$Modules,[Parameter(Mandatory)][string]$Tests)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$valid=@('stock-market-service','stock-scheduler-service')
if(@($Modules.Split(',') | Where-Object {$_ -notin $valid}).Count -or $Tests -notmatch '^[A-Za-z0-9_,]+$'){throw 'Invalid test selection'}
$keys=@('MARKET_TEST_DB_URL','MARKET_TEST_DB_PASSWORD','SCHEDULER_TEST_DB_URL','SCHEDULER_TEST_DB_PASSWORD')
$saved=@{}; foreach($key in $keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key)}
try {
 $secret=[IO.File]::ReadAllText((Join-Path $root 'runtime/auth-test-db-secret.txt')).Trim()
 $env:MARKET_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_market_test?serverTimezone=UTC'
 $env:SCHEDULER_TEST_DB_URL='jdbc:mysql://127.0.0.1:13316/stock_scheduler_test?serverTimezone=UTC'
 $env:MARKET_TEST_DB_PASSWORD=$secret; $env:SCHEDULER_TEST_DB_PASSWORD=$secret
 & mvn -B -f (Join-Path $root 'backend/pom.xml') '-Pcli-build,auth-integration' -pl $Modules -am "-Dtest=$Tests" '-Dsurefire.failIfNoSpecifiedTests=false' test
 if($LASTEXITCODE -ne 0){throw 'Selected tests failed'}
} finally {
 foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$saved[$key])}
 $secret=$null
}
```

使用已安装Java17和Maven，在执行环境设置JAVA_HOME=D:/java、PATH加入D:/java/bin与C:/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin。首次先运行现有`scripts/start-auth-test-db.ps1`，它校验测试容器label并保留已有口令；不得改为现用库。每次RED/GREEN输出重定向本片runtime日志，只读尾部与失败断言。

---

## Task 1: 就绪判定、固定窗口及内部只读协议

**Files:**
- Create: MS02 comparison/ComparisonUpdateRecords.java、ComparisonUpdatePolicy.java、ComparisonUpdateQuery.java、ComparisonUpdateController.java。
- Create tests: `backend/stock-market-service/src/test/java/com/aistock/market/comparison/ComparisonUpdateSamples.java`、ComparisonUpdatePolicyTest.java、ComparisonUpdateQueryMysqlTest.java、ComparisonUpdateHttpTest.java。
- Modify: MS02 comparison/README.md、测试comparison/README.md、scripts/README.md；Create scripts/check-comparison-update.ps1。

**Interfaces:**
```java
// ComparisonUpdateRecords: all nested records public; immutable list copies.
public record Options(Instant asOf,String definition,List<Item> items) {}
public record Item(String instrumentId,String state,String reason,UUID rawVersionId,
 String rawContentHash,LocalDate rangeStart,LocalDate targetDate,UUID evidenceId,LocalDate sourceTo) {}
public record Request(UUID operationId,String instrumentId,UUID rawVersionId,String rawContentHash,
 LocalDate start,LocalDate end,String definition,Instant leaseUntil) {}
public record Result(String operationId,String state,String versionId,String errorCode) {}
// Policy: pure functions, numeric BigDecimal comparison after canonicalization.
public static LocalDate target(List<TradingDay> days,Instant now,int hour);
public static boolean sameWindow(List<RawBar> a,List<TradingDay> ca,List<RawBar> b,
 List<TradingDay> cb,LocalDate from,LocalDate to);
// Query constructor(MarketVersionRepository,CalendarService,Clock), @Service.
public Options options(List<String> instruments);
public ComparisonEvidenceService.RawWindow fixedWindow(Request request);
```

Item.state为READY/CURRENT/NO_RAW_VERSION/RAW_LAGGING/CALENDAR_UNAVAILABLE/INSUFFICIENT_HISTORY/SOURCE_INCOMPATIBLE。非READY/CURRENT给reason，未知UUID/hash/日期不得塞空串。就绪整体参数非法422，单股前置不可用是该Item状态，不吞掉其他股。

- [x] Step1 写测试工具和纯合成fixtures（不是交易所真实日历）。ComparisonUpdateSamples给固定Clock、raw发布及配对候选，所有日期参数显式传入。Samples静态方法在测试中用静态import；imports使用java.time/java.util/java.math及文中已给实际包名。核心可直接使用：
```java
static List<TradingDay> calendar(LocalDate from,LocalDate to) {
 return from.datesUntil(to.plusDays(1)).map(d->new TradingDay(d,
  d.getDayOfWeek()!=DayOfWeek.SATURDAY&&d.getDayOfWeek()!=DayOfWeek.SUNDAY)).toList();
}
static List<RawBar> bars(LocalDate from,LocalDate to,String price,boolean suspendEnd) {
 BigDecimal p=new BigDecimal(price),v=new BigDecimal("100");
 return calendar(from,to).stream().filter(TradingDay::open).map(d->new RawBar(d.date(),p,p,p,p,
  v,p.multiply(v),suspendEnd&&d.date().equals(to)?0:1,null)).toList();
}
static UUID raw(MarketDatabaseFixture f,String op,LocalDate from,LocalDate to,
 String price,boolean suspended,long revision,Clock clock) {
 var c=new MarketCandidate(op,"SH600030","SH600030","中信证券",true,"ACTIVE","baostock","RAW",
  clock.instant(),null,"a".repeat(64),bars(from,to,price,suspended),calendar(from,to),revision);
 return UUID.fromString(new PublicationService(new MarketVersionRepository(f.jdbc()),new CandidateValidator()).publish(c).versionId());
}
static String rawHash(MarketDatabaseFixture f,UUID raw) {
 return f.jdbc().queryForObject("SELECT content_hash FROM market_data_version WHERE version_id=?",String.class,raw.toString());
}
static CalendarService validCalendar(LocalDate from,LocalDate to,Clock clock) {
 var service=org.mockito.Mockito.mock(CalendarService.class);
 var days=calendar(from,to).stream().map(d->new CalendarRecords.Day(d.date(),d.open())).toList();
 var c=new CalendarRecords.Candidate("baostock",from,to,clock.instant(),CalendarService.hash(days),days);
 org.mockito.Mockito.when(service.covering(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
  .thenReturn(Optional.of(new CalendarRecords.Snapshot(UUID.randomUUID(),c)));
 return service;
}
static ComparisonUpdateRecords.Request request(MarketDatabaseFixture f,UUID raw,LocalDate from,LocalDate to,Clock clock) {
 return new ComparisonUpdateRecords.Request(UUID.randomUUID(),"SH600030",raw,rawHash(f,raw),from,to,
  ComparisonEvidenceValidator.DEFINITION,clock.instant().plusSeconds(90));
}
static ComparisonEvidenceRecords.Candidate candidate(ComparisonUpdateRecords.Request r,String rawPrice,String adjusted,Clock clock) {
 return new ComparisonEvidenceRecords.Candidate(r.operationId().toString(),r.instrumentId(),r.rawVersionId(),r.start(),r.end(),"baostock",
  r.definition(),clock.instant(),"b".repeat(64),bars(r.start(),r.end(),rawPrice,false).stream()
  .map(b->new ComparisonEvidenceRecords.Point(b.date(),b.close(),new BigDecimal(adjusted),b.volumeShares(),b.tradeStatus())).toList(),calendar(r.start(),r.end()));
}
```
为并发到期测试在同fixture定义以下Clock；所有访问库的测试类加@Tag("mysql")，测试日期/日历只为合成边界，不发布现用库。
```java
static final class MutableClock extends Clock {
 private final java.util.concurrent.atomic.AtomicReference<Instant> value; private final ZoneId zone;
 MutableClock(Instant now){this(new java.util.concurrent.atomic.AtomicReference<>(now),ZoneOffset.UTC);}
 private MutableClock(java.util.concurrent.atomic.AtomicReference<Instant> value,ZoneId zone){this.value=value;this.zone=zone;}
 void advance(Duration amount){value.updateAndGet(t->t.plus(amount));}
 public Instant instant(){return value.get();}
 public ZoneId getZone(){return zone;}
 public Clock withZone(ZoneId next){return new MutableClock(value,next);}
}
```

- [x] Step2 写RED。Policy测试19:59:59目标昨日、20:00目标当日、周末仍最后开放日、日历缺口/重复拒绝；过期/future fetchedAt在Query用CalendarSnapshot验证（纯target函数没有采集时间，不承担TTL）。leap目标2028-02-29窗口起点2027-02-28。示例：
```java
var days=calendar(LocalDate.of(2026,10,1),LocalDate.of(2026,10,13));
assertEquals(LocalDate.of(2026,10,12),ComparisonUpdatePolicy.target(days,Instant.parse("2026-10-13T11:59:59Z"),20));
assertEquals(LocalDate.of(2026,10,13),ComparisonUpdatePolicy.target(days,Instant.parse("2026-10-13T12:00:00Z"),20));
```
Query真实MySQL测试：末行停牌仍READY、RAW未到目标RAW_LAGGING、无一年INSUFFICIENT_HISTORY；旧绑定RAW在新头发布后fixedWindow仍返回指定原ID/hash；窗口内价格/量/状态/日历任一改变为READY，数值10.0/10.000不误判；只扩窗口外休市日历为CURRENT。证据目标日MISSING不得CURRENT，历史其他缺失仍显式保留。options前后表计数不变、CalendarCollector零调用。

- [x] Step3 Run RED：
```powershell
./scripts/check-comparison-update.ps1 -Modules stock-market-service -Tests 'ComparisonUpdatePolicyTest,ComparisonUpdateQueryMysqlTest,ComparisonUpdateHttpTest' *> runtime/sdd007d-task1-red.log
```
Expected: 新类型/方法缺失或上述业务断言失败，不能以fixture编译错误、库连接错误冒充RED。

- [x] Step4 实现Record/Policy/Query。窗口筛选后按date/close.stripTrailingZeros/volumeShares/status及连续日历比较；不要比较open/high/amount或窗口外日期。Query用只读REPEATABLE_READ事务、5秒SQL预算固定头和候选证据；Options.asOf规范到毫秒，MS04存储同一毫秒值，避免DATETIME(3)损失精度后上下文比较不一致；CalendarService.covering后仍验证snapshot来源/hash/TTL/连续性。CURRENT需证据覆盖一年、to==target、目标日非MISSING；否则READY或明确前置状态。fixedWindow按传入rawVersionId查询AVAILABLE/baostock/RAW、证券和hash、完整窗口日历，不重选head，拒绝未来/错范围。


```java
private static String canonical(BigDecimal value) {
 return value == null ? null : value.stripTrailingZeros().toPlainString();
}
private static Map<LocalDate,List<String>> projection(List<RawBar> bars,LocalDate from,LocalDate to) {
 var out=new TreeMap<LocalDate,List<String>>();
 for(var b:bars) {
  if(b==null || b.date()==null) throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_INVALID");
  if(b.date().isBefore(from) || b.date().isAfter(to)) continue;
  var row=Arrays.asList(canonical(b.close()),canonical(b.volumeShares()),Integer.toString(b.tradeStatus()));
  if(out.put(b.date(),row)!=null) throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_INVALID");
 }
 return out;
}
```
`sameWindow`除上述投影外，还比较from..to内逐日日历的日期与开放状态；重复或不连续日历受控失败。数值为null的记录不补零，由既有证据校验保留缺失状态。

- [x] Step5 实现GET `/internal/v1/market/comparison-update-options`，只允许ids单query、最多449字符/1—50唯一SH/SZ ID。沿用CandidateRequestFilter的先认证，缺身份畸形参数401；未知/重复query422，受控错误统一响应。MockMvc测试矩阵路径认证及verifyNoInteractions源采集器。

- [x] Step6 同命令GREEN并加旧ComparisonEvidenceMysqlTest/ComparisonEvidenceOptionsMysqlTest/ComparisonEvidenceHttpTest，预期全部通过。选择性提交：`feat: 核对比较更新就绪与固定数据窗口`；记录具体test数和日志，不把纯合成日历当真实交易日历。

## Task 2: 有界采集、持久回执和原子发布

**Files:**
- Create MS02 comparison/ComparisonCollector.java、ComparisonCollectorClient.java、ComparisonRefreshRepository.java、ComparisonRefreshService.java、ComparisonRefreshController.java、ComparisonRefreshConfiguration.java。
- Create `backend/stock-market-service/src/main/resources/db/migration/V12__comparison_refresh_receipt.sql`（执行前核对末号）。
- Modify ComparisonEvidenceRepository.java、ingestion/CandidateRequestFilter.java、support/MarketDatabaseFixture.java及对应README。
- Create tests comparison/ComparisonCollectorHttpTest.java、ComparisonRefreshMysqlTest.java、ComparisonRefreshHttpTest.java；使用Task1 Samples。

**Interfaces:**
```java
public interface ComparisonCollector {
 record Collected(String state,ComparisonEvidenceRecords.Candidate candidate,String errorCode) {}
 Collected collect(ComparisonUpdateRecords.Request request);
}
// Repository constructor(JdbcTemplate,ComparisonEvidenceRepository,ComparisonEvidenceValidator,ObjectMapper,Clock)
public record Claim(boolean execute,ComparisonUpdateRecords.Result receipt) {}
public Result replay(Request request); // null if absent; conflict if changed params
public Claim begin(Request request);
public Result complete(Request request,ComparisonEvidenceRecords.Candidate candidate,String errorCode);
public Result lookup(UUID operationId); // pure read, expired RUNNING -> derived FAILED
// Service constructor(repository,query,collector,clock)
public Result refresh(Request request);
// Existing repository overload, package-private, same datasource REQUIRED transaction.
Publication publish(Candidate candidate,String requestHash,ComparisonEvidenceValidator validator,Runnable beforeReturn);
```
现有三参数publish委托无额外guard的四参数版本，原手工契约不变；guard在已有回放和新插入的每条返回路径执行。接收端完整成功由外层回执事务提交，不能publish先commit再单独finish。

- [x] Step1 先写数据库约束与状态断言测试；下列为随后GREEN实现使用的目标DDL（不要在RED前先实现）：
```sql
CREATE TABLE comparison_refresh_receipt (
 operation_id CHAR(36) PRIMARY KEY, request_hash CHAR(64) NOT NULL,
 instrument_id VARCHAR(64) NOT NULL, raw_version_id CHAR(36) NOT NULL,
 raw_content_hash CHAR(64) NOT NULL, range_start DATE NOT NULL, target_date DATE NOT NULL,
 definition VARCHAR(64) NOT NULL, lease_until DATETIME(3) NOT NULL,
 state VARCHAR(16) NOT NULL, evidence_id CHAR(36) NULL, error_code VARCHAR(64) NULL,
 created_at DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL,
 FOREIGN KEY(raw_version_id) REFERENCES market_data_version(version_id),
 FOREIGN KEY(evidence_id) REFERENCES comparison_evidence_version(evidence_id),
 CHECK(state IN ('RUNNING','AVAILABLE','FAILED')),
 CHECK((state='RUNNING' AND evidence_id IS NULL AND error_code IS NULL AND finished_at IS NULL)
    OR (state='AVAILABLE' AND evidence_id IS NOT NULL AND error_code IS NULL AND finished_at IS NOT NULL)
    OR (state='FAILED' AND evidence_id IS NULL AND error_code IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```
先写测试再添加迁移/实现。MarketDatabaseFixture清理新增回执在comparison_evidence_operation/version和RAW之前，防止全套测试FK失败；不能改现用清理脚本。

- [x] Step2 写RED本地HTTP测试。ComparisonCollectorClient构造器(String base,String token,ObjectMapper)生产30秒；包内测试构造器另接Duration timeout。向既有`/internal/v1/market/comparison-collect`只发operationId/instrumentId/rawVersionId/start/end五字段，不把lease/hash/definition塞给Python。模拟AVAILABLE及四种SOURCE失败、超时/慢流、>2MiB、301/401/500、未知/重复/尾随JSON、身份/范围/op/源definition不符。捕获请求确认假Token仅在服务头，body不含Token。根形状为state/candidate/errorCode三字段，不能照抄RAW采集器的operationId根协议。

- [x] Step3 写RED接收端MySQL核心测试：
```java
var f=new MarketDatabaseFixture();var clock=new MutableClock(Instant.parse("2026-10-13T12:05:00Z"));
var from=LocalDate.of(2025,10,13);var to=LocalDate.of(2026,10,13);
var raw=raw(f,"raw",from,to,"10",false,0,clock);var r=request(f,raw,from,to,clock);
var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
var market=new MarketVersionRepository(f.jdbc());
var repo=new ComparisonRefreshRepository(f.jdbc(),new ComparisonEvidenceRepository(market,mapper,clock),new ComparisonEvidenceValidator(clock),mapper,clock);
assertTrue(repo.begin(r).execute());
var done=repo.complete(r,candidate(r,"10","20",clock),null);
assertEquals("AVAILABLE",done.state());
assertEquals(done.versionId(),repo.lookup(r.operationId()).versionId());
assertFalse(repo.begin(r).execute());
```
补service测试mock collector只调用一次，重复同ID包括终态租约过去仍回放；同ID换raw/hash/start/end/lease409；并发同ID仅一个execute；成功响应丢失重启后lookup成功；到期GET只读派生FAILED、不写新表或调用源。

- [x] Step4 写真正的锁等待/回滚RED：连接A锁指定comparison_evidence_version的contentHash行，线程B的complete等待；等待确认后推进MutableClock跨lease，释放A锁，断言新证据/operation映射/AVAILABLE回执都未提交。另用数据库触发器或错误约束使回执最终UPDATE失败，断言版本与映射一起回滚、旧版本仍在。不要只在调用前advance就宣称“锁等待跨期”。测试误线程须Future超时及finally关闭连接/线程。

- [x] Step5 RED命令：
```powershell
./scripts/check-comparison-update.ps1 -Modules stock-market-service -Tests 'ComparisonCollectorHttpTest,ComparisonRefreshMysqlTest,ComparisonRefreshHttpTest' *> runtime/sdd007d-task2-red.log
```
Expected: 缺新功能或业务断言失败；检查失败名称，再实现。

- [x] Step6 实现transport：HttpClient连接2秒/NONE重定向，sendAsync配Future.get(30s)覆盖响应body，订阅累计2MiB，finally取消Future和subscription。严格duplicate+trailing+unknown JSON及Candidate反序列化；AVAILABLE必须有配对candidate/error=null，失败无candidate且code与state一致；未知状态SOURCE_INVALID。不记原文、Token；超时SOURCE_TIMEOUT，传输SOURCE_UNAVAILABLE；严格畸形SOURCE_INVALID。

- [x] Step7 实现refresh：结构及原回执优先→固定RAW preflight（不存在不先触发FK原始异常）→短事务begin→事务外collect→完整绑定/目标日/范围核对→complete。参数hash使用固定字段与毫秒lease，不可同ID换租约。begin用INSERT IGNORE+行锁判断execute，正在运行返回RUNNING，不重采。
```java
var known=records.replay(r);if(known!=null)return known;
query.fixedWindow(r);
var claim=records.begin(r);if(!claim.execute())return claim.receipt();
var source=collector.collect(r);
// 成功候选必须匹配r.operationId/instrument/raw/from/to/definition，包含目标日；
// Collected失败或验证失败用受控错误complete；保存状态未知不谎称已保存FAILED。
```
complete成功事务锁回执并校验参数与Clock，内部调用guarded publish，再UPDATE AVAILABLE，最后重新读取Clock并校验，过期抛异常回滚。原RAW头不变、不写RAW last_error。失败保存只改本回执；已过期返回/保存ATTEMPT_EXPIRED。lookup状态读取不调用begin/collect/publish，终态优先，RUNNING到期派生失败。sha256序列化Request前严格要求毫秒精度，避免DB截断导致不同hash。

- [x] Step8 实现POST八字段及GET UUID路由，Controller mapper开启unknown/duplicate/trailing拒绝；unknown/重复query拒绝，日期/UUID严格形状，lease上限120秒。CandidateRequestFilter在规范化`/internal/v1/market/comparison-refresh`路径使用Math.min(limit,4096)，身份检查仍优先。测试无身份畸形401、有效身份4097字节413、重复键/尾JSON422、未知UUID404、GET零采集。Configuration复用现有Clock及aistock.algorithm.url/token，不创建第二个Clock bean。

- [x] Step9 GREEN定向及旧RefreshMysqlTest/ComparisonEvidenceMysqlTest/ComparisonEvidenceHttpTest，选择性提交`feat: 受控刷新比较证据并保存执行回执`。记录source/DB异常分类，不把本地HTTP当真实供应商。

## Task 3: 持久比较任务、固定上下文与周期预算

**Files:**
- Create MS04 task/ComparisonTaskRecords.java；预计迁移`backend/stock-scheduler-service/src/main/resources/db/migration/V7__comparison_update_context.sql`。
- Modify MS04 task/TaskRepository.java、task/README.md；tests/task/TaskMysqlTest.java清理次序。
- Create tests/task/ComparisonTaskMysqlTest.java、README.md（目录已存在时更新说明，不新建空目录）。

**Interfaces:**
```java
// MS04独立DTO，不import MS02包。
public record Snapshot(String instrumentId,UUID rawVersionId,String rawContentHash,
 LocalDate start,LocalDate end,String definition,Instant planAsOf) {}
public record Context(String taskId,LocalDate cycleDate,Snapshot snapshot,int attemptLimit) {}
public TaskRepository.Task createComparison(Snapshot snapshot,LocalDate cycleDate,int attemptLimit,Instant now);
public Context comparisonContext(String taskId);
public TaskRepository.Claim claim(Instant now,Set<String> allowedTypes);
public List<TaskRepository.Claim> pendingReceipts(Set<String> allowedTypes);
```
保留旧claim(now)/pendingReceipts()入口，默认旧两类型；旧Task/Claim record签名不扩充。JdbcTemplate单参构造器保留给现有fixture；新增标记@Autowired的构造器接比较退避秒数900/1800，校验正值，旧退避硬规则不变。createComparison键由服务生成，固定context同事务，回放原任务不替换当日快照；不使用原RAW create()的key正则（该正则不允许冒号），新键不是用户输入。

- [x] Step1 写RED：一日两次扫描、不同RAW同周期、两个并发创建都同taskId/context；cycleDate与planAsOf/now不符、未知证券/过期asOf/一年范围/错definition非法；重复getContext纯读。
```java
var legacy=new TaskMysqlTest();var repo=legacy.repo();var now=Instant.parse("2026-10-13T12:05:00Z");
var snapshot=new ComparisonTaskRecords.Snapshot("SH600030",UUID.randomUUID(),"a".repeat(64),
 LocalDate.of(2025,10,13),LocalDate.of(2026,10,13),"baostock-comparison-backward-close-v1",now);
var first=repo.createComparison(snapshot,LocalDate.of(2026,10,13),3,now);
assertEquals(first.taskId(),repo.createComparison(snapshot,LocalDate.of(2026,10,13),3,now).taskId());
assertEquals(snapshot,repo.comparisonContext(first.taskId()).snapshot());
assertNull(repo.claim(now,Set.of("MARKET_REFRESH","PREDICTION_VERIFY")));
assertEquals(first.taskId(),repo.claim(now,Set.of("COMPARISON_UPDATE")).taskId());
```

- [x] Step2 运行RED：
```powershell
./scripts/check-comparison-update.ps1 -Modules stock-scheduler-service -Tests 'ComparisonTaskMysqlTest' *> runtime/sdd007d-task3-red.log
```
Expected: 新比较创建/上下文/筛选缺失，不能由已有库清理FK异常替代RED。

- [x] Step3 加迁移并实现原子创建：
```sql
CREATE TABLE comparison_update_context (
 task_id CHAR(36) PRIMARY KEY, cycle_date DATE NOT NULL,
 instrument_id VARCHAR(64) NOT NULL, raw_version_id CHAR(36) NOT NULL,
 raw_content_hash CHAR(64) NOT NULL, range_start DATE NOT NULL, target_date DATE NOT NULL,
 definition VARCHAR(64) NOT NULL, plan_as_of DATETIME(3) NOT NULL,
 attempt_limit TINYINT NOT NULL,
 UNIQUE KEY uk_comparison_cycle(cycle_date,instrument_id,definition),
 FOREIGN KEY(task_id) REFERENCES scheduled_task(task_id),
 CHECK(attempt_limit BETWEEN 1 AND 3),CHECK(range_start<=target_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```
scheduled_task.task_type=COMPARISON_UPDATE，idempotency_key=`comp-update:<cycle>:<stock>:v1`，target_date=context.end。父/子同事务插入，任一失败全回滚。已有当日业务键返回已有上下文，不把新RAW塞入旧任务；显式上下文查询校验父类型与字段一致。TaskMysqlTest.repo()清理comparison_update_context在task_attempt/scheduled_task之前。

- [x] Step4 实现claim/pendingReceipts双重类型白名单（仅三种合法type，空集合不查询执行）；用参数占位符构建IN，不拼入调用方任意SQL。过期扫描和领取都按allowedTypes筛选，关闭类型不能因其他工作器tick变为执行。比较QUEUED/RETRY_WAIT的cycleDate早于当前上海日期，直接FAILED/COMPARISON_CYCLE_EXPIRED，不创建尝试；RUNNING先转RECONCILING并核对，不能强当失败。


```java
if(allowedTypes.isEmpty()) return List.of();
if(!Set.of("MARKET_REFRESH","PREDICTION_VERIFY","COMPARISON_UPDATE").containsAll(allowedTypes))
 throw error("TASK_TYPE_INVALID",HttpStatus.UNPROCESSABLE_ENTITY);
var types=allowedTypes.stream().sorted().toList();
var marks=String.join(",",Collections.nCopies(types.size(),"?"));
String sql="SELECT * FROM scheduled_task WHERE task_type IN ("+marks+") "
 +"AND state IN ('RUNNING','RECONCILING') ORDER BY updated_at LIMIT 20";
// jdbc.query绑定types的每个值；claim的过期扫描和领取使用相同白名单。
```
已核对当前TaskRepository的旧类型为MARKET_REFRESH和PREDICTION_VERIFY；保留两者及历史数据。此片不向公开API暴露allowedTypes。

- [x] Step5 写并GREEN预算测试：第一次失败60秒不领取、900秒后领取；第二次1800秒后；第三次终态FAILED；原类型仍60/120。比较上限按COUNT(task_attempt)与context.attempt_limit，人工retry不清零总历史、到上限受控409、跨周期拒绝；attemptLimit=1首次失败即终态，不能修改旧context到3。无效数据直接停止，不将所有错误一律重试。
```java
var claim=repo.claim(now,Set.of("COMPARISON_UPDATE"));
repo.finish(claim,new MarketRefreshClient.Result(claim.attemptId(),"FAILED",null,"SOURCE_TIMEOUT"),now.plusSeconds(1));
assertNull(repo.claim(now.plusSeconds(60),Set.of("COMPARISON_UPDATE")));
assertNotNull(repo.claim(now.plusSeconds(901),Set.of("COMPARISON_UPDATE")));
```
比较新失败策略仅允许SOURCE_BUSY/SOURCE_TIMEOUT/SOURCE_UNAVAILABLE/ATTEMPT_EXPIRED。保存成功versionId就是evidenceId；原finish/reconcile令牌代次保护不变。并发claim唯一、旧令牌不能finish新尝试、上下文/任务回滚无孤儿都需真实MySQL覆盖。

- [x] Step6 GREEN ComparisonTaskMysqlTest及已核实存在的旧TaskMysqlTest/TaskVerificationMysqlTest/RevisionScanMysqlTest。选择性提交`feat: 持久化比较更新任务与预算`。

## Task 4: 计划、独立HTTP、开关隔离与未知结果恢复

**Files:**
- Create MS04 task/ComparisonTaskClient.java、HttpComparisonTaskClient.java、ComparisonTaskHttp.java、ComparisonUpdatePlan.java。
- Modify TaskWorker.java、TaskConfiguration.java、application.yml、deploy/compose.yaml、.env.example、task/README.md。
- Create tests/task/ComparisonUpdatePlanTest.java、ComparisonTaskClientTest.java、ComparisonTaskWorkerTest.java；更新TaskWorkerTest.java/TaskMysqlTest.java/TaskVerificationMysqlTest.java的构造器及mock签名。

**Interfaces:**
```java
// nested records added to ComparisonTaskRecords
public record Item(String instrumentId,String state,String reason,UUID rawVersionId,String rawContentHash,
 LocalDate rangeStart,LocalDate targetDate,UUID evidenceId,LocalDate sourceTo) {}
public record Options(Instant asOf,String definition,List<Item> items) {}
public interface ComparisonTaskClient {
 Options options(List<String> instruments);
 MarketRefreshClient.Result execute(TaskRepository.Claim claim,ComparisonTaskRecords.Context context);
 MarketRefreshClient.Result receipt(TaskRepository.Claim claim);
 final class MissingReceipt extends RuntimeException {}
}
// HttpComparisonTaskClient constructor(String base,String token,ObjectMapper,Clock).
// ComparisonTaskHttp.call(String path,Object body,Duration timeout): JsonNode; fixed configured base,
// GET body=null, max256KiB response, no redirects/retries, typed Remote(status,code).
// ComparisonUpdatePlan constructor(TaskRepository,ComparisonTaskClient,Clock,String token,boolean planEnabled,String instruments,int maxAttempts); runOnce():boolean.
// Worker constructor(repo,market,receipts,predictions,comparisons,clock,token,oldEnabled,comparisonEnabled).
public synchronized void runOnce(); // explicit old-type execution, preserves current test/operator meaning
public synchronized void runComparisonOnce(); // explicit new-type execution, no automatic invocation
```
生产tick根据两开关生成allowedTypes再调用内部runTypes，both false不调用；不能把runOnce()原来用于测试的false绕过行为复制到生产tick。

- [x] Step1 RED本地HTTP：GET就绪字段、READY完整快照、未知/重复JSON、future asOf/非法日期、错误股票/definition、未知状态、超限/慢流/重定向。execute body恰好八字段，operationId=claim.attemptId，lease同原claim；AVAILABLE必须合法UUID且无error，FAILED非空安全码/无version，RUNNING无version/error。
固定传输45/3/5秒真实配置，包内测试注入Duration缩短超时；不能只assert new Options对象而不测试实际HTTP。GET404只转换MissingReceipt；网络/5xx/畸形成功响应标未知，不伪造已保存失败；明确4xx验证错误允许返回FAILED安全码，409先查回执，401/403受控权限故障不泄漏头。响应中请求操作ID不符视未知。

- [x] Step2 RED Plan：19:59不GET、不创建；20点后只READY建日任务；CURRENT/RAW_LAGGING/CALENDAR_UNAVAILABLE不创建。响应asOf超过5秒、definition/范围/ID不符拒绝。HTTP跨上海午夜时重读Clock，不能用旧asOf建新周期；同日RAW变化仍原快照，重启只补当前计划日。空/重复/超50配置启动受控失败，不静默截断。代码断言：
```java
verify(client,never()).execute(any(),any()); // Plan永远只options
verify(repository,never()).createComparison(any(),any(),anyInt(),any()); // 19:59/CURRENT情形
```

- [x] Step3 RED Worker：四种开关组合验证实际tick的allowedTypes与旧调用次数；C false/old true即使数据库有比较QUEUED也不领取。C true/old false不领取两旧任务。UNKNOWN类型注入仍受控unsupported、不打RAW/预测/比较接口。
丢失execute响应→GET AVAILABLE→原任务成功且attempt数1；GET unavailable保持RECONCILING，未过期404不重发，到期404映射ATTEMPT_EXPIRED并按周期预算决定下一尝试。RUNNING原回执只查询，FAILED不再POST原编号；同ID终态重放不执行源。跨日旧尝试在途只查不续费。

- [x] Step4 RED命令：
```powershell
./scripts/check-comparison-update.ps1 -Modules stock-scheduler-service -Tests 'ComparisonUpdatePlanTest,ComparisonTaskClientTest,ComparisonTaskWorkerTest' *> runtime/sdd007d-task4-red.log
```
Expected: 缺新计划/客户端/分支或上述断言失败。

- [x] Step5 实现独立HTTP与Plan。Job配置如下，不能覆盖RAW/预警配置：
```yaml
# scheduler application.yml, under aistock:
comparison-update:
  plan-enabled: ${COMPARISON_UPDATE_PLAN_ENABLED:false}
  worker-enabled: ${COMPARISON_UPDATE_WORKER_ENABLED:false}
  instruments: ${COMPARISON_UPDATE_INSTRUMENTS:SH600030,SH600276,SZ000002}
  scan-poll-ms: ${COMPARISON_UPDATE_SCAN_POLL_MS:60000}
  max-attempts: ${COMPARISON_UPDATE_MAX_ATTEMPTS:3}
  retry-first-seconds: ${COMPARISON_UPDATE_RETRY_FIRST_SECONDS:900}
  retry-second-seconds: ${COMPARISON_UPDATE_RETRY_SECOND_SECONDS:1800}
  market-url: ${COMPARISON_UPDATE_MARKET_URL:http://127.0.0.1:18082}
```
Compose仅scheduler服务传上述八键，market-url固定内部http://stock-market-service:8080；.env.example两开关false、不提交真实.env。max-attempts只1..3，写入新周期Context；改变配置不能重写旧周期。

- [x] Step6 Worker runTypes：先按allowedTypes恢复原回执，再claim同集合；使用注入Clock，每次完成/到期判定取freshNow；比较读取本库Context交client.execute，收到终态才finish，否则recover。不要new网络调用包在TaskRepository事务中。手动runOnce仅旧类型、runComparisonOnce仅新类型都复用同逻辑；tick按配置选集合。现有三处测试构造器更新，保留旧业务断言，不删难通过测试。TaskConfiguration使用既有schedulerClock并新增client bean，禁止重复Clock。

- [x] Step7 GREEN定向与所有scheduler tests，检查旧比较B30秒、AI65秒、预测185秒未改；选择性提交`feat: 调度比较证据更新并恢复未知结果`。保留两新开关false，不在此步做真实采集。

## Task 5: 完整回归、一次独立审查、受控推进与保全

**Files:**
- Create docs/verification-sdd007d.md、docs/evidence/sdd007d-comparison-refresh-checks.json、docs/sdd007d-execution-ledger.md、docs/sdd007d-rollback.md。
- Modify docs/implementation-status.md、docs/pending-items.md、本计划与设计实际交付状态。
- Modify scripts/check-comparison-snapshot.ps1（增加可选OutputDirectory，默认旧路径不变，D显式写runtime/sdd007d/audit，禁止覆盖B证据）、scripts/README.md；Create scripts/export-comparison-update-evidence.ps1（仅内部GET导出）。
- Add targeted tests to `backend/stock-market-service/src/test/java/com/aistock/market/comparison/ComparisonEvidenceOptionsMysqlTest.java`和`backend/stock-prediction-service/src/test/java/com/aistock/prediction/comparison/ComparisonServiceTest.java`，只补跨新旧证据选择/旧固定结果行为，不改计算公式。

- [x] Step1 写RED/GREEN混合窗口测试：两股新to=2026-10-13/from=2025-10-13、一股旧to=2026-10-09/from=2025-10-09，1Y共同起点2025-10-09；新两股要选它们保留的旧版本，不能选不完整新窗口。全部推进后to=10-13；日历冲突不可比较。查询旧结果与AI说明只GET、不自动创建/生成；sourceHash不变。

- [x] Step2 运行一次最终全量：
```powershell
./scripts/check-auth-task1.ps1 *> runtime/sdd007d-backend-final.log
pnpm --dir frontend test *> runtime/sdd007d-frontend-final.log
pnpm --dir frontend build *> runtime/sdd007d-frontend-build.log
docker exec aistock-sdd001-algorithm-1 python -m pytest tests/test_comparison_provider.py tests/test_comparison_process.py
```
Expected: 全量后端0失败/错误/跳过，前端全绿/严格构建，Python相关既有契约全绿；记录实际数量，不把上轮606/148当本轮实跑结果。Maven串行；本地HTTP/SDK测试不访问供应商。

- [x] Step3 整片一次独立审查（executing-plans规定的最终review），五Review Focus全部列入审查包。重评分级后Important一次修复RED→GREEN及全套回归；无第二次独立复审假称，Minor记待办。最终证据包含计划执行ruling与未演练项。

- [x] Step4 保留MS02/MS04旧镜像摘要/标签与runtime回退override；用各库自有账户快照关键计数/hash，不读取session/password/token内容。记录市场/调度迁移末号，原比较ID01a31765-75cf-4bf5-95be-df7037e57b81、成功解释IDa4b1997e-8aeb-4ac4-a74c-92b33a599144的hash；已有RAW工作器可以自然推进，不能将其活动算成本片新增采集。
```powershell
docker compose --env-file .env -f deploy/compose.yaml build stock-market-service stock-scheduler-service
docker compose --env-file .env -f deploy/compose.yaml up -d --no-build --no-deps stock-market-service stock-scheduler-service
```
新比较开关保持false，先MS02新接收端和迁移健康，再MS04。检查V12/V7实际成功、六Java/算法UP/Nacos、内路由匿名401/网关404，旧行为和数据保全。若末迁移号变动，只顺延新文件并记ruling，不能覆盖已有迁移。

- [x] Step5 隔离回退验证：保留当前新版MS02处理在途，关闭比较新计划，冻结比较新claim，QUEUED/RETRY_WAIT通过已有cancel保留历史；RUNNING/RECONCILING查原回执至终态或过期。确认新类型无可领取/待恢复行后才切旧MS04/MS02镜像。隔离库同时加载基线d44300c的真实旧TaskRepository与TaskWorker（git show后复制到本片runtime并统一重命名Legacy类），验证新类型被unsupported以及清理后旧任务可继续；不能只换旧Worker却沿用新Repository的类型过滤，那样会掩盖旧版本问题。整个探针用隔离库事务回滚或独立测试fixture，HTTP依赖mock、零供应商调用，别仅写一句“可以回退”。新增表/数据保留，不降库；旧镜像/Flyway完整启动若未做明确未演练，不以隔离探针替代。

- [x] Step6 真实首验逐股控制。使用仅含非敏感字段的runtime/sdd007d-acceptance.override.yaml叠加：
```yaml
services:
  stock-scheduler-service:
    environment:
      COMPARISON_UPDATE_PLAN_ENABLED: 'true'
      COMPARISON_UPDATE_WORKER_ENABLED: 'true'
      COMPARISON_UPDATE_INSTRUMENTS: SH600030
      COMPARISON_UPDATE_MAX_ATTEMPTS: '1'
```
只含当前一股，完成该日单任务后立刻关闭比较新计划/工作器并只读审查原回执与版本。首股成功后再分别换成SH600276、SZ000002，仍limit1；任何一股失败停止，不启动剩余股，不在同周期放宽limit或改业务键。三个阶段最多各3次数据query，共9次，login/logout另计，既有源槽BUSY也不真实自动重试。计划执行如发生需要额外预算，先完成可审核诊断后再单独询问，不能超出此数。

既有MS04工作器不受关闭比较影响，生产tick按类型过滤已经测试；源调用统计依据应用执行操作及SDK query调用点，不假冒供应商计费token或底层包记录。若执行时间未到上海20点或就绪RAW/日历不足，先保留未验收，不人为推进时钟或改数据制造成功。

- [x] Step7 先增加只读导出工具。export-comparison-update-evidence.ps1参数为TaskIds（恰好3个唯一UUID）、RunName（^[A-Za-z0-9_-]{1,48}$，默认acceptance）。输出固定在runtime/sdd007d/live/<RunName>，从忽略的.env本机读取SERVICE_TOKEN，只供HTTP头使用，finally清引用；不打印授权值。对每个任务仅GET`http://127.0.0.1:18084/internal/v1/tasks/{id}`，要求data.task.taskType=COMPARISON_UPDATE/state=SUCCEEDED且versionId为UUID；再GET`http://127.0.0.1:18082/internal/v1/market/comparison-evidence/{versionId}`，要求证据ID、instrumentId与任务一致、to==task.targetDate、provider=baostock/basis=BACKWARD及hash形状正确。既有Evidence记录不包含definition字段，不能对不存在的字段做断言。恰好三只配置股且无重复，保存裸data为evidence-<instrumentId>.json（不是统一响应wrapper），以及非敏感任务摘要。工具不能POST、采集或选择未成功任务；错误立即停止。TaskIds取本轮实际三股任务详情，不能硬填历史任务ID。

调整已有check-comparison-snapshot.ps1的param增加`[string]$OutputDirectory='runtime/sdd007b/audit'`，令$output由该参数解析（限制在项目runtime内），原默认保持；D的独立路径防止覆盖B原始毕业设计证据。该脚本已核对会编译实际ComparisonCalculator并输出calculation-3M/6M/1Y.json；独立Python需要fixture与output两个目录参数。实际执行为：
```powershell
# 本轮TaskIds由实际三股成功任务记录提供，先检查UUID与结果；导出只读。
./scripts/export-comparison-update-evidence.ps1 -TaskIds $acceptedTaskIds -RunName acceptance
./scripts/check-comparison-snapshot.ps1 -FixtureDirectory runtime/sdd007d/live/acceptance -OutputDirectory runtime/sdd007d/audit
python scripts/verify-comparison-metrics.py runtime/sdd007d/live/acceptance runtime/sdd007d/audit
```
$acceptedTaskIds是操作者从本轮三股任务详情记录获得的3个已验证UUID，不是虚构占位ID，不含凭据。Expected：真实完整样本36指标/曲线/sourceHash复核通过。脚本当前要求真实样本完整VERIFIED；若真实出现MISSING/SUSPENDED，不改数据来凑36通过，记录各状态及对应指标UNAVAILABLE，完整数值样本验收标未完成，按测试和实际数据分别报告。

- [x] Step8 只读审计三股新证据范围/hash、目标日、逐日RAW close/volume/status配对、calendar及缺失状态。通过正常本人登录新建一份对比，确认新日期及指标/刷新；原旧比较与解释hash仍相同。无浏览器工具时如实用用户确认，不伪造Cookie/会话，不调用AI做验收。

- [x] Step9 全部通过后将实际新计划/工作器开为true、INSTRUMENTS现三股、MAX_ATTEMPTS=3；已首验周期attemptLimit=1仍不改写，下一新周期使用3。文档明确一次推进不等于长期连续运行，后续持续观察为待办。部署回退/真实供应商失败保留与用户页面状态分别记录。

- [x] Step10 选择性提交验收文档，保留当前分支无remote不push。不把比较自动更新冒充全系统完工，不删CodeArts/MyBatis-Plus/视觉/自然邮件待办。

## 计划自审与执行门禁

自审覆盖：spec第1—5节→Task1/3/4；第6节预算→Task3/4/5；第7—8节回执/协议→Task2/4；第9节新旧结果→Task5；第10—11节部署/回退/边界→五Task与Review Focus；第12节四交付中MS04拆为Task3持久化和Task4执行，两者各可独立测试，没有新增业务。

2026-10-14：用户确认计划后已按Native实施Task1—4及Task5回归/工具；后端656、前端148、Python18通过。唯一独立审查因账户额度失败，用户选择保存并等待恢复；Task5部署、真实首验与启用尚未完成，新开关未改，新增真实数据/MaaS/邮件调用0。具体交接见docs/sdd007d-execution-ledger.md。


2026-10-14历史状态：Step1/2完成；Step3独立审查未完成；Step4镜像/基线保留完成、部署未执行；Step5隔离旧程序探针通过、完整镜像启动未演练；Step6真实首验未开始；Step7工具实现及旧样本离线审计通过、新更新证据导出未执行；Step8/9未执行；Step10本次阶段证据已保存，不代表整片完工。

2026-10-17最终状态：Task5各交付步骤完成，原审查3项Important一次修复轮次通过完整660/148/18验证；市场13/调度7部署、三股真实首验9次query方法路径、36项指标及页面刷新通过，正常周期已启用。Step5完成的是实际旧程序隔离探针与边界记录，完整旧镜像/Flyway启动仍未演练，不能宣称完整回退通过；长期运行独立保留待办。
