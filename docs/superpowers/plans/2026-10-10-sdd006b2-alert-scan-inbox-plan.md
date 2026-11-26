# SDD006B2 自动扫描、预警触发与站内通知 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans逐任务内联实施。步骤使用复选框；本文件仅为计划，未执行，不代表功能上线。

**Goal:** 收盘后自动核验本人已启用的预警规则，满足条件时保存一次触发及站内消息；重复任务、重启和来源故障不产生错误或重复通知。

**Architecture:** MS-04持久化独立预警扫描游标及任务，经内部HTTP消费MS-02固定证据和MS-05规则。MS-05拥有评价回执、触发与站内消息，在同库事务中按规则修订号核验并去重；MS-01提供即时账号状态，网关及前端提供本人消息访问。各服务只读写本库。

**Tech Stack:** Java17/Spring Boot/JdbcTemplate/TransactionTemplate/Flyway/MySQL；Vue3/TypeScript/Vitest；沿用Docker Compose及现有受保护HTTP客户端，不增加消息中间件或付费调用。

**Spec:** `docs/superpowers/specs/2026-10-10-sdd006-alert-notification-design.md` V1.1；前置已完成SDD006A、B0、B1，证据见`docs/verification-sdd006b1.md`。范围仍13业务、五业务服务、ADMIN/USER两角色。

## 范围与成功标准

- 四类规则：PRICE_ABOVE、PRICE_BELOW、DAILY_RISE、DAILY_FALL，严格超过才触发；相等不触发。
- 新建、改变条件、重新启用规则，只评价effectiveAfterTradeDate之后的交易日。
- 扫描时只评价最新应到期交易日。每天仍满足可再提醒；不按穿越阈值模式，不批量补发过去各日。
- 同用户、股票、规则ID、交易日最多一条触发与一条站内消息；修订不突破同日去重。已触发记录固定原规则、原证据，不覆盖。
- 本片完成消息列表、未读数、已读操作、规则触发历史和首页简短入口。邮件投递属于C，页面继续明确邮件尚未启用，即使已保存接收偏好也不发送。
- 本片不训练模型、不请求MaaS、不发验证码或预警邮件、不改变股票池；使用现有三个受支持股票。

## Global Constraints

- 统一响应`{code,message,data,traceId}`；内部接口使用X-Service-Token，先鉴权再解析，写请求4096B；HTTP连接2秒，总读取期限30秒，响应上限128KiB，不跟随重定向。
- 时区Asia/Shanghai，默认20:00日线截止。与行情服务配置共用同一部署值，截止前只检查前一自然日之前最近开市日；交易日必须来自完整日历，禁止猜测周一至周五。
- 比较使用BigDecimal及原始十进制字符串，不经过double或显示舍入。复权日变化比较使用交叉乘法；无有效分母不计算。
- 正常用户与管理员只能读取本人规则、触发、消息。账号禁用时不生成新触发；MS-01故障是可恢复失败，不能视作正常或禁用。核验与跨服务禁用存在时间窗口，不承诺跨库原子性。
- 不跨库读取或建立跨库外键；HTTP不在数据库行锁事务内执行；事务提交前核验租约、规则修订和enabled。
- 配置默认关闭，部署和验证后才能开启。已读操作不用付费服务，规则停用无需依赖市场或算法可用。
- 每个新代码目录附README，说明作用与实现方法；选择性git add、小步提交；不提交凭据、运行日志、邮箱或用户密码。

## Review Focus

1. 评价已提交但HTTP丢失：重放相同操作、查回执；租约过期或未知不可直接新操作重发。Task2/3/4测试。
2. 翻页期间规则修改、停用及重新启用：提交前锁规则并比较revision及生效边界；旧任务不按新条件执行。Task2/4测试。
3. 最新日切换、长假、滞后RAW、缺复权数据：过期任务跳过，价格与变化可用性分离，缺证据留下原因并可再核验。Task1/2/4测试。
4. 同日RAW修订、多个任务竞争：已触发保留，未触发可用新证据评价，同日唯一键为最终防线。Task2/4测试。
5. 批量已读混入他人ID、管理员读取他人消息：整批拒绝且不部分更新，分页和请求体有界。Task5/6测试。

## 核心数据流与接口

1. MS-04检查共享市场日历覆盖和有效期；必要时通过现有`POST /internal/v1/market/calendar/refresh`刷新。读取现有revision-events独立游标，同时每小时对所有启用规则作最新日对账，修复漏事件和临时失败。
2. 通过MS-05分页取得规则ID、ownerId、instrumentId、revision、effectiveAfterTradeDate。股票级准备共享证据；MS-02现有alert-boundary给出rawVersionId和expectedCompletedTradeDate，现有alert-evidence/prepare按固定RAW与日期生成证据。
3. MS-04按规则提交固定evidenceId和revision给MS-05。MS-05自己向MS-02读取固定证据及当前边界，核验股票、日期、定义、内容哈希和当前RAW一致性；不信任调度传入价格或阈值。再向MS-01核验账号状态。
4. MS-05事务锁操作及规则，检查租约/修订/启用/生效日期，精确比较；触发、消息、最终回执同事务保存。网络调用均在事务外。提交前本地时间仍须符合先前边界的截止窗口，否则返回可重试边界变化。
5. 网关转发本人触发及消息接口。刷新和已读只读取/更新已保存记录。

### 新内部接口（统一ApiResponse）

- MS-01：`GET /internal/v1/users/{uuid}/alert-status` → AccountStatus(userId:UUID,status:ENABLED|DISABLED,checkedAt:Instant)。不存在404，故障503；仅返回必要状态，不返回邮箱或会话。消费者接受最多5秒旧值/5秒未来漂移。
- MS-05：`GET /internal/v1/alerts/due?after={uuid}&limit=50&instrumentId={optional}` → RulePage(items:List<RuleRef>,nextCursor:String?)。RuleRef(ruleId:UUID,ownerId:UUID,instrumentId:String,revision:long,effectiveAfterTradeDate:LocalDate)。只返回enabled规则，按ruleId升序、严格向前；无日期过滤，日期由固定证据及事务检查。
- MS-05：`POST /internal/v1/alert-evaluations`，EvaluateRequest(operationId:UUID,ruleId:UUID,revision:long,evidenceId:UUID,leaseUntil:Instant)。不接受用户、行情值或阈值字段；租约不超过120秒。
- MS-05：`GET /internal/v1/alert-evaluations/operations/{uuid}` → EvaluationReceipt(operationId:UUID,state:String,triggerId:UUID?,reasonCode:String?)。
- 状态：RUNNING、TRIGGERED、ALREADY_TRIGGERED、NOT_MET、SKIPPED_RULE_CHANGED、SKIPPED_INACTIVE、SKIPPED_NOT_EFFECTIVE、SKIPPED_STALE、EVIDENCE_UNAVAILABLE、FAILED。账户禁用用SKIPPED_INACTIVE；证据缺失或停牌记录EVIDENCE_UNAVAILABLE和具体原因；依赖故障FAILED并分类。重复相同operation请求返回原回执，改内容409，过期RUNNING原子终结FAILED/ATTEMPT_EXPIRED。
- 内部服务间身份只由规则owner及MS-01确认，不允许外部请求伪造owner。内部接口不可经网关访问。

### 新公开接口

- `GET /api/v1/alerts/{id}/triggers?page=1&size=20`：本人规则触发历史，统一页面最大size50。
- `GET /api/v1/messages?page=1&size=20&unreadOnly=false`：本人消息，按createdAt/id降序，返回items/page/size/total/unreadCount；单次查询内采用一致读，接受其他请求并发更新导致下一次计数变化。
- `GET /api/v1/messages/unread-count`：{unreadCount:long}，默认刷新/页面可见时读取，不高频轮询。
- `PATCH /api/v1/messages/{id}/read`：空JSON对象，重复成功不改变最初readAt。
- `POST /api/v1/messages/read`：{ids:UUID[]}，1至50个唯一ID。锁定本人消息并先校验整批归属；混入不存在或他人ID统一404，整批回滚。不实现无限制“全部已读”。
- Message字段：id/triggerId/ruleId/instrumentId/tradeDate/condition/threshold/rawClose/previousBackwardClose/currentBackwardClose/definition/evidenceId/rawVersionId/capturedAt/createdAt/readAt。价格规则无需显示复权分母。UI显示变化值仅作展示，注明复权口径；触发历史以固定字段呈现，绝不重新取当前阈值。

## 文件与模块边界

所有路径相对工程根`E:/aiStock/项目代码/stock-system`；既有包内新增文件复用README，新增inbox及scheduler/alert目录随建README。

### Task 1 — 权威账号状态及固定证据客户端

**Files:**
- Create: `backend/stock-user-service/src/main/java/com/aistock/user/alert/README.md`、`AlertAccountStatusController.java`；复用`account/UserRepository.java`只读账号状态；新增`alert/AlertAccountTokenFilter.java`仅保护此内部路径。当前SecurityConfiguration对/internal/**放行，不能依赖其自动认证：过滤器须常量时间比较配置的服务令牌，未配置时拒绝，先认证后解析UUID；路径规范化并测试矩阵/编码变体。
- Create: `backend/stock-notification-service/src/main/java/com/aistock/notification/alert/AlertEvidenceClient.java`、`AlertAccountClient.java`。
- Test: 各服务对应`src/test/java/com/aistock/{user,notification}/alert/AlertAccountStatusTest.java`、通知包`AlertEvidenceClientTest.java`；新测试目录带README。

**Interfaces:** AccountStatus与读取证据/当前边界客户端按本计划核心接口返回；失败抛受控BusinessException，不泄露原始响应。

- [x] 写失败测试：内部匿名/错误令牌401，ENABLED/DISABLED/不存在与依赖故障；响应错ID、未来时间、非法类型、越界体积、停滞响应体；证据哈希不符、股票或日期不符拒绝。
```java
assertEquals(401, anonymousAccountStatus(userId).status());
assertThrows(BusinessException.class, () -> evidenceClient.read(tamperedEvidenceId));
assertEquals("DISABLED", accountClient.status(disabledUser).status());
```
- [x] 定向测试先运行确认目标RED，再实现受保护状态接口和有界客户端；只读，不复制用户凭据。对证据使用B1相同canonical字段顺序重算哈希并测试正常真实格式。
- [x] 运行对应模块verify，确认GREEN；选择性提交`feat: add alert account and evidence checks`。

### Task 2 — 精确评价、事务去重与不可变消息

**Files:** 通知alert包新增`AlertEvaluationRecords.java`、`AlertComparator.java`、`AlertEvaluationStore.java`、`AlertEvaluationService.java`、`AlertEvaluationController.java`、`AlertRuleScanController.java`；修改`AlertRuleRepository.java`/`AlertRuleService.java`只增加锁规则读取及实际availability策略，不改边界口径；新增`backend/stock-notification-service/src/main/resources/db/migration/V3__alert_evaluation_inbox.sql`；测试`AlertComparatorTest.java`、`AlertEvaluationMysqlTest.java`、`AlertEvaluationHttpTest.java`。

**Interfaces:** Comparator.matches(condition:String,threshold:BigDecimal,raw:BigDecimal,previous:BigDecimal,current:BigDecimal):boolean；Service.evaluate(EvaluateRequest):EvaluationReceipt；receipt(UUID):EvaluationReceipt。服务内部取得RuleRef和owner，借Task1取权威依据。

**DB:**
- alert_evaluation_operation：operation_id主键、request_hash、rule_id/revision、evidence_id、lease_until、state/reason_code/trigger_id、created_at/finished_at。
- alert_trigger：trigger_id主键、owner_id、instrument_id、rule_id、trade_date、rule_revision、snapshot_json、created_at；唯一(owner_id,instrument_id,rule_id,trade_date)，JSON合法检查。
- inbox_message：message_id主键、trigger_id唯一且本库外键、owner_id、created_at/read_at；owner/read/created/id索引。事务先存触发、再消息、再最终回执；同触发仅一条消息。无需C邮件表，本片禁止创建或发送邮件任务。
- 所有状态/非空组合用CHECK约束；阈值及数值以快照字符串保存，状态更新用精确CAS及行锁，不捕获所有SQL异常伪装重复键。

- [x] 写比较RED测试，至少精确涵盖相等、上下浮动最小位、跌幅符号及除权RAW100→50/后复权100→100。
```java
assertFalse(matches("PRICE_ABOVE", d("26"), d("26"), null, null));
assertTrue(matches("DAILY_RISE", d("2"), null, d("100"), d("102.000001")));
assertFalse(matches("DAILY_FALL", d("2"), null, d("100"), d("98")));
```
- [x] 实现比较：上涨`100*(current-previous) > threshold*previous`，下跌`100*(current-previous) < -threshold*previous`，previous>0。价格直接compareTo。禁止先除法舍入。
- [x] MySQL测试RED：同操作重放/内容冲突、两个操作并发同日、触发消息回执原子回滚、停用/条件修改竞态、账号禁用与故障、证据缺失/停牌、历史/未来/边界日、已有触发修订保留、租约过期及超时后查回执。
```java
assertEquals(first, service.evaluate(request));
assertEquals(1, countTriggers(ruleId, tradeDate));
assertEquals(1, countMessagesForTrigger(first.triggerId()));
assertEquals("SKIPPED_RULE_CHANGED", service.evaluate(oldRevisionRequest).state());
```
- [x] 实现有界operation start/外部读取/最终本地事务；规则owner锁与现有修改锁顺序一致，防止停用竞态和死锁。并发唯一冲突必须查出原触发，返回ALREADY_TRIGGERED，旧消息不覆盖。
- [x] 规则扫描分页严格验证after/limit/instrument；HTTP先认证、严格字段/UUID/日期/体积；返回受控错误与reasonCode，不将数据库异常传给API。
- [x] 完整通知模块及隔离MySQL回归通过，提交`feat: evaluate alert rules and persist inbox triggers`。

### Task 3 — 独立预警任务与回执恢复

**Files:** 新包`backend/stock-scheduler-service/src/main/java/com/aistock/scheduler/alert/`：`README.md`、`AlertJobRecords.java`、`AlertJobRepository.java`、`AlertTaskClient.java`、`AlertJobWorker.java`；SQL`backend/stock-scheduler-service/src/main/resources/db/migration/V5__alert_jobs.sql`；测试对应`alert/README.md`、`AlertJobMysqlTest.java`、`AlertJobWorkerTest.java`、`AlertTaskClientTest.java`。修改既有`task/TaskWorker.java`明确既有两种类型分派，未知类型拒绝且不调市场刷新，并更新其测试。

**Interfaces:** Job(ruleId,revision,instrumentId,tradeDate,rawVersionId,evidenceId?,stage,state,roundId)；Repository.claim(Instant):Claim?、saveEvidence(Claim,UUID):boolean、finish(Claim,EvaluationReceipt):boolean、pendingReceipts():List<Claim>。Claim固定jobId/attemptId/stage/leaseUntil及payload。外部证据operation和评价operation分别持久化，不能共用一次操作ID。

**设计选择:** 独立alert_job/alert_job_attempt，不向scheduled_task添加业务字段或第三种类型，因此无需修改原两类型CHECK；保留原行情和预测验证行为。修复原TaskWorker的兜底else，防止将未知类型误当行情刷新。此处对总体设计“新增类型”作独立任务表细化，避免扩宽共享记录。

- [x] RED测试持久化stage PREPARE_EVIDENCE→EVALUATE_RULE、抢占租约、崩溃重启、超时先查两服务回执、失败有界重试。
```java
worker.runOnce();
assertEquals("EVALUATE_RULE", jobs.get(jobId).stage());
worker.runOnce();
assertEquals("SUCCEEDED", jobs.get(jobId).state());
assertEquals(0, marketRefreshCallsForUnknownTaskType());
```
- [x] 实现任务状态QUEUED/RUNNING/RECONCILING/RETRY_WAIT/SUCCEEDED/SKIPPED/FAILED。每阶段最多3次明确失败重试，默认60/120秒退避，租约120秒；超时未知保留RECONCILING，先查原operation。
- [x] prepare AVAILABLE持久evidenceId后才能进入评价；SKIPPED_STALE结束旧任务，最新日由对账产生。评价TRIGGERED/ALREADY_TRIGGERED/NOT_MET为SUCCEEDED，其余SKIPPED_*为SKIPPED；缺证据与依赖故障按具体code有限重试，耗尽FAILED可由新对账轮再查。
- [x] 回执404且租约未到期继续等待；已过期的404需依赖远端“过期请求不会提交”契约才可结束并排新attempt。远端不可达不得假设404。实现阶段转换/回执落库均校验claim标识，不接受旧worker回写。
- [x] 市场证据按(rawVersionId,tradeDate)共享已验证结果，重试只复用当前有效证据；新RAW版本需重新准备。不得用任一规则失败阻断同股其他规则，也不逐规则重复采集。
- [x] scheduler隔离MySQL与旧TaskWorker测试通过，提交`feat: persist alert jobs and recover remote receipts`。

### Task 4 — 日历刷新、事件扫描与最新日对账

**Files:** 同scheduler/alert包新增`AlertScanRepository.java`、`AlertScanPlan.java`、`AlertMarketClient.java`；新增独立`backend/stock-scheduler-service/src/main/resources/db/migration/V6__alert_scans.sql`，V5只创建任务及attempt，V6创建事件/对账/日历刷新游标，不改已应用迁移；测试`AlertScanMysqlTest.java`、`AlertCalendarPlanTest.java`。修改scheduler/application.yml及application-compose.yml、deploy/compose.yaml和`.env.example`仅添加非敏感开关/策略。

**Interfaces:** runOnce():boolean；scan.cursor(kind:EVENT|RECONCILE,Instant):Cursor；scan.publish(Cursor,RulePage,List<Job>,Instant):boolean，游标与本页job创建同事务。独立event cursor不能共用verification_revision_scan。

- [x] RED：分页第二页失败不推进、双扫描者CAS竞争、规则修改/插入造成分页遗漏由下一轮恢复、假期/周末/20点前后、日历过期/缺口、RAW滞后、积压事件仅最新日、同日修订和缺证据恢复。
```java
assertFalse(plan.runOnceWithSecondPageUnavailable());
assertEquals(savedCursor, scans.cursor("EVENT", now));
assertEquals(0, triggersCreatedForStaleEvent());
assertEquals(latestDueTradeDate, jobs.lastCreated().tradeDate());
```
- [x] 配置建议：alert-plan-enabled=false、alert-worker-enabled=false；poll-ms=60000、reconcile-minutes=60、page-size=50；单tick最多1页；有界任务队列遵循现有三个股票规模。按当前轮ID/规则修订/RAW版本/交易日SHA256生成<=128字符唯一jobkey，事件/对账共用逻辑去重，触发唯一约束最终兜底。
- [x] 日历查询覆盖[截止日前365天,截止日]，完整自然日和7天TTL校验（总范围不满400天、起始不早于今天减366天，符合现有CalendarService）；不将GET成功等同有效。每日最多一次主动刷新，失败默认5分钟退避，不每个规则刷新。刷新失败但现快照仍完整且有效可继续，否则暂停评价并记录原因。新日期越过cutoff时重算边界，不沿用旧轮日期。
- [x] 每60秒消费一个revision event；只将相关股票最新HEAD送入任务，事件载荷旧RAW不直接用于最新评价。积压扫描每tick有界；每小时完整扫描启用规则补查。完整轮结束记录完成时间/下一轮时间，重启从保存页继续；当最新日已变时重置旧轮，只做新日。
- [x] 验证UNKNOWN任务仍占住相同评价工作，不让下一轮绕过未决operation；先解决回执再重排。NOT_MET同日新RAW可以重评，已触发保留原快照。
- [x] scheduler/market/notification契约回归通过，配置默认保持关闭，提交`feat: schedule latest-day alert reconciliation`。

### Task 5 — 本人消息与触发历史API、网关

**Files:** 新通知`inbox/README.md`、`InboxRecords.java`、`InboxService.java`、`InboxController.java`、`AlertTriggerController.java`；修改alert/NotificationSecurityFilter.java、NotificationResponseLimit.java；修改gateway/NotificationApiProxyController.java、ProxyHeaderPolicy.java。测试通知`inbox/README.md`、`InboxMysqlTest.java`、`InboxSecurityTest.java`及gateway/NotificationProxyTest.java。

**Interfaces:** 本计划公开接口；所有owner从已验证session解析，不从body/query读取。统一Message/Page返回；时间ISO、数字字符串。

- [x] RED：本人/他人/管理员越权、未登录、来源与CSRF、路径编码/矩阵绕过、严格分页/UUID/批量上限、单条及批量重复读。
```java
assertEquals(404, markBatchAsRead(ownerA, List.of(messageA, messageBFromOwnerB)).status());
assertNull(readAt(messageA));
assertEquals(firstReadAt, markReadAgain(messageA).readAt());
```
- [x] 实现一致读分页及计数、整批校验后更新；单条已读保持首次readAt。只允许上述精确公开路径和方法，网关仍拒绝内部评价/扫描接口。
- [x] 测试触发历史直接用固定快照，规则修改不改变旧消息。更新规则evaluationAvailability为配置受控ENABLED/NOT_ENABLED，不将计划写成已启用；Preference.deliveryAvailability继续NOT_ENABLED直到C完成。
- [x] 通知/网关隔离MySQL和安全回归通过，提交`feat: expose owner-scoped alert inbox`。

### Task 6 — 消息中心与规则历史页面

**Files:** Create frontend/src/api/messages.ts；新`frontend/src/pages/messages/README.md`、`MessagesPage.vue`、`MessagesPage.test.ts`；修改`frontend/src/pages/alerts/AlertsPage.vue`及测试、`frontend/src/api/alerts.ts`、`frontend/src/router/index.ts`、`frontend/src/App.vue`及实际首页组件`frontend/src/pages/market/MarketHomePage.vue`。

**Interfaces:** types Message、MessagePage、TriggerPage按公开接口；listMessages、unreadCount、markRead、markBatchRead、listAlertTriggers使用现有request封装。路由/messages需要登录；保持Vue script setup。

- [x] Vitest先RED：全部/未读切换、分页、选择最多50条、重复标记、请求失败保留列表、旧请求结果不覆盖新筛选；点击消息去规则历史，无需新预测或MaaS。
```typescript
expect(screen.getByText('复权口径日变化')).toBeVisible()
expect(api.markBatchRead).toHaveBeenCalledWith(selectedIds)
expect(screen.queryByText('邮件已发送')).not.toBeInTheDocument()
```
- [x] 实现消息中心：股票、行情日期、条件及阈值、实际触发值、来源采集时间、已读状态。详情展示固定证据ID/口径，长标识折叠避免列表拥挤；原值与展示值分离。
- [x] 规则页增加“触发历史”和已核验/待核验原因说明；空记录明确尚无触发，不能写成从未满足条件。导航未读徽标，首页仅最近3条提醒摘要和查看全部入口；复用listMessages(size=3)，不增加专用摘要API。
- [x] 不用无限轮询：登录后、进入消息页及用户手动刷新时更新未读数；账号切换清除旧数据，后台请求401时沿用会话过期流程。
- [x] 执行frontend的`npm test`与`npm run build`，确认类型检查/测试通过，提交`feat: add alert message center and trigger history`。

### Task 7 — 全链路自审、部署及验收证据

**Files:** docs/verification-sdd006b2.md、docs/sdd006b2-review-and-fixes.md、docs/evidence/sdd006b2-alert-inbox-checks.json；更新implementation-status.md、pending-items.md、总体设计状态及相关服务README。执行时按executing-plans要求一次最终独立审查，仅审查本切片；先复现重要问题再修复，未解决项明确记录。

- [x] 作者逐条核对规格覆盖、接口签名、迁移顺序、锁顺序与失败恢复；定向GREEN后运行`scripts/check-auth-task1.ps1`完整隔离后端回归、算法原测试、前端测试/构建。按实际报告记录数量，不预填通过。
- [x] 部署前保存当前RAW头、预测/模型哈希、规则/偏好、已有消息及邮件计数。部署必要服务；网关地址变化时核对前端代理，不能仅凭容器启动声称可用。
- [x] 本机固定时钟隔离集成链路：规则生效边界之后一天的真实格式数据，覆盖触发/相等不触发/重复扫描/重启/停用/缺复权/跨用户。任何自建日期或数据明确标测试夹具，不冒充真实行情或真实持续运行。
- [x] 真实当前日只读核对全部三股及本人规则，规则生效边界不可为演示而改写。若当前无符合条件的新交易日，记录“已部署、真实触发待下一有效交易日”，不人工伪造成功通知。
- [x] 六Java服务及algorithm健康、内部401/网关404、本人消息刷新及重载一致。启用后台扫描会产生实际站内消息，依照已批准功能开启；没有邮件发送或MaaS调用。
- [x] 分开记录自动任务执行事实和真实触发事实，保留重放与重启证据；验收后更新文档并选择性本地提交，无远程时不虚报推送。

## 执行次序与暂停点

按Task1→2→3→4→5→6→7执行，沿用当前指定目录；实施前检查工作区、迁移号及现有签名，如与计划不同先纠正计划。每任务测试失败停在当前问题，不能带失败扩展后续任务。B2本身可在上述任务之间小步提交；不得以推进B2为由顺带实施C邮件。

## 作者自审（计划阶段）

- 已覆盖总体设计：四类阈值/生效边界/每日一次/同日修订/停用竞态/账号状态/积压最新日/独立游标/断页恢复/本人消息/已读/首页入口；邮件独立C不在本计划中。
- 关键细化：独立任务表保持现有task CHECK不变；同时把旧TaskWorker隐式else改成明确类型分派。保护既有预测验证与行情刷新，避免扩大共享Claim。
- 按实际文件核对：现有通知身份接口基于session，不能直接用于无session后台评价，因此Task1新增最小账号状态接口；现有前端availability固定NOT_ENABLED，因此Task5/6一并修改。
- 真实验收受规则生效日期及未来有效交易日约束，不为演示绕过边界。连续可用性和实际除权样本继续作为后续观察事项。
- 本文件尚未实施；不填写预计测试通过数或完成日期。用户确认计划后按既有内联执行方式推进。

## 执行更新
Task1至Task4已实现并通过完整回归，详见docs/sdd006b2-progress.md。Task5至Task7未完成；未部署、未开启自动扫描，最终独立审查仍待Task7。

Task4执行细化：额外拆出HttpAlertMarketClient、AlertMarketClientTest、AlertScanPlanMysqlTest，保持接口与实现分离；publish消费Input并在游标事务内创建Job。日历刷新范围[today-366,today]保证一天内截止点变化后仍覆盖cutoff-365；默认活动队列1000、三股/每页50、小时补查均有界。以上不是已部署状态。

Task5已完成：本人消息/未读数/单条与批量已读/固定快照触发历史及精确网关路由；完整后端481项通过，见docs/evidence/sdd006b2-inbox-tests.json。NotificationResponseLimit保持既有内部身份响应64KiB保护（不处理公开分页），原保护测试通过。默认ALERT_EVALUATION_ENABLED=false；邮件仍未启用。Task6/7尚未完成，未部署。

Task6已完成：消息中心/固定证据组件/规则历史/首页最近三条/未读Pinia徽标与401处理。前端102项测试、严格类型检查与生产构建通过。新增stores/inbox及MessageEvidence/TriggerHistoryPanel/RecentMessages保持共享逻辑清晰；未部署，浏览器视觉与全链路验收在Task7进行。

Task7于2026-10-11完成：最终完整487/102/76通过及构建通过，已部署开启扫描；实际日历范围读取问题先RED后修复，无真实触发造数。验收与限制见docs/verification-sdd006b2.md。以上早期“未部署”段落为历史过程状态。
