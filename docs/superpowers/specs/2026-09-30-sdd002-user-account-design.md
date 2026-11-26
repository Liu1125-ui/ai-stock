# SDD-002 邮箱注册、登录会话与用户管理规格

日期：2026-09-30｜状态：规格整理完成，尚未编码。

配套：[数据库详细设计](2026-09-30-sdd002-database-design.md)、[七步实施计划](../plans/2026-09-30-sdd002-user-account-plan.md)。
题目：基于Transformer的股票时序分析与辅助决策系统设计与实现。
依据：FR-01、FR-10，AC-01、AC-10、AC-17、AC-20及本次用户确认。13项业务、五服务、两角色不变。

## 1 已确认要求与本批范围

用户必须填写邮箱，验证成功后才能创建账号；登录使用邮箱与密码，昵称是展示名称。注册只能创建普通用户，管理员通过受控部署初始化创建，不开放管理员自行注册。

本批实现注册、登录、退出、查看本人资料、修改昵称和密码；管理员查询用户、启用和禁用普通账号、授予和撤销实验权限。实验授权不增加角色，本批只验收授权状态，不表示训练业务完成。

本批页面为注册、登录、基本个人中心和管理员用户管理。头像上传、更换邮箱、通知偏好、忘记密码找回保留到后续规格，不从总需求删除。个人中心先展示已验证邮箱，不提供未完成的更换入口。管理员角色修改、管理员相互禁用及替他人改密码不属于本批。

## 2 方案选择与服务责任

采用服务端可撤销的登录会话。相比仅校验自包含Token，它便于立即撤销退出、禁用和改密码后的旧凭据；引入完整外部身份平台增加部署成本，因此本批不接入外部身份平台。

MS-01拥有用户、注册挑战、注册回执、登录会话、实验授权和管理审计。MS-05拥有邮件发送、重试与状态，提供经服务鉴权的内部接口。用户服务决定验证码是否有效和是否创建账号；消息服务不能决定邮箱已验证。服务间通过Feign/Nacos调用，不跨库读取。

网关只按白名单转发公开接口，清除外部伪造的内部身份头；用户服务仍独立核验会话与权限，不信任浏览器提交的userId或role。后续股票业务通过内部会话解析核验身份，用户服务故障时受保护操作拒绝执行。

真实SMTP是注册可用性的依赖，发件账号与凭据已通过本机隐藏输入配置，并通过TLS认证检查。测试邮件适配器仅用于隔离测试，不代表实际邮件送达；没有真实发送配置时明确不可用，不绕过验证创建账号。凭据不写入规格或Git；涉及付费另行确认。

实施审核补充：已注册邮箱也使用相同的加密派发与邮件状态路径，区别仅为挑战不能消费，不创建第二用户。PENDING与ACCEPTED不代表邮件到达收件箱，SENT只代表SMTP接受；页面继续查询到SENT、FAILED或EXPIRED。注册提交初始每来源30次/分钟，先做挑战与回执预检再执行密码哈希，最终事务内仍重新核验。可信前端代理覆盖X-Real-IP，网关仅相信该代理连接并以内部服务凭据向用户服务传递来源；外部伪造来源头无效。受保护接口AUTH_REQUIRED清除前端身份并回登录页，登录失败保留错误提示。

## 3 邮箱注册流程

1. 申请验证码时校验邮箱。本系统约定邮箱首尾去空格、大小写不敏感，保存email与唯一email_normalized；不合并加号别名或点号别名。注册表单明确此约定。
2. 创建RegistrationChallenge：未注册用户没有user_id，因此挑战不能依赖用户非空外键。此时不保存密码；仅保存绑定邮箱、用途、有效期和验证码摘要的待验证记录，不算正式账号。
3. 验证码使用密码学随机数；短验证码用服务端密钥HMAC保护，不能仅保存普通哈希。邮件以稳定requestId去重，消息服务持久化发送状态；对外202只表示受理，页面区分受理、发送失败和可重试，不能声称收件人已收到。
4. 提交challengeId、邮箱、验证码、密码、昵称及registrationRequestId。服务端锁定挑战，检查绑定邮箱、期限、尝试次数和消费状态；验证后在同一用户服务事务内创建USER账号、标记邮箱已验证、消费挑战、失效同邮箱其他挑战并保存注册回执。
5. 错码次数必须持久化，不能因抛出业务异常回滚计数。并发注册依靠锁与唯一邮箱约束确保一个账号；同一合法请求重试返回同一回执，相同请求标识但不匹配公开字段或挑战返回409，不修改已创建密码。
6. 注册成功进入登录页，不自动登录。验证码一次消费、绑定REGISTER用途；换邮箱和找回密码以后使用不同用途，不能借注册验证码重置账号。
7. 新验证码申请受理后使旧挑战失效；发送失败明确提示，重试遵守冷却与次数限制。用户服务持久化待派发记录，跨服务超时可用同一requestId重试，不能丢失派发意图或重复建挑战。登记请求和网络发送不构成跨服务数据库事务。

## 4 登录、个人资料及管理规则

密码通过Spring Security密码组件存储，首批BCrypt强度12，之后按实测调整。默认密码12—64字符且UTF-8最多72字节，允许空格，不自动裁剪；验证码、密码、原始会话凭据不进入日志。

登录失败统一说明，不区分邮箱不存在、密码错误或禁用。成功生成至少256位随机会话凭据，数据库仅保存摘要。会话固定8小时过期，无刷新令牌；允许多个会话。Cookie使用HttpOnly、SameSite=Lax，正式HTTPS环境必须Secure，本机HTTP配置可关闭Secure。凭据不放URL或localStorage。

浏览器写接口全部校验CSRF令牌，登录也校验来源。CORS只允许配置的前端来源；信任代理名单控制来源地址，不能直接信任客户端X-Forwarded-For。认证Cookie不可由脚本读；CSRF令牌可由前端读并通过请求头提交，两者不能混为一谈。登录与退出后前端重新获取CSRF令牌。

退出撤销当前会话并清Cookie，重复退出仍成功但需有效的CSRF保护。修改密码验证旧密码后递增credential_version、撤销全部会话并要求重新登录。禁用账号撤销全部会话；重新启用不恢复旧会话。受保护请求核验账号当前状态和会话版本，不只检查凭据未过期。

本人接口从会话推导ID，不接收资料所有者参数。管理接口每次核验ADMIN；普通用户403。启停和实验授权使用revision防止旧页面覆盖新状态，变更与成功审计同事务；拒绝事件另记安全日志。撤销实验权限后权限查询立即失效，后续新建任务及启动前再次核验。重复授予不创建多份有效授权，保留授权历史。

本批管理员只能管理普通账号，不能禁用自己或其他管理员。初始化使用本机受控命令读取隐藏密码，无管理员时才能执行；不写迁移默认密码，不开放HTTP初始化入口。部署初始化管理员属于管理员预置，不属于公开邮箱注册流程，必须记录来源。

## 5 数据对象与归属

MS-01 User：user_id、email、email_normalized唯一、email_verified_at、email_version、password_hash、role_code、status、nickname、avatar_uri可空、credential_version、revision、created_at、last_active_at。移除username登录依赖。

MS-01 RegistrationChallenge：challenge_id、email_normalized、code_digest、purpose、expires_at、failed_attempts、consumed_at、invalidated_at、created_at。注册前无user_id；成功后由回执建立关联。

MS-01 RegistrationDispatch：request_id唯一、challenge_id、派发状态、attempt_count、next_attempt_at。保存最小加密发送载荷用于崩溃恢复，使用环境密钥，成功派发或过期后清理；这是用户服务的派发意图，不替代消息服务发送状态。

MS-01 RegistrationReceipt：registration_request_id唯一、challenge_id、user_id、公开请求匹配摘要、created_at。不保存密码或验证码及其普通哈希。回执ID只由随机请求标识定位，不暴露邮箱资料。

MS-01 LoginSession：session_id、user_id本库外键、token_hash唯一、credential_version、expires_at、revoked_at、created_at。

MS-01 ExperimentGrant：grant_id、user_id、granted_by、state、granted_at、revoked_at；每用户最多一个当前有效授权。

MS-01 UserAudit：audit_id、operator_id、target_id、action、result、trace_id、occurred_at；不保存敏感请求体。

MS-05 EmailDelivery：delivery_id、request_id唯一、challenge_id仅REF、recipient_email、template_code、state、attempt_count、created_at、last_attempt_at。短期加密载荷支持有限重试，完成或过期后清理，密钥不存库。SMTP不保证恰好一次送达，极端重试可能收到重复邮件，但同一验证码不能创建多个账号。

各服务单独迁移历史和数据库账号；公共模块不含用户实体或跨库Repository。索引、事务锁顺序、字段长度及清理任务在实现计划细化，不改变本规格的不变量。

## 6 公开和内部API

统一响应 `{code,message,data,traceId}`；状态码与业务码一致，不泄露堆栈及SMTP原始错误。下面是待实现接口，不是现有可调用接口。

- POST /api/v1/auth/registration-challenges：email；202返回不可猜测challengeId、expiresAt、resendAfter、受理状态。公开申请不通过响应直接确认邮箱是否已有账号；已注册邮箱不建立第二用户。
- GET /api/v1/auth/registration-challenges/{challengeId}：返回最小发送状态，无邮箱和验证码；限频并使用随机挑战ID。
- POST /api/v1/auth/register：challengeId、email、code、password、nickname、registrationRequestId；首次201，合法重试200，只返回成功回执和userId。
- POST /api/v1/auth/login：email、password；200设置Cookie并返回安全摘要；失败401，限频429。
- GET /api/v1/auth/csrf：返回与本站浏览器状态绑定的CSRF令牌；由安全组件校验，匿名注册同样有保护。
- POST /api/v1/auth/logout：撤销当前会话、清Cookie，幂等200。
- GET /api/v1/users/me：本人ID、邮箱、验证状态、昵称、角色、实验权限及revision；不返回任何凭据摘要。
- PATCH /api/v1/users/me：nickname、revision；返回新revision，版本冲突409。
- PUT /api/v1/users/me/password：currentPassword、newPassword；成功撤销全部会话。
- GET /api/v1/admin/users：邮箱或昵称筛选、status、分页；上限初始100，核验ADMIN，不返回密码。
- PATCH /api/v1/admin/users/{userId}/status：ENABLED或DISABLED、revision；变更与审计同事务。
- PUT /api/v1/admin/users/{userId}/experiment-grant：enabled、revision；返回新revision，重复授权保持幂等。
- POST /internal/v1/sessions/resolve：MS-01服务鉴权接口，只接受会话凭据用于核验，返回当前身份权限；不能用userId代替凭据，不能输出凭据日志。
- POST /internal/v1/email-deliveries、GET /internal/v1/email-deliveries/{requestId}：MS-05内部受理及状态查询，鉴权、去重，不经网关公开。

错误包括VALIDATION_FAILED(400)、CHALLENGE_INVALID(400)、AUTH_REQUIRED/LOGIN_FAILED(401)、ACCESS_DENIED(403)、RESOURCE_NOT_FOUND(404)、REVISION_CONFLICT/REQUEST_CONFLICT(409)、RATE_LIMITED(429)、EMAIL_SERVICE_UNAVAILABLE/USER_SERVICE_UNAVAILABLE(503)。错码、过期、消费状态对外统一说明，内部区分原因。

## 7 可配置初始策略

验证码6位、有效期10分钟、同邮箱间隔60秒及每小时5次、每挑战最多5次错误、来源地址每小时30次申请。登录同邮箱及来源地址组合15分钟10次失败，来源地址总体限制另在实现配置明确。状态和计数持久化，以数据库原子更新保护，不能只用单进程内存。

这些值是可调整默认，不是永久限制。唯一邮箱、验证码消费一次、账号禁用、对象归属和会话撤销不能通过配置放宽。测试采用可控时钟验证过期，不靠真实等待10分钟。

## 8 验收标准

UA-01：真实邮箱收到验证邮件，正确码只创建一个已验证普通用户，邮箱密码可登录。

UA-02：无邮箱、非法格式、弱密码、错码、过期码、已消费码不能创建；重发旧码失效；错误计数与发送限制生效。

UA-03：SMTP未配置、消息服务停机或发送失败明确反馈，重试可恢复且无重复发送记录；测试适配器通过不等于真实送达。

UA-04：并发注册、重复验证码及相同请求重试只有一个用户；请求标识冲突409；数据库唯一约束生效。

UA-05：登录错误不泄露账号存在；退出、过期、改密码、禁用后旧会话拒绝，重新启用需要重新登录。

UA-06：普通用户不能管理账号或修改他人资料；伪造角色、用户ID及内部头无效；内部接口缺凭据拒绝。

UA-07：管理员启停及实验授权有审计，重复授予不产生多个有效权限，旧revision冲突；管理员不能禁用自己或其他管理员。

UA-08：跨站写请求与无CSRF令牌拒绝，登录退出后新令牌有效；Cookie符合环境要求；库和日志无明文密码、验证码、会话凭据。

UA-09：重启后账号、会话撤销与计数保留；跨服务超时重试不丢派发、不重复创建；跨库访问拒绝。

覆盖业务单元测试、MySQL并发事务测试、邮件契约与故障测试、网关授权测试、前端表单与实际浏览器注册登录、Compose重启回归。覆盖率和性能结果按实测记录，不预写通过结论。

## 9 GitHub参考及适配边界

参考日期2026-09-30。GitHub CLI未登录，公共内容通过网页读取；没有克隆、安装或执行第三方代码。以下为工程参考，不是已经核实知网收录的论文。

- [Spring Security官方示例仓库](https://github.com/spring-projects/spring-security-samples)：作为后续安全组件实现入口。本轮未完整读取具体示例，不声称照此实现完成。
- [Spring Security官方CSRF文档](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)：借鉴Vue类单页应用令牌提交与登录退出后刷新令牌的要求。页面最新API可能超过现有Boot 3.2.5管理版本，不能直接复制最新版配置，编码前按实际依赖版本核对。
- [Keycloak VerifyEmail源码](https://github.com/keycloak/keycloak/blob/main/services/src/main/java/org/keycloak/authentication/requiredactions/VerifyEmail.java)：借鉴发送冷却、并发保护及刷新页面不自动重发。它依赖已有用户上下文，不能原样满足本项目“验证后才创建账号”；本项目另设注册挑战。没有引入Keycloak平台或宣称其直接支持本项目流程。

参考原则：学习成熟组件职责和边界，通过本项目测试证明适配正确；不复制整个系统改变五服务分工。如复制第三方源码，必须核实许可证并保留声明。

## 10 自查与开发入口

注册前无用户的问题已通过独立挑战解决；密码不存入挑战；消息归MS-05、验证归MS-01；身份两角色、实验权限独立；旧会话可撤销。头像、换邮箱、通知偏好明确后续承接，没有删减总需求。

尚待配置SMTP，不影响规格编写，但真实注册验收必须等待实际送达证据。旧ER图件未纳入注册挑战、派发与回执，编码前需更新图源并渲染，不能直接当作本批物理表设计。本文件是本批唯一规格，SDD目录索引指向此处。

后续先评审本规格，再写数据库、组件与测试的实施计划，依次完成邮件最小链路、注册、会话与网关、本人资料、管理员权限、前端联调。CodeArts生成和重构保留实际源码差异，Codex审核验证；尚无CodeArts证据时不标工具要求完成。
