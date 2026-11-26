# SDD010B2B 行情版本审核与处理记录详细设计

2026-10-28。执行者选定的FR-11增量；用户已授权自主设计、检查、编码、测试和本机部署，不再要求可行性确认。沿用五业务服务、ADMIN/USER、Spring Boot/MySQL和Vue3。当前分支继续使用，不远程推送。

## 为什么先做审核记录

已有引用概览不具备分布式在途互锁。Forecast/Comparison等读取、发布和旧结果重放各有语义，不能以引用为零直接开放停用。因此本片实现可独立使用的版本审核业务，不修改market_data_version.status或当前头，不改变模型、选择候选、任务、行情内容和历史结果，不声称完成停用/恢复。状态只表示管理员处理进度，不表示行情准确性已得到系统保证。

## 业务与持久化

在固定版本详情主动加载审核记录；无记录为UNREVIEWED、revision字符串0。管理员填写1—300字符说明，选择UNREVIEWED（撤回核对结论）、REVIEWED（已核对）、NEEDS_REVIEW（需复核）。任何状态切换均需说明并追加不可覆盖的历史；最近20条按版本revision降序展示，hasMore只提示存在更早记录，本片不删除历史。当前版本也可做审核标记，因为不影响可用状态。

新增本库V14：market_version_review（version_id主键、revision），market_version_review_operation（operation_id全局UUID主键、version_id、actor_user_id、expected_revision、revision、review_state、note、created_at；版本+revision唯一）。同库外键保护，测试夹具先清子表；不跨库。读取RR事务、写入事务5秒；版本归属/非demo先核对，写入锁定版本行及审核行。版本状态不变。actor来自权威身份解析，禁止请求传入。

POST包含operationId、expectedRevision（规范非负long十进制字符串，最大Long.MAX_VALUE-1）、reviewState、note；严格字段、重复字段/尾随JSON/超4KiB/控制字符拒绝。每个operation相同actor/版本/规范命令重放原回执；换actor或内容409 REVIEW_OPERATION_CONFLICT。陈旧revision409 REVIEW_REVISION_CONFLICT，不静默覆盖。不同版本竞争同operation主键时事务回滚并返回冲突，无部分更新。返回只含审核内容、时间、版本和operation编号，不公开actor ID；原操作者可查自己的operation，其他管理员查询该操作统一404；所有管理员可看版本审核历史。

## 接口与安全

GET/POST /api/v1/admin/market-data/{stock}/versions/{version}/reviews；GET /.../reviews/{operationId}。GET无参数/正文；POST无query、单Content-Type application/json、单Origin且为既有允许地址、X-Aistock-Admin-Action单值1。UUID为规范小写，股票SH/SZ六位；未知路径/方法404。网关和行情直连均权威ADMIN检查，匿名401、USER403、身份失败503；所有响应no-store。OPTIONS精确CORS，不调用身份/数据库。POST不重试，公开请求不能指定actor、表名或SQL。

公共契约严格验证Page/Receipt及输入，网关独立有界Feign（沿用已验证64KiB/无重定向/无重试传输）；输出只重建白名单包络/业务字段，不泄露上游异常。错误码白名单：REVIEW_REQUEST_INVALID400，REVIEW_OPERATION_CONFLICT/REVIEW_REVISION_CONFLICT409，REVIEW_OPERATION_NOT_FOUND404，以及既有AUTH/ACCESS/RESOURCE/503。

## 页面

独立VersionReviewPanel，和引用检查相邻。默认不自动加载，明确审核不改变版本可用性。加载后显示当前标记与说明、最近20条、版本revision；提交按钮在加载成功、说明有效且无未确认请求时可用。点击确认后生成一次UUID，仅一次POST。超时或503保留operationId并阻止新提交，提供“查询本次保存结果”（GET）；重载时按账号+股票+版本在sessionStorage保存受限原命令（编号、原修订、标记、说明），仅用于未确认请求恢复，成功/权限撤销后清除，不发送到第三方。404表示目前未查到，不断言原请求不会稍后提交；继续阻挡新编号操作，允许再次只读查询，或手动“使用原编号再次保存”逐字重提原命令，依赖服务端幂等防止原请求迟到形成重复。没有原命令的兼容编号只提供查询。409陈旧需显式重新加载记录，清空提交状态；同一operation冲突仍保留以便核对。成功清理pending并加载最新记录。切换版本/账号/卸载保护迟到结果；401/ACCESS_DENIED403清空私人状态与该账号pending并通知父页；503已有结果标旧时间、不当最新。文本Vue转义，无v-html。

## 验证、部署和回滚

TDD覆盖输入严格性/幂等/陈旧版本/多管理员冲突/并发/清理/归属和权限、Origin、POST不重试、未知保存恢复、账号切换和迟到响应；真实测试只用stock_market_test，业务数据不参与。完整受影响后端、前端测试/类型/构建，一次最终独立审查。备份原库与三个旧镜像，先市场迁移，再网关和前端；账户/算法/其他业务服务和原卷不动；只读生产验收不人工添加虚构质量结论，不调用供应商/MaaS/邮件。不因本片改变原后台开关。回滚镜像保留新增两表及审计数据，不drop或恢复覆盖业务库；如迁移失败停止部署。

后续仍需独立设计在途准入/冻结快照和消费者状态语义，才可实现真正停用。FR-11仍部分完成。
