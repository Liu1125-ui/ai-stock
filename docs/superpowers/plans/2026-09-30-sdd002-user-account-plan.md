# SDD-002 用户业务实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本项目采用当前会话按顺序实施、最终独立审查，不并行拆开共享认证逻辑。

**Goal:** 完成经真实邮箱验证才能创建账号的注册、可撤销会话、本人基本资料和管理员用户管理。

**Architecture:** MS-01管理账号与验证，MS-05投递邮件，网关提供统一浏览器入口；服务独立数据库。发送意图与邮件投递各自持久化，通过稳定requestId和短事务认领恢复，不跨库事务。身份由用户服务会话解析，安全规则在服务端执行。

**Tech Stack:** 现有Java17、Spring Boot3.2.5、JdbcTemplate、MySQL8、Flyway、Feign/Nacos、Spring Security、Vue3/TypeScript/Pinia/Vite/Element Plus。当前Spring Security及测试依赖优先使用Boot BOM管理版本，不从最新版文档直接拷贝不兼容API。

**Spec:** [主规格](../specs/2026-09-30-sdd002-user-account-design.md)；[数据库设计](../specs/2026-09-30-sdd002-database-design.md)。

## Global Constraints

- 用户必须填写邮箱，验证成功后才能创建账号；登录使用邮箱与密码，昵称是展示名称。
- 注册只能创建普通用户，管理员通过受控部署初始化创建，不开放管理员自行注册。
- MS-01拥有用户、注册挑战、注册回执、登录会话、实验授权和管理审计。MS-05拥有邮件发送、重试与状态。
- 服务间通过Feign/Nacos调用，不跨库读取。
- 验证码、密码、原始会话凭据不进入日志。
- Cookie使用HttpOnly、SameSite=Lax，正式HTTPS环境必须Secure，本机HTTP配置可关闭Secure。
- 真实SMTP已配置并完成用户收件、验证注册与登录；测试邮件适配器仍仅用于隔离测试。
- CodeArts生成和重构保留实际源码差异，Codex审核验证；尚无CodeArts证据时不标工具要求完成。
- 每个实际创建的自建代码目录附README，说明作用和实现；不按本计划预先堆空目录。

## Review Focus

1. 两个浏览器同时申请/消费验证码：同邮箱冷却必须串行，一个挑战仅成功一次，Task1/3用MySQL并发测试固定。
2. 验证错误后事务抛异常：错误计数不能回滚，Task3验证第六次即使输入正确码也拒绝。
3. 比对密码期间管理员禁用：登录建会话前必须再检查版本及状态，Task4用双线程同步屏障复现。
4. SMTP已接受但响应超时、worker重启：重试可能重复邮件但不能重复发送记录/账号，Task2/7检验稳定requestId和认领版本。
5. 登录后第一个写请求或退出后再次登录：前端必须取新CSRF令牌，Task4/6固定浏览器与API测试。

## 路径与组件接口

所有下列路径相对正式工程根目录E:\aiStock\项目代码\stock-system。Java服务业务文件按account/registration/session/security/admin分包；消息文件按email分包。公共模块只复用ApiResponse、BusinessException、TraceIds，不新增共享用户Entity。

核心接口均在对应任务内实现并测试：

- `EmailAddress.normalize(String raw): String`，规范化并校验ASCII邮箱。
- `SecretDigests.code(UUID challengeId,String email,String code): String`，HMAC绑定验证码；`SecretDigests.session(String token): String`。
- `RateLimitService.reserveRegistration(String email,String source,Instant now): void`，数据库原子限频；`RateLimitService.recordLoginFailure(String email,String source,Instant now): void`。
- `EmailDeliveryService.accept(EmailCommand command): DeliveryView`；`EmailDeliveryService.find(UUID requestId): DeliveryView`；`EmailWorker.runOnce(Instant now): int`。
- `RegistrationService.request(String email,String source): ChallengeView`；`RegistrationService.register(RegisterCommand command): RegistrationResult`；`RegistrationDispatcher.runOnce(Instant now): int`。
- `SessionService.login(String email,String password,String source): IssuedSession`；`SessionService.resolve(String rawToken): Identity`；`SessionService.logout(String rawToken): void`。
- `ProfileService.rename(UUID actorId,String nickname,long revision): ProfileView`；`ProfileService.changePassword(UUID actorId,String oldPassword,String newPassword): void`。
- `AdminUserService.setStatus(UUID adminId,UUID targetId,String status,long revision): ProfileView`；`AdminUserService.setExperimentGrant(UUID adminId,UUID targetId,boolean enabled,long revision): ProfileView`。

数据记录：EmailCommand(requestId,challengeId,email,code,expiresAt)，DeliveryView(requestId,state,expiresAt)，ChallengeView(challengeId,expiresAt,resendAfter,state)，RegisterCommand(requestId,challengeId,email,code,password,nickname)，RegistrationResult(userId,replayed)，IssuedSession(rawToken,expiresAt,identity)，Identity(userId,role,experimentAllowed,credentialVersion)，ProfileView(userId,email,nickname,role,status,experimentAllowed,revision)。含secret的record重写toString或改为不自动输出字段的类，IssuedSession只在设置Cookie处使用，不能序列化为API响应。

## Task 1：用户数据库、安全工具与真实MySQL测试

**Files:**
- Modify: `backend/stock-user-service/pom.xml`、`src/main/resources/application.yml`（下列src均属本模块）。
- Create: `src/main/resources/db/migration/V1__account_auth.sql`。
- Create: `src/main/java/com/aistock/user/account/EmailAddress.java`、`SecretDigests.java`、`UserRepository.java`；`registration/ChallengeRepository.java`、`RateLimitService.java`、`RateBucketRepository.java`；相应README。
- Test: `src/test/java/com/aistock/user/account/EmailAddressTest.java`、`SecretDigestsTest.java`、`AuthDatabaseTest.java`、`registration/RateLimitServiceTest.java`。
- Test support: `src/test/java/com/aistock/user/support/AuthDatabaseFixture.java`、`MutableClock.java`。

**Interfaces:** 提供上文EmailAddress、SecretDigests、RateLimitService；Repository只接收本服务JdbcTemplate，提供按ID/规范化邮箱查询、FOR UPDATE和revision条件更新。AuthDatabaseFixture使用Testcontainers MySQL8隔离数据库，执行实际Flyway迁移，提供jdbc()/clock()、关闭时只处理自身测试容器。

- [ ] 编写邮箱规范化测试和SQL唯一约束/授权生成列测试；测试夹具不连接stock_user。Testcontainers依赖用BOM管理，Flyway core与MySQL支持沿用行情模块已验证依赖。

```java
@Test void emailUsesOneLoginKey() {
    assertEquals("user@example.com", EmailAddress.normalize(" User@Example.COM "));
    assertNotEquals(EmailAddress.normalize("u+a@example.com"), EmailAddress.normalize("u@example.com"));
    assertThrows(BusinessException.class, () -> EmailAddress.normalize("bad-address"));
}
```

- [ ] 运行 `mvn -pl stock-user-service -am test '-Dtest=EmailAddressTest,AuthDatabaseTest,RateLimitServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，确认缺组件或迁移导致预期失败，而不是连接到现用数据库。
- [ ] 按数据库设计生成V1迁移；实现HMAC、BCrypt参数、规范化、原子桶及UTC转换。SMTP等配置不在此步修改运行环境。

```sql
INSERT INTO auth_rate_bucket(bucket_key,scope,window_started_at,window_ends_at,request_count,revision)
VALUES (?, ?, ?, ?, 0, 0) ON DUPLICATE KEY UPDATE bucket_key=bucket_key;
SELECT * FROM auth_rate_bucket WHERE bucket_key=? FOR UPDATE;
```

- [ ] 测试两个并发申请仅一个通过冷却；邮箱大小写唯一、active grant唯一、过期边界now=expiresAt、重启保留桶状态。MutableClock扩展Clock并提供advance(Duration)，测试不sleep等待限期。
- [ ] 全模块测试通过后只提交上述本任务文件，消息服务业务未创建不记完成。

## Task 2：消息服务邮件受理、持久化和恢复

**Files:**
- Modify: `backend/stock-notification-service/pom.xml`、`src/main/resources/application.yml`、`deploy/compose.yaml`、`.env.example`。
- Create: 本模块 `src/main/resources/db/migration/V1__email_delivery.sql`；`src/main/java/com/aistock/notification/email/{EmailCommand,DeliveryView,EmailDeliveryRepository,EmailDeliveryService,EmailWorker,MailTransport,SmtpMailTransport,EmailDeliveryController}.java`及README。
- Test: 本模块 `src/test/java/com/aistock/notification/email/{EmailDeliveryServiceTest,EmailWorkerTest,EmailDeliveryControllerTest}.java`和测试夹具。

**Interfaces:** MailTransport.send(EmailCommand): void；发送失败抛受控TransportFailure，由EmailWorker写稳定错误码。内部接口参照规格；EmailCommand在消息服务独立定义，不能跨服务共享持久Entity。worker短事务认领，事务外发送，按claim_version提交。依赖：Task1的设计约定，不依赖其数据库代码。

- [ ] 先编写稳定requestId测试，测试邮件transport是mock，禁止读取真实SMTP凭据。

```java
@Test void duplicateAcceptanceDoesNotQueueTwice() {
    EmailCommand cmd = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "test@example.com", "123456", clock.instant().plusSeconds(600));
    assertEquals(deliveries.accept(cmd).requestId(), deliveries.accept(cmd).requestId());
    assertEquals(1, jdbc.queryForObject("select count(*) from email_delivery", Integer.class));
}
```

- [ ] 运行 `mvn -pl stock-notification-service -am test '-Dtest=EmailDeliveryServiceTest,EmailWorkerTest,EmailDeliveryControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，先确认未实现导致失败。
- [ ] 生成独立迁移、服务鉴权及spring-boot-starter-mail适配器。配置SMTP_HOST/PORT/USERNAME/PASSWORD/FROM、SMTP_STARTTLS_REQUIRED、MAIL_PAYLOAD_KEY和MAIL_DIGEST_KEY；密钥本机生成，不打印；生产TLS必需，不关闭证书校验。
- [ ] 实现AES-GCM临时载荷、幂等受理、5/30/120秒有限重试、过期清理和旧claim_version拒绝。缺配置返回503，不静默切换测试transport；应用本地投递扫描间隔2秒、租期30秒，可配置。
- [ ] 验证同ID不同载荷409、服务凭据缺失401、SMTP超时、过期不重发、SMTP已接受后写库失败及旧worker无法覆盖；通过后小步提交。

## Task 3：邮箱挑战、派发与原子注册

**Files:**
- Modify: `backend/stock-user-service/pom.xml`（Feign/loadbalancer/validation）、`application.yml`及Compose用户配置。
- Create: 本模块 `registration/{RegisterCommand,ChallengeView,RegistrationResult,RegistrationService,RegistrationDispatcher,RegistrationController,RegistrationReceiptRepository,RegistrationDispatchRepository,MailDeliveryClient}.java`；README。
- Test: `registration/{RegistrationServiceTest,RegistrationConcurrencyTest,RegistrationDispatcherTest,RegistrationControllerTest}.java`。

**Interfaces:** 提供request/register/runOnce；MailDeliveryClient消费Task2两内部接口，同请求ID重试；本模块本地EmailCommand/DeliveryView DTO定义与消息接口字段一致。UTC Clock注入。注册失败以结果对象返回事务外映射异常，保留failed_attempts。

- [ ] 使用MySQL夹具写注册成功/错误计数测试，给邮件mock捕获验证码，不在日志打印；仅测试能访问捕获内容。

```java
@Test void successfulCodeCreatesUserOnce() {
    ChallengeView challenge = registration.request("member@example.com", "127.0.0.1");
    dispatcher.runOnce(clock.instant());
    String code = capturedMail.code();
    RegisterCommand cmd = new RegisterCommand(UUID.randomUUID(), challenge.challengeId(), "member@example.com", code, "Long-test-password-1", "测试用户");
    RegistrationResult first = registration.register(cmd);
    assertEquals(first.userId(), registration.register(cmd).userId());
    assertEquals(1, jdbc.queryForObject("select count(*) from user_account", Integer.class));
}
```

- [ ] 运行 `mvn -pl stock-user-service -am test '-Dtest=RegistrationServiceTest,RegistrationConcurrencyTest,RegistrationDispatcherTest,RegistrationControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，确认RED。
- [ ] 实现申请限频、独立挑战、加密派发、回执，按数据库设计锁顺序处理消费。public_request_digest只用challengeId/email/nickname；重试不重新设密码。
- [ ] 已注册邮箱申请返回同形受理/挑战响应并发送不含账号信息的受控验证邮件，注册阶段统一拒绝再创建；公开状态不显示“已注册”，避免通过状态查询绕过统一反馈。
- [ ] 验证五次错码提交已持久，第六次正确仍拒绝；重发使旧码无效；两个并发注册同邮箱最多一个成功；不同id消费同码拒绝；同id改字段409；派发受理后崩溃重试使用原requestId；无SMTP配置不能通过真实验收。通过后小步提交。

## Task 4：登录会话、CSRF和网关转发

**Files:**
- Modify: `backend/stock-user-service/pom.xml`（Spring Security）、`backend/stock-gateway/pom.xml`、gateway配置；`frontend/nginx.conf`保留内部接口404。
- Create: 用户模块 `session/{IssuedSession,Identity,SessionService,SessionRepository,AuthController,SessionResolveController}.java`；`security/{SecurityConfiguration,SessionAuthenticationFilter,OriginPolicy,CookieWriter,SecurityErrorWriter}.java`；README。
- Create: 网关 `src/main/java/com/aistock/gateway/UserApiProxyController.java`、`ProxyHeaderPolicy.java`及对应测试。
- Test: 用户模块 `session/{SessionServiceTest,LoginDisableRaceTest,AuthControllerTest}.java`、`security/SecurityBoundaryTest.java`；gateway `UserApiProxyControllerTest.java`。

**Interfaces:** resolve返回Identity或AUTH_REQUIRED；UserApiProxy仅转发/api/v1/auth/**、精确/api/v1/users/me及其password、/api/v1/admin/users受控路径。使用Feign/Nacos与受控HTTP响应保留多条Set-Cookie，剔除浏览器内部身份头及hop-by-hop头；代理不记录Cookie/请求体。

- [ ] 编写Cookie登录/撤销及伪造身份测试。以下login后的cookie来自服务响应，不由客户端随意指定userId。

```java
@Test void oldSessionCannotSurviveLogout() {
    IssuedSession issued = sessions.login("member@example.com", "Long-test-password-1", "127.0.0.1");
    assertNotNull(sessions.resolve(issued.rawToken()).userId());
    sessions.logout(issued.rawToken());
    assertThrows(BusinessException.class, () -> sessions.resolve(issued.rawToken()));
}
```

- [ ] 运行 `mvn -pl stock-user-service,stock-gateway -am test`，确认新功能测试RED。
- [ ] 实现256位随机会话、摘要、8小时期限；登录成功建会话前锁用户并重新核对凭据版本。相同错误反馈，并对不存在账号执行等价密码哈希比对以减少明显时序差异。
- [ ] 使用现有BOM对应的SecurityFilterChain和CookieCsrfTokenRepository配置；自定义会话认证，安全上下文不复制成默认HttpSession身份。匿名CSRF也要求来源校验；登录/退出后重新签发令牌；filter错误使用统一ApiResponse，不泄露Spring原始错误页。
- [ ] 验证缺CSRF/跨站写403、内部接口缺服务凭据401、过期/禁用会话401、登录与禁用同步屏障竞态、多Set-Cookie经网关不丢、请求头不能冒充管理员、网关停机/用户服务不可用受控503。测试当前组件版本而非最新文档语法；通过后提交。

## Task 5：个人资料、管理员启停及实验授权

**Files:**
- Create: 用户模块 `account/{ProfileView,ProfileService,ProfileController}.java`；`admin/{AdminUserService,AdminUserController,AdminBootstrapCommand,AuditRepository}.java`；README。
- Test: `account/ProfileServiceTest.java`、`admin/{AdminUserServiceTest,AdminBootstrapConcurrencyTest,AdminUserControllerTest}.java`。
- Create: `scripts/init-admin.ps1`（隐藏输入转进程临时环境，finally清除，不把密码放命令行或Git）。

**Interfaces:** 沿上文ProfileService/AdminUserService签名；初始化通过单次独立命令模式调用事务服务，完成后退出，不自动启动HTTP服务，不在启动日志打印参数。

- [ ] 编写禁止自禁用和版本覆盖测试；fixture中管理员与普通账号通过测试数据构造，不在正式迁移植入密码。

```java
@Test void staleRevisionCannotOverwriteNickname() {
    ProfileView current = profiles.rename(memberId, "新昵称", 0);
    assertEquals(1, current.revision());
    assertThrows(BusinessException.class, () -> profiles.rename(memberId, "旧页面昵称", 0));
}
```

- [ ] 运行 `mvn -pl stock-user-service -am test '-Dtest=ProfileServiceTest,AdminUserServiceTest,AdminBootstrapConcurrencyTest,AdminUserControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'`，确认RED。
- [ ] 实现本人资料、旧密码校验、密码修改全会话撤销；管理员每次核验当前角色，锁目标用户且写审计。保护ADMIN账号，普通用户无角色更新接口。
- [ ] 实现bootstrap_guard锁和无管理员前置条件，两进程同时初始化仅一成功；幂等授权不增revision，撤销历史保留。初始登录来源地址总体15分钟100次失败，可配置，与邮箱组合10次分开计数。
- [ ] 测试越权、非法角色参数、昵称长度/控制字符、多字节密码72字节上限、禁用后旧会话、启用后不恢复旧会话、授权重复与审计。通过后提交。

## Task 6：前端四页面及浏览器链路

**Files:**
- Modify: `frontend/package.json`、`pnpm-lock.yaml`、`src/router/index.ts`。
- Create: `src/api/{auth,userAdmin}.ts`；`src/stores/auth.ts`；`src/router/guards.ts`；`src/pages/auth/{LoginPage,RegisterPage}.vue`；`src/pages/profile/ProfilePage.vue`；`src/pages/admin/UsersPage.vue`；各目录README。
- Test: `src/api/auth.test.ts`、`src/stores/auth.test.ts`、`src/pages/auth/RegisterPage.test.ts`、`src/pages/admin/UsersPage.test.ts`；Vitest配置。

**Interfaces:** auth API提供requestChallenge(email)、challengeStatus(id)、register(form)、login(email,password)、logout()、refreshCsrf()、me()；cookie由浏览器携带，store只保留安全摘要。write请求通过X-XSRF-TOKEN；认证变化后先刷新再写。页面守卫只改善导航，不能代替后端权限。

- [ ] 先写表单及刷新令牌顺序测试，测试API用mock，不把测试验证码写进页面产品逻辑。

```ts
it('registers only after submitting the entered verification code', async () => {
  const form = { email: 'member@example.com', code: '', password: 'Long-test-password-1' }
  expect(validateRegisterForm(form).code).toBeDefined()
  expect(validateRegisterForm({ ...form, code: '123456' }).code).toBeUndefined()
})
```

validateRegisterForm在`src/pages/auth/registerForm.ts`定义，返回字段错误映射，服务端仍重新验证；同文件纳入此任务。密码首次输入与确认仅在前端比对，确认字段不发送到API。

- [ ] 运行 `pnpm exec vitest run`先确认RED，再实现页面。验证码倒计时依据服务器resendAfter，刷新页面不自动发码；只轮询已申请challenge状态，最长到expiresAt，离开页面销毁timer。
- [ ] 注册成功跳登录；401清除身份转登录；503显示可重试错误，不假显示注册成功；个人中心邮件/头像后续功能不显示可用按钮。管理列表分页及启停/授权时处理409并重新取当前资料。
- [ ] 测试非ADMIN不展示管理入口，store不存token，重复点击提交复用registrationRequestId；修正输入后新申请/请求使用新ID。`pnpm typecheck`、`pnpm exec vitest run`、`pnpm build`通过。
- [ ] 实际浏览器验证登录→修改昵称→退出→再登录→管理启停；真实邮箱注册仍单独验收。通过后提交，不顺手增加股票业务。

## Task 7：部署、故障回归与CodeArts证据

**Files:**
- Modify: `scripts/init-local-env.ps1`（已有.env补缺失密钥但不覆盖旧密码）、`scripts/start-local.ps1`、`deploy/README.md`、`docs/implementation-status.md`。
- Create: `tests/check-user-business.ps1`、`.github/workflows/user-business-ci.yml`、`docs/verification-sdd002.md`；`docs/codearts/sdd002-prompts.md`及README。

**Interfaces:** check-user-business使用专用测试环境与隐藏输入，不输出口令/验证码；现用Compose检查仍运行check-compose.ps1，不能把诊断DEMO样本当正式业务。CI自动运行Maven、前端与MySQL隔离测试，SMTP使用测试适配器，无生产凭据；真实送达是人工观察的独立证据。

- [ ] 先编写故障验收断言，禁止状态503含成功账号资料。

```powershell
$response = Invoke-WebRequest "$baseUrl/api/v1/auth/registration-challenges" -Method Post -ContentType 'application/json' -Body $testEmailJson -Headers $csrfHeaders -WebSession $browserSession -SkipHttpErrorCheck
if ($response.StatusCode -eq 503 -and ($response.Content | ConvertFrom-Json).data) { throw 'Failure contains success data' }
```

脚本baseUrl/testEmailJson/csrfHeaders/browserSession在入口由专用测试配置与GET csrf建立，不接受现用用户库清空命令；读取秘密使用Read-Host -AsSecureString或安全输入。

- [ ] 运行隔离部署验证消息服务停机、恢复、派发超时与重启；用户服务停机时网关503；数据库重启后账号和计数保留；再次运行SDD-001检查保证旧联调未被破坏。
- [ ] 配置真实发件邮箱后，由用户输入收到的验证码跑完整注册，不读取个人邮箱收件内容或代发其他消息；SMTP配置未提供时明确记录UA-01未验收，不能打全部完成勾。
- [ ] CodeArts每批提示包含主规格、数据库设计和当前任务文件，仅生成该步；保存工具实际输出、源码差异、人工审核与测试。客户端启动不算源码生成，现有Codex代码不写成CodeArts产物。若CodeArts不可执行，保留待验收状态并准确汇报。
- [ ] `mvn verify`、`pnpm typecheck`、`pnpm exec vitest run`、`pnpm build`、Compose检查通过后做全批独立审核，修复具体问题再复测。保存实际命令/版本/测试统计，文档小步提交；没有Git远程不自动编造push成功。

## 覆盖自查和执行交接

UA-01→Task2/3/6/7；UA-02→Task1/3/6；UA-03→Task2/3/7；UA-04→Task1/3；UA-05→Task4/5；UA-06→Task4/5；UA-07→Task1/5；UA-08→Task1/4/6/7；UA-09→Task1/2/3/7。所有要求都有实现及验收归属，头像/换邮箱/通知偏好明确由后续规格承接。

SMTP及账号链路已验收。CodeArts实际源码证据、全量ER图件渲染不因本阶段账号验收而标为完成。用户授权CodeArts无法执行时由Codex实施，源码来源见docs/codearts；未伪称工具生成。原始步骤中的浏览器管理员操作、昵称修改和数据库实际重启不扩展为已人工验收，相关事务和权限由隔离测试验证。

推荐顺序是本会话逐项实施，任务之间共享接口，避免并行修改认证规则。实施前审核本计划及数据库设计，再进入Task1。执行进度仅按实际完成勾选；本计划的原始步骤保留供追溯，完成状态以执行台账和verification-sdd002.md为准。Task1—7实现和本机账号验收已完成；自动测试81项后端、10项前端通过，真实邮箱注册及登录成功。生产管理员尚未初始化；远程CI和CodeArts真实生成未验收。
