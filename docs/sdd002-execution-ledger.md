# SDD ledger — plan: docs/superpowers/plans/2026-09-30-sdd002-user-account-plan.md

2026-09-30，基线19083db，用户已审核规格及实施计划。按现用正式工程目录在本会话逐项实施，保留运行数据库，不创建新的并行主需求。

Task1开始：用户数据库、安全工具与MySQL隔离测试。其他任务未开始。

CodeArts：计划先提交Task1生成请求，只有真实源码差异和测试通过才记生成成功。

Ruling: MySQL隔离测试先使用已验证Docker CLI启动独立临时MySQL容器，由JUnit JDBC夹具访问；不与现用Compose共享数据库或卷。避免为测试再引入一套容器客户端，保留真实MySQL并发/约束验证；代价是需要显式测试启动脚本。

用户确认CodeArts已登录，已重新提交Task1（附两份RED测试）；注册测试收件邮箱只存忽略的runtime，真实SMTP尚未配置。独立MySQL测试容器已启动，无现用业务卷。

Task1 RED：新增邮箱、安全摘要、迁移存在性及MySQL限频测试已运行，当前因EmailAddress/SecretDigests/RateLimitService尚未生成而编译失败，符合测试先行阶段。隔离MySQL8.0.46已验证stock_user_test且使用tmpfs；现用业务库未执行迁移。CodeArts重新提交后仍未见源码差异，等待客户端真实执行；Task1及全部业务均不标完成。

Ruling: 用户明确授权CodeArts无法执行时由Codex编写。本机检查显示客户端及账号信息获取正常，但任务文本和写文件证据缺失，仍无Task1源码；现改由Codex编写测试与实现，不计作CodeArts生成验收。

Task1继续：UserRepository、ChallengeRepository、PasswordHasher测试先运行，缺少对应实现导致RED；补实现后GREEN。事务锁测试先运行时缺lockById，编译产物报Unresolved compilation problems；增加锁方法并要求活跃事务，改为clean构建消除旧产物。

Task1诊断：MySQL CHECK违反返回3819/HY000，Spring默认译为UncategorizedSQLException，数据库拒绝行为符合要求；测试改为验证实际SQL错误码，不放宽数据库约束。SQL文本存在性测试替换为真实的未验证邮箱、非法角色/状态约束验证。

Ruling: 普通Maven构建排除mysql标签，auth-integration配置启用全部测试；专用脚本注入固定隔离数据库地址、运行整个后端clean verify。保持普通构建可移植，并明确普通构建不代表数据库验收；代价是CI须显式启动独立MySQL并执行专用配置。

Task1中间验证：整个后端auth-integration clean verify成功，原有服务测试未失败；不注入数据库凭据的标准clean verify亦成功。之后增加来源限额回滚、限频窗口边界、登录失败双重限频测试，最终结果另记。

Task1审查：已请求独立只读审查当前基础组件，范围不包含后续邮件、HTTP注册、登录与前端。没有执行现用业务数据库迁移或重建Compose。

Task1 Final: fixed 昵称2—30码点及去空格后校验 — nicknameLimitsApplyAfterTrimmingAndSupportUnicodeCharacters RED→GREEN，插入和修改边界均检查。

Task1 Final: fixed 非法邮箱绕过来源额度 — malformedRegistrationCountsSourceEvenWhenCallerRollsBack / malformedLoginFailuresCountSourceWithoutEmailBucket RED→GREEN。格式校验失败的来源计数使用独立事务，返回错误不回滚限频。

Task1 Final: UTC疑虑已检查 — 原始DATETIME值与Instant截止时间分别在Asia/Shanghai、UTC JVM测试通过。

Task1 Final: minor (deferred): RateBucketRepository.lock尚无直接事务断言，当前唯一调用方已在事务内使用；新增调用路径前补保护。

Task1验证完成：scripts/check-auth-task1.ps1 → 全后端mvn -B -Pauth-integration clean verify，39/39通过，无失败、错误或跳过；标准构建亦通过。SMTP与注册页面未开始，不记完整用户业务完成。

Task1 complete：325775e，独立审查与最终39项测试通过。开发分支codex/sdd002-user-account，不改动其他项目。

Task2开始：邮件受理/Worker/内部鉴权先写测试，运行mail-task2-red.log，因所需组件未实现编译失败。实现消息服务独立迁移、AES-GCM临时载荷、HMAC幂等请求、SKIP LOCKED认领与租约版本、SMTP适配器，运行全后端clean verify通过。

Task2补充测试：并发Worker不重复认领、SMTP接受后写库前崩溃的租约恢复、旧Worker拒绝覆盖、三次重试后停止、到期停止、载荷篡改及绑定错误拒绝。测试发送器仅在测试源码内，不用于实际运行。

Task2运行配置：QQ邮箱465端口SSL并验证服务器身份，SMTP授权码通过本机隐藏输入存忽略.env。首次启动工具依赖普通窗口无pwsh，改为Windows PowerShell5.1并保留UTF8 BOM，语法检查通过。用户配置尚待确认，SMTP_NOT_CONFIGURED检查不发送任何邮件。

Ruling: 邮件数据库沿用独立MySQL容器新建stock_notification_test，消息服务测试只清理该独立库，默认串行；不清理业务库。测试配置显式注入两个地址，业务库不跨服务读取。

Task2 complete：全后端48/48测试通过。用户通过兼容Windows的隐藏输入工具保存授权码后，check-smtp.py返回SMTP_TLS_AUTH_OK，实际未发送邮件。

Task3 RED→GREEN：注册成功/回执重试/错码计数/重发与并发建号测试先运行，组件缺失导致RED；实现用户服务事务与加密派发后通过。补充跨服务受理超时后同requestId恢复、已有账号不可消费挑战、公开接口不含邮箱和验证码、状态查询限频。

Task3边界修复：expiryIsCheckedAfterWaitingForTheRegistrationLock先复现处理时刻到期仍成功，改为锁取得后读取当前时刻，RED→GREEN。公开ACCEPTED只说明邮件服务受理；发送失败状态通过内部API查询，连接故障返回受控503。

Ruling: 用户数据库夹具也在每个测试开始时清理严格绑定的stock_user_test，防止全局派发Worker消费其他用例的残留任务；默认串行测试、测试内并发另用线程验证。业务库未删除数据。

Task3验证：scripts/check-auth-task1.ps1 → 全后端auth-integration clean verify，56测试、0失败、0错误、0跳过。HTTP认证与页面尚未接入，不记完整注册验收。

Task4验证：全后端 auth-integration,cli-build clean verify 成功；67测试无失败、错误、跳过。登录/退出真实Cookie、CSRF刷新、会话撤销、并发禁用、身份头伪造、来源校验、网关多Cookie和实际HTTP PATCH均通过。
Ruling: 增加cli-build输出隔离目录，避免Java IDE覆盖Maven编译产物；普通构建目录保持兼容。网关明确采用Apache HC5以支持PATCH；使用真实HTTP服务验证。非法JSON/参数由统一异常处理返回400而非500。
Ruling: Spring Security可额外清除CSRF Cookie，退出测试以各同名Cookie的最终过期状态为准，不要求固定响应头数量；网关保留所有Set-Cookie。
Task4 complete：后端会话安全与代理实现已验证；尚未实际部署、浏览器账号业务和真实邮件验收。

Task5 RED→GREEN：个人资料版本冲突、密码修改全会话撤销、管理员当前权限、普通用户保护、启停与授权审计、幂等授权、并发初始化、非Web初始化模式、HTTP越权及非法参数测试均已执行。角色注入测试发现MVC测试环境默认忽略未知字段，统一配置实际MVC转换器使用严格JSON对象映射后通过。
Task5 complete：scripts/check-auth-task1.ps1 全后端76/76测试通过，无失败、错误、跳过（runtime/task5-verified.log）。初始化脚本隐藏读取密码，经进程环境传入容器，不带凭据命令行；独立配置不扫描HTTP和定时任务并强制关闭Nacos发现。尚未在现用业务库初始化管理员。

Task6 RED→GREEN：CSRF写请求前获取及登录退出后刷新、获取失败不写、Pinia身份及退出清理、服务503不冒充匿名成功、注册验证码与密码校验、管理员操作可见性，共6项测试通过；pnpm build包含严格TypeScript检查通过。已创建登录、注册、个人中心、用户管理四页面和路由守卫，仍待实际浏览器和真实邮件链路验收。Element Plus主包约1MB，构建提示体积警告，未隐藏；页面已按路由拆分，后续可优化组件按需加载。
Ruling: 页面沿现用蓝白工程页面，采用克制的卡片、明确标签与响应式布局；未套用技能检索结果的营销页结构。
Task6 implementation complete：前端实现与6项测试、类型构建通过；浏览器验收归Task7。

Task7整体只读审核：1名独立Reviewer，Critical无，Important5项、Minor1项。已修复公开邮件状态枚举（已有邮箱也真实加密派发，挑战仍不可消费）、可信客户端来源、注册提交限额及哈希前预检、邮件受理后终态轮询、受保护401清身份与导航，以及409刷新管理列表。各缺陷先复现再修复；后端80/80、前端10/10通过，类型与生产构建通过。ClientSources严格限定数字IP、受信代理与内部服务凭据，不直接相信浏览器来源头。
Task7部署发现与修复：HC5不能覆盖Client Bean而绕开负载均衡，改用Apache HTTP实例交由Spring Cloud封装；Spring CORS非JSON403保留403并规范化错误；浏览器旧CSRF在拒绝后获取当前令牌并同请求重试一次，仍保留后端校验。网关停机时Nginx502改为受控503 JSON，服务测试始终finally恢复。
Ruling: SENT仅表示SMTP接受，不能表述为用户已收件；用户确认已收到邮件，但首次注册提交仍报403、库中账号0。修复已上线，等待实际重试成功落库，不标UA-01全部完成。受控初始化尚未在现用库执行，不自动创建管理员。
Task7当前：真实部署与用户服务故障/恢复、原有五服务及五库隔离通过。CI仅配置未远程执行，Git无远程不推送；CodeArts按用户批准使用Codex替代，准确记录来源。Task7完整人工注册验收仍待用户。


Task7最终核验：真实用户注册登录暴露HC5默认共享Cookie缓存，匿名请求错误携带既有会话。停止网关后，真实HTTP隔离回归复现RED；disableCookieManagement修复后GREEN并恢复部署。独立Reviewer确认该Bean实际进入Feign负载均衡委托，原5项Important、1项Minor及新增Cookie Critical均已解决。
Task7最终验证：全后端clean verify 81项测试，0失败、0错误、0跳过；前端5文件10项测试通过，严格类型和生产构建通过。用户服务故障/恢复、网关受控JSON故障/恢复、原有五服务注册及库隔离均通过。用户自行输入验证码与密码，完成真实邮箱注册和登录；服务器确认已验证普通账号1个、有效会话1个，浏览器个人中心验证本人资料。UA-01真实链路验收完成。
Task7实现与本机验收完成：初始管理员工具已实现和测试，现用库未初始化，需用户自行隐藏输入管理员密码；远程CI未执行、Git无远程、CodeArts没有生成证据，继续准确保留这些边界。股票业务不在本阶段完成范围。
