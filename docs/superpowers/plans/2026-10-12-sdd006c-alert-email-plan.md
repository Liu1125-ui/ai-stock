# SDD006C 预警邮件投递实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对新产生且用户允许邮件通知的预警触发，建立可追溯、可取消、结果诚实的邮件投递，并提供本人状态查看。
**Architecture:** MS-05在触发/站内消息事务内保存渠道决策和独立邮件outbox；事务外工作器向MS-01核验账号及邮箱，再调用独立预警SMTP适配器。公开只读状态严格限定本人消息；站内通知不依赖SMTP。
**Tech Stack:** 既有Spring Boot3.2.5、Spring JDBC/JdbcTemplate、MySQL8、Flyway、Spring Mail、Vue3/TypeScript、JUnit5/Vitest。该片以事务和并发为主，不为接技术而重写现有仓储；MyBatis-Plus保留为后续常规业务持久化选型，不宣称当前已接入。
**Spec:** docs/superpowers/specs/2026-10-10-sdd006-alert-notification-design.md V1.1，重点邮件安全与失败处理及C切片。用户已于2026-10-12确认执行；Task1–5已实现并小步提交，Task6已完成独立审查/修复、完整回归和受控部署。真实外发、登录后浏览器视觉验收仍待完成。原逐项清单为验收目标，不将未逐条执行的扩展矩阵标成已覆盖；实际证据见docs/verification-sdd006c.md。

## Global Constraints

- 固定E:/aiStock/项目代码/stock-system；13业务、五个业务微服务、ADMIN/USER和附加实验授权不变。跨服务不跨库联表。
- 不改任务书开发顺序和CodeArts要求；据实记录当前Codex实施，后续补真实CodeArts证据。
- A/B0/B1/B2既有数据及注册验证码流程保留。旧EmailCommand只接受六位验证码，预警不能复用该命令或正文模板。
- 邮件偏好默认关闭，只影响开启以后新产生的触发；不能给旧站内消息补邮件，关闭后重开不复活旧投递。
- 发送前核验当前账号、邮箱验证、身份锚点、规则和偏好修订；停用/改变取消未发送邮件；依赖故障有限重试。
- SMTP不与数据库原子提交；不确定结果UNKNOWN，不盲目重发。SENT只表示SMTP接受，不表示用户收到/已读。
- 日志不含密码、授权码、正文或完整地址；地址仅内部受保护接口传递和加密保存。没有MaaS调用。
- 模板和本轮本机部署默认关闭真实预警邮件工作器。外发验收须单独明确收件人/数量并获得授权；不通过改历史边界或制造业务触发来演示。
- 沿用指定本地分支、小步选择性提交，无远程不虚报推送。每个新第一方代码目录README说明作用/方法。

## 已核对的现有接口与实现

- MS-01 AlertAccountStatusController和MS-05 AlertAccountClient为严格三字段v1：userId/status/checkedAt。保留v1，不能直接加字段使旧严格客户端失效。
- user_account已有revision和email_verified_at；昵称、密码、账号状态、实验授权更新会增加revision。未来邮箱变更/验证状态改变必须同事务递增revision。
- MS-05 AlertEvaluationStore.finish采用owner guard后operation/rule的锁顺序，触发/消息由AlertEvaluationService.apply同事务写入。
- NotificationPreferenceService.get/patch当前deliveryAvailability固定NOT_ENABLED；偏好更新也使用owner guard。
- MS-05现有迁移到V3，MS-01到V2；本片新增通知V4，不修改已应用迁移。无需为身份锚点增加用户表字段。
- MailPayloadCodec只编码EmailCommand；EmailWorker将异常统一重试，不符合预警UNKNOWN语义；本片不能原样复用此工作器。
- 完整回归基线后端487/前端102/算法76（2026-10-11记录）；执行时重新核对源码和实际环境，不预填下一轮通过数。

## 关键细化和取舍

1. 身份锚点：新建受服务令牌保护的GET /internal/v1/users/{id}/alert-context，返回AccountContext(UUID userId,String status,long revision,boolean emailVerified,Instant checkedAt)，不含邮箱。评价使用context核验账号并保存revision。worker再查询GET /internal/v1/users/{id}/alert-mail-target?revision=N，返回MailTarget(UUID userId,String status,long revision,boolean emailVerified,String email,Instant checkedAt)。不存在404、修订不符409、依赖不可用503，内部响应no-store。地址未取到时可保留重试资格，始终锚定触发时revision，不能把旧邮件寄往新地址。
2. 采用现有全账号revision作为保守锚点：昵称/授权变化也会取消旧待发，避免旧身份被重新解释；这是可用性代价，不是发送错误地址。未来独立邮箱版本可替换，但需迁移与测试，当前不增加邮箱更换业务。
3. 每个新trigger保存唯一alert_mail_delivery决策行；不具资格的行终态NOT_REQUESTED，原因SYSTEM_DISABLED/PREFERENCE_OFF/EMAIL_UNVERIFIED。旧trigger无行，公开显示NOT_RECORDED，不能推断过去的偏好。具资格保存PENDING、owner/rule/trigger、rule_revision/preference_revision/account_revision、created_at/expires_at。地址首次取得后加密绑定deliveryId，worker再次核验身份一致，不覆盖为新邮箱。
4. 两个阶段租约：CHECKING可安全恢复（尚未调用SMTP）；SENDING提交后才进入SMTP，过期一律UNKNOWN。重启不得把SENDING还原PENDING。SMTP后保存失败也保持SENDING直至UNKNOWN，禁止执行者直接重发。
5. 计数规则：attempts仅在CAS进入SENDING、即将进行SMTP尝试时递增；依赖核验失败另计check_failures并同样最多3次，避免不发信却耗尽SMTP尝试，也避免依赖故障无限重试。两者均受24小时TTL限制。默认可配置：最大有效期24小时、最多3次明确可重试失败、退避60/300秒、租约120秒、扫描间隔2秒且每次最多1条。网络调用不持DB锁。所有配置有合理范围校验，TTL按触发createdAt，尝试不延长。
6. 独立纯文本模板只取固定触发快照，不调用AI；含股票、交易日、条件/阈值、实际值/口径、来源采集时间和站内查看提示，不含验证码或投资承诺。禁止不可信换行进入邮件头。
7. 依赖失败和明确未提交SMTP可重试；SMTP拒绝仅在能证明该单一收件人未被接受时有限重试。发送后超时、未知异常、部分成功、断连和回写失败归UNKNOWN。保守处理优于重复发信，不声称端到端恰好一次。

## Review Focus

- 原来三字段账号客户端和注册验证码不可因新协议回归：Task1/3/6。
- 偏好关闭再开启、改规则、昵称变化与多worker并发：Task2/3；旧修订不能恢复。
- 调用SMTP前后崩溃/租约过期/发送成功但数据库失败：Task3；UNKNOWN不重发。
- “未创建邮件记录”和“未收到邮件”不能展示为SENT/FAILED：Task4/5。
- worker关闭、SMTP未配置、邮箱未验证和依赖不可用不应吞掉站内消息：Task2/3/5。

## Task 1（已实现并提交）— 版本化账号与收件人契约

**Files:** 新建user/alert/AlertMailContextController.java及AlertMailContextTest.java；修改user/alert/README.md（既有TokenFilter覆盖/internal/v1/users）；新增notification/alert/AlertMailAccountClient.java及AlertMailAccountClientTest.java。旧v1保持。
**Interfaces:** AccountContext和MailTarget精确如上；AlertMailAccountClient.context(UUID)、target(UUID,long)。HTTP连接2秒、整体10秒、body64KiB、禁止重定向/未知字段、严格UUID/日期/类型、checkedAt前后5秒；409返回受控身份改变语义，不能吞成无限依赖重试。

- [ ] RED：新增接口404；匿名/重复服务头401，非法UUID/缺少或重复revision400，邮箱未验证字段如实返回，账户不存在404，错revision409，依赖异常503不含SQL/地址。
```java
mvc.perform(get(path+"/alert-mail-target?revision=2").header("X-Service-Token","fixture")).andExpect(status().isConflict());
mvc.perform(get(path+"/alert-context").header("X-Service-Token","fixture"))
    .andExpect(jsonPath("$.email").doesNotExist());
```
- [ ] 实现只读当前用户表必要字段，不查密码；内部context无邮箱，target仅有效服务身份可取，核对revision及邮箱格式。前端网关不新增内部透传。
- [ ] RED→GREEN HTTP客户端：版本字段严格、身份不一致/未来或过期时间/超大或停滞body/重定向拒绝；真实HTTP fixture验证target409可区分，原v1三字段测试不变。
- [ ] 跑用户/客户端受影响测试及构建，选择性提交feat: add versioned alert mail recipient context。

## Task 2（已实现并提交）— 渠道决策与事务Outbox

**Files:** 新增notification/db/migration/V4__alert_mail_delivery.sql；新包notification/alertmail/README.md、AlertMailRecords.java、AlertMailOutbox.java、AlertMailRepository.java；对应test/alertmail/README.md、AlertMailOutboxMysqlTest.java；修改AlertEvaluationService、配置及调用测试，保留Snapshot既有读取兼容。
**仓储边界:** AlertMailRepository独立构造器注入同一通知数据源的JdbcTemplate；不访问alert包中package-private字段，不跨库。AlertMailOutbox.capture要求已有事务（MANDATORY），沿用外层owner guard；评价服务配置与构造器测试同步接入新依赖，不保留绕过邮件决策的生产构造分支。

**Interfaces:** Decision capture(UUID triggerId,UUID owner,UUID ruleId,long ruleRevision,AccountContext account,Instant createdAt)，必须在现有触发事务/owner guard内调用；NotificationPreference读取由本事务完成，不在事务外捕获可变偏好。deliveryId为UUID，triggerId唯一，同库trigger FK；版本/时效CHECK及领取索引，owner索引。

- [ ] RED：同时创建触发/消息/决策，重复评价只一行；NOT_REQUESTED各原因、历史不补建。强制outbox写失败时三者回滚、回执不虚报成功。
```java
assertEquals(1,countMailRows(triggerId));
assertEquals("NOT_REQUESTED",mailStateWhenPreferenceOff());
assertThrows(RuntimeException.class,()->evaluateWithInjectedOutboxWriteFailure());
assertEquals(0,countCommittedTriggers());
```
- [ ] v2 context替代评价账号读取；账号核验不可用仍按现有安全规则不触发。状态核验成功但邮箱地址尚未获取不阻塞站内消息（地址worker取）。偏好+规则版本在owner锁保护下与trigger一同捕获。
- [ ] 迁移及仓储实现PENDING/CHECKING/SENDING/RETRY_WAIT/SENT/FAILED/CANCELLED/EXPIRED/UNKNOWN/NOT_REQUESTED合法状态；NOT_REQUESTED不参与领取。状态变迁CAS使用owner token、lease；列精度、终态检查、唯一约束与FK在MySQL8测试。
- [ ] 实现可安全恢复CHECKING及必须UNKNOWN的SENDING；同owner guard→delivery/rule的固定锁顺序，不与规则更新反序。只短事务领取；补充两worker领取、回滚、重启、UTC/上海往返测试。
- [ ] 完整后端隔离回归通过后提交feat: persist atomic alert mail decisions。未部署/不发信。

## Task 3（已实现并提交）— 加密模板、SMTP结果和投递工作器

**Files:** 新建alertmail/AlertMailCodec.java、AlertMailTemplate.java、AlertMailTransport.java、SmtpAlertMailTransport.java、AlertMailWorker.java、AlertMailConfiguration.java及对应CodecTest/TemplateTest/WorkerMysqlTest/TransportTest；复用SMTP连接参数但不修改EmailCommand/验证码模板/原工作器行为。修改application.yml、.env.example与compose.yaml，仅示例不得含凭据。
**Interfaces:** encrypt(deliveryId,recipient,body)、decrypt以AAD绑定deliveryId/type/keyId；RecipientDigest为HMAC，AES-GCM随机12字节nonce，复用既有mail.payload-key/digest-key并加领域前缀，禁止验证码密文互解；Transport.send(immutable command)→ACCEPTED/DEFINITE_NOT_ACCEPTED/UNKNOWN，不向调用者透传SMTP正文或地址；Worker.runOnce(Instant now)→int处理条数，外发只由默认false的ALERT_MAIL_WORKER_ENABLED控制；无外发的租约/TTL维护独立运行。

- [ ] RED→GREEN纯模板：四类条件、精确实际值、日变化按后复权对计算、日期/中文/长字段；只来自固定证据；缺字段拒绝，CRLF地址/头注入拒绝。GCM篡改、deliveryId换绑、未知key和类型混淆拒绝。
- [ ] RED工作器：禁用/未验证/邮箱或账号revision变化、偏好/规则变化取消；关再开不复活；依赖不可用重试并保留消息；TTL过期终态EXPIRED；过期lease和过期checkedAt不得发送。
```java
assertEquals("UNKNOWN",recoverSendingAfterRestart().state());
assertEquals(1,fakeSmtp.acceptedCount());
worker.runOnce(now.plusSeconds(121));
assertEquals(1,fakeSmtp.acceptedCount());
```
- [ ] 用纯fake transport和本机测试SMTP服务器注入ACCEPTED、明确未接受、DATA后断连、超时、成功后回写异常；证明UNKNOWN终态不重发。网络前后记录分开；发送前commit SENDING；先核验后调用SMTP仍有无法消除的跨服务竞态，记录边界不声称原子撤回。
- [ ] 有效期/最大尝试/退避/lease验证；默认poll2s、每tick1条，预警关闭不影响注册验证码。启用但配置不完整不领取为发送成功；受控availability NOT_CONFIGURED。
- [ ] 单元/隔离DB/既有验证码回归通过，小步提交feat: deliver alert mail with explicit uncertain outcomes。未真实外发。

## Task 4（已实现并提交）— 本人投递状态查询与服务可用状态

**Files:** 新增alertmail/AlertMailViewService.java、AlertMailController.java及ViewMysqlTest/SecurityTest；修改NotificationSecurityFilter、gateway/NotificationApiProxyController和NotificationPublicClient及测试；修改NotificationPreferenceService及配置。
**Interfaces:** GET /api/v1/messages/{id}/email-delivery；DeliveryView(UUID deliveryId nullable,String state,String reasonCode nullable,int attempts,Instant updatedAt nullable,Instant sentAt nullable)。不存在/他人消息404；无决策行返回NOT_RECORDED，仅表示未记录，不推断当时开关。响应不含密文/nonce/地址/账号身份锚点。Preference.deliveryAvailability为ENABLED/NOT_ENABLED/NOT_CONFIGURED（新增枚举需前后端同步），由实际worker配置及SMTPready决定，与emailEnabled独立。

- [ ] RED：本人/ADMIN他人/匿名/不存在/非法路径、矩阵及编码路径、重复/未知参数、重复Origin/CSRF。以已验证session owner查询，不能使用body/query owner。
- [ ] 实现精确公开路径透传及只读服务；网关新路径不允许内部target；读API不触发SMTP、不修改状态；状态的时间和计数来自同一行。
- [ ] 测试开启配置但SMTP缺失≠已启用、偏好false但服务ready≠收件已开启、NOT_RECORDED≠失败；schema旧消息可读。
- [ ] 完整受影响权限/网关回归通过，提交feat: expose owner scoped alert mail status。

## Task 5（已实现并提交）— 消息详情状态与通知偏好页面

**Files:** 修改frontend/src/api/messages.ts、api/alerts.ts、pages/messages/MessageEvidence.vue及MessagesPage.vue、pages/profile/NotificationPreferences.vue和各对应测试；新共享组件需要README。按实际组件结构实施，不单独新建邮箱设置业务。
**Interfaces:** getMessageEmailDelivery(id)返回Task4 DeliveryView；状态按需展开/刷新才请求，不对全部列表逐行轮询；偏好显示服务状态和个人接收意愿，开启仍需验证邮箱。

- [ ] RED→GREEN：PENDING/处理中/SMTP接受/取消/过期/失败/结果待确认/未记录分别显示；SENT文案“邮件服务器已接受”，不能“用户已收到”；UNKNOWN无直接重发按钮。
- [ ] 详情读失败保留站内内容，账号切换或旧请求不能覆盖新视图；刷新只读，不发送。关闭偏好后不展示邮件一定可撤回的承诺。
- [ ] npm --prefix frontend test与npm --prefix frontend run build通过，选择性提交feat: display alert email delivery state。

## Task 6 — 完整审查、受控部署和验收

**Files:** docs/verification-sdd006c.md、sdd006c-review-and-fixes.md、evidence/sdd006c-alert-mail-checks.json；更新实施状态、待办、进度、既有规格状态及服务README，执行ledger/mirror按既有约定。

- [x] 最终一次独立审查覆盖整片，各任务作者自审；重要发现先RED→GREEN再完整回归，不用每任务代理重复审查。
- [x] scripts/check-auth-task1.ps1隔离后端完整回归，原算法pytest、frontend test/typecheck/build；记录实际数量，覆盖未知结果/隐私/权限/注册验证码/既有站内通知。不得运行会真实发信的验收脚本。
- [x] 保存部署前RAW/预测快照/模型/规则/偏好/站内消息/注册邮件计数和旧镜像ID；只部署所需user/notification/gateway/frontend；worker保持关闭、默认不改变用户偏好；无DB重置或删除卷。
- [ ] 六Java/algorithm健康、内部target匿名401/网关404、本人API及浏览器偏好/消息详情、desktop/narrow及重载一致；若会话过期让用户自行登录，不索取密码。
- [ ] 真实发信测试单独申请具体收件人及最多1封。不得伪造真实行情触发、改历史边界或旧消息补发。若没有符合条件新触发，明确记录“本机fake SMTP与隔离链路通过，真实预警邮件待自然触发”，不以验证码收到冒充预警验收。
- [ ] 用户明确授权后再启用真实worker；外发验证/长期运行以实际结果记录。未获得授权，完成已授权代码和只读部署部分，并精确列明剩余限制，不虚报C整片真实外发通过。
- [x] 选择性本地提交并保留分支，无remote不push；不得混入股票池扩充、模型训练或任务书修订。

## 自审结果与执行交接

覆盖：触发资格/原子outbox→Task2；安全邮箱锚点→Task1/3；偏好和规则竞态/关再开→Task2/3；明确失败/未知/崩溃→Task3；本人状态与旧消息→Task4/5；原注册流程、真实邮件边界和数据保全→Task6。已补充跨包仓储访问与MANDATORY事务边界、SMTP尝试和依赖核验失败分别计数，避免隐含不可访问成员及无限重试。两处并发切点（触发事务和发送前核验）均明确，跨服务最终核验与SMTP之间不保证原子。不得把昵称revision失效误写成邮箱已经变化。

本片采用用户确认的本会话逐任务实施方式。Task1–5与Task6已授权代码/本机部分完成；真实外发和浏览器验收留待后续，范围和剩余限制见核验记录。后续MyBatis-Plus常规业务接入单独规划，不把该技术写成C已使用。
