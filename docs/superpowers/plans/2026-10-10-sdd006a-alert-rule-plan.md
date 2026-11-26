# SDD006A 预警规则与通知偏好 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. 本计划已获用户批准并以内联方式执行，2026-10-10完成A阶段本机验收；证据见docs/verification-sdd006a.md。

**Goal:** 用户可管理本人日线预警规则和邮件偏好，具有正确生效边界、权限隔离和并发保护；不把尚未实现的自动触发或邮件投递展示为已运行。

**Architecture:** MS-05独立拥有规则/偏好/幂等记录；通过受保护接口读取MS-01身份和MS-02边界。网关严格转发公开白名单；前端新增规则页面和个人中心偏好。A不启用预警调度或外发邮件；B负责证据评价与站内消息，C负责邮件投递。

**Tech Stack:** 现有Spring Boot、JdbcTemplate、MySQL/Flyway、Nacos/OpenFeign、Vue3/TypeScript/Pinia/Element Plus、JUnit/MySQL集成测试/Vitest；不引入新中间件。

**Spec:** docs/superpowers/specs/2026-10-10-sdd006-alert-notification-design.md V1.1。

## Global Constraints

- 保持13业务、五业务服务、ADMIN/USER；各业务数据独立，不跨库联表。
- 四种条件均严格比较，等于不触发。涨跌幅输入为正数百分比，后续评价固定复权口径。
- 生效边界为最新发布RAW交易日与当前应到期交易日的较晚者；只评价之后交易日。缺行情/日历不得猜测边界，停用不依赖行情接口。
- 邮件默认关闭，不外发验收邮件，不调用MaaS，不改预测或注册验证码行为。
- 新建目录配README，记录作用与实现方法。代码来源如实记录，凭据不提交。
- 阈值使用十进制字符串传输/BigDecimal及DECIMAL存储，禁止用浏览器浮点作为最终阈值权威。

## Review Focus

- 滞后行情、周末及20点前后：新规则不能收到创建前已应到期日的历史提醒（Task 1/2）。
- 请求超时重发与并发创建：同requestId同内容复用，同ID异内容拒绝，规则上限不能被并发突破（Task 2）。
- 伪造userId、直连服务、管理员读他人规则：均不能越权（Task 3）。
- 快速启停/修改、晚到页面响应：revision防丢更新，不能显示他人或已退出账号的缓存（Task 2/4）。
- 邮件偏好显示启用不等于投递能力上线：界面标明A阶段状态，只有后续切片验收才启用投递（Task 4/5）。

## 契约与可配置策略

规则类型：PRICE_ABOVE/PRICE_BELOW/DAILY_RISE/DAILY_FALL。Create输入requestId(UUID)、instrumentId、condition、threshold(十进制字符串)；所有者来自服务端身份，客户端不得指定。Patch输入revision及允许修改字段，instrumentId不可变；换股票新建规则。enabled=false不调用行情；条件变更或false→true需重新取得边界。请求未知字段、非有限/非正/超精度阈值拒绝。

响应包含id、instrumentId、condition、threshold、enabled、revision、effectiveAfterTradeDate、boundaryRawVersionId、createdAt、updatedAt、evaluationAvailability。A的evaluationAvailability固定NOT_ENABLED，页面解释自动检查待接入。

通知偏好GET/PATCH /api/v1/notification-preferences：emailEnabled、revision、emailVerified及deliveryAvailability。开启须读取当前本人验证邮箱状态；不把原始邮箱放进规则记录。渠道变更递增revision，即使先关闭后开启也产生新资格版本。SessionResolveController返回的Identity没有邮箱验证字段，邮件资格必须另外读取MS-01本人ProfileView；不得把会话存在直接等同邮箱有效。默认关闭，投递能力固定NOT_ENABLED。

规则GET/POST /api/v1/alerts，PATCH /api/v1/alerts/{id}。分页page>=1、size默认20最大50。每用户最多50条规则为配置默认值；价格最多6位小数，涨跌幅最多4位，百分比默认有效范围(0,100]，明确作为可调整版本策略而非证券市场涨幅上限。价格最高值与DECIMAL(20,6)一致；不得静默截断舍入输入。

错误类别：VALIDATION_FAILED(400)、AUTH_REQUIRED(401)、ACCESS_DENIED(403)、RESOURCE_NOT_FOUND(404，含他人对象)、ALERT_REVISION_CONFLICT/ALERT_REQUEST_CONFLICT/ALERT_LIMIT_REACHED(409)、ALERT_BOUNDARY_UNAVAILABLE/ACCOUNT_SERVICE_UNAVAILABLE(503)。最终命名与现有公共错误约定核对，禁止泄露原始异常。

边界接口：GET /internal/v1/market/alert-boundary/{instrumentId}，只对服务身份开放，返回instrumentId/provider/rawVersionId/latestPublishedTradeDate/expectedCompletedTradeDate/effectiveAfterTradeDate/calendarHash/asOf/definition。时钟来自MS-02，交易日历须覆盖截止日期，未上市、停牌、RAW不可用/日历不全不得伪造。边界与规则保存分属不同服务，采用固定asOf与版本记录，短调用预算并检查响应过期；不能宣称跨库原子。公开接口不能指定边界值。

## Task 1: 行情生效边界合同

Files Create:
- backend/stock-market-service/src/main/java/com/aistock/market/alert/AlertBoundaryService.java
- backend/stock-market-service/src/main/java/com/aistock/market/alert/AlertBoundaryController.java
- backend/stock-market-service/src/main/java/com/aistock/market/alert/README.md
- backend/stock-market-service/src/test/java/com/aistock/market/alert/AlertBoundaryTest.java及README.md
Review: backend/stock-market-service/src/main/java/com/aistock/market/ingestion/CandidateRequestFilter.java（已拦截/internal/v1/market/，测试确认新路径受保护；不误改只管公开行情的MarketSecurityConfiguration）。

- [x] 读现有MarketQueryService/MarketVersionRepository与内部安全策略；写时钟固定测试：20点前后、周末、滞后RAW、未知/缺日历、停牌/非交易日数据、匿名及错误服务令牌。
- [x] 运行定向JUnit观察RED：新合同不存在，确认不是环境错误。
- [x] 实现固定证据边界，不为规则创建调用外部行情；现有交易日历若仅覆盖RAW终点则返回受控不可用，不能将其默认当完整。
- [x] 运行定向测试GREEN；用现有真实三股版本只读核对周末边界，未达到日历覆盖要求则先补专用日历证据路径并补测，不绕过校验。
- [x] 本地提交单一边界合同变更。

Produces: 受保护、版本化边界响应。Consumes: 现有RAW和交易日历。
Completion: 固定日历/时钟边界测试通过，数据不足受控失败，原行情查询不回归。

## Task 2: 规则与偏好持久领域

Create under backend/stock-notification-service/src/main/java/com/aistock/notification/alert/: AlertRecords.java、AlertRuleRepository.java、AlertRuleService.java、NotificationPreferenceService.java、AlertBoundaryClient.java、README.md。
Create: backend/stock-notification-service/src/main/resources/db/migration/V2__alert_rules_preferences.sql。
Create tests under src/test/java/com/aistock/notification/alert/: AlertRuleMysqlTest.java、NotificationPreferenceMysqlTest.java及README.md。

- [x] 写MySQL测试RED：本人规则列表、请求幂等/内容冲突、revision冲突、非法字符串阈值、并发上限、启用边界、停用时行情故障、偏好关闭重开修订。
- [x] 新增alert_rule、alert_rule_request、notification_preference及用户级guard，按现有MySQL迁移风格；不创建B/C尚未使用的触发/邮件表。
- [x] HTTP发生事务外，持久提交前锁用户guard/规则行并再次校验revision；首次创建幂等内容摘要只包含用户输入，不包含随重试变化的边界。保存已完成同requestId响应以支持未知结果恢复。
- [x] 设置开关启用前核验权威当前邮箱；缺账号服务不默认为已验证。价格/涨跌幅分别校验精度范围，后续B共享比较规则。
- [x] 定向GREEN并纳入隔离MySQL完整回归；本地提交。

Produces: 规则和偏好服务、确定响应及幂等记录。Consumes: Task1边界、MS-01本人身份/当前资料。
Completion: 并发/故障/幂等用例通过，不改验证码表或发送器。

## Task 3: 公共API、安全与网关

Create notification/alert/: AlertController.java、NotificationPreferenceController.java、NotificationSecurityFilter.java、NotificationIdentityClient.java、NotificationClientConfiguration.java。
Create gateway/: NotificationPublicClient.java、NotificationApiProxyController.java。
Modify: backend/stock-notification-service/pom.xml、对应application.yml/application-compose.yml、backend/stock-gateway/src/main/java/com/aistock/gateway/ProxyHeaderPolicy.java、deploy/compose.yaml（只新增非敏感内部服务发现配置）。
Tests: AlertControllerTest.java、NotificationSecurityTest.java、gateway/NotificationProxyTest.java，均在对应test包新增README。

- [x] 核实现有SessionResolveController及市场/预测服务认证过滤器，只复用合同与约定，不信任客户端userId/角色头。
- [x] 安全测试RED：未登录、伪造身份、他人对象、ADMIN越权、停用账号、无CSRF/错误来源、直接访问服务，以及内部端点经网关不可达。
- [x] Notification内部注册邮件接口保持原服务认证；新公开API解析本人会话且校验写请求CSRF/Origin。必要使用Feign/Nacos，带有限超时及响应上限。
- [x] 网关按精确方法/路径白名单代理、限制body/查询字段，不以泛化/**开放整个通知服务；保留统一响应、Cookie策略和traceId。
- [x] 定向GREEN并回归注册验证码及原网关安全路径；本地提交。

Produces: 可从网关安全操作本人规则/偏好。Consumes: Task2服务及现有会话合同。
Completion: 未授权/越权全部拒绝，注册登录流程不改变。

## Task 4: 规则页面与个人偏好

Create: frontend/src/api/alerts.ts、frontend/src/pages/alerts/AlertsPage.vue及AlertsPage.test.ts、frontend/src/pages/alerts/README.md。
Modify: frontend/src/router/index.ts、frontend/src/App.vue、frontend/src/pages/market/MarketAnalysisPage.vue、frontend/src/pages/profile/ProfilePage.vue及测试、frontend/src/api/README.md、frontend/src/api/auth.ts（401过期事件增加/alerts与/notification-preferences路径及测试）。

- [x] 写页面测试RED：加载/新建/修改/启停、非法阈值、冲突重新读取、请求未知时同requestId恢复、账号切换清理、晚响应不覆盖现界面。
- [x] 新建表单以字符串传阈值，显示严格比较符、生效日期和“收盘后日线预警”；不让用户自行填写版本或owner。
- [x] 单股页加入设置预警入口、导航增加预警规则；个人中心增加默认关闭的邮件偏好，显示当前已验证邮箱状态及“自动预警与投递尚未启用”。不显示假的已触发消息。
- [x] 写入失败保留表单和原列表，避免双击重复创建；本人数据不存localStorage。小屏表格可横向滚动，键盘可操作、错误有可读说明。
- [x] npm test与npm run build通过；本地提交。

Produces: A阶段真实可操作页面。Consumes: Task3精确API字段。
Completion: 本人规则可持久修改，语义不暗示B/C已完成。

## Task 5: 本机部署与验收记录

Modify: docs/implementation-status.md、docs/pending-items.md、相关服务根README/资源配置README；Create docs/verification-sdd006a.md及docs/evidence/sdd006a-*.json。

- [x] 审查上述共享接口、完整执行隔离MySQL后端测试（scripts/check-auth-task1.ps1）和前端npm test/npm run build；失败先定位修复。
- [x] 只构建变更模块并更新相应本机Compose服务；不重置数据库，不变更SMTP/MaaS凭据。
- [x] 核验迁移、六服务健康、网关安全、实际三股边界；浏览器创建一条明确测试规则，修改/停用/重载读回。按用户自己的业务数据处理，不能伪造其他账号。
- [x] 保存原预测/模型/验证码流程不回归证据；没有真实用户邮件发送或付费模型调用。
- [x] 明确A完成、B/C未完成；记录策略默认值与后续扩展点，本地提交。不push/合并。

## 执行前审查

实施者须先检查文件是否存在、既有库迁移版本与服务安全合同；拟建文件路径是计划而非现状。Task1输出字段与Task2消费、Task2响应与Task3/4消费必须对照一遍。有共享接口冲突按设计修订并记录，不编造已通过状态。除权真实样本专项属于B的入口质量门，本次可用性抽查不代替该验收。

## 验证命令与期望

- 后端定向（在backend目录）：`mvn -B -Pcli-build -pl stock-market-service -am -Dtest=AlertBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`。Task1首次期望因新测试所验证的合同未实现而失败；实现后指定测试全部通过。其他任务改为对应模块/测试类；MySQL测试不得用默认排除mysql的结果宣称通过。
- 后端隔离库全量（仓库根）：`pwsh -File scripts/check-auth-task1.ps1`。前置独立测试库可用，不得连接生产库；期望0失败/错误，查看报告确认mysql组确实执行。
- 前端（frontend目录）：`npm test`及`npm run build`；期望全部测试、严格类型和构建通过，原有资源包体积提示如实保留。
- 提交前：`git diff --check`；期望无空白错误。仅git add本切片文件后本地提交，未配置remote不push。

## 执行核对

Task1–5完成。测试按实际职责集中在AlertRuleMysqlTest、NotificationSecurityTest等文件，没有为满足拟定文件名而重复建类；公开权限、数据所有者、revision、幂等、并发、当前资料和实际代理均有验证。真实浏览器只使用本人登录账号，不伪造其他身份。一次独立审查Important已修复，Minor及运行限制已记录；B/C不属于本切片完成声明。
