## Task1—6最终独立审查（2026-10-27）

审查范围867ab40..49fd3ca及compose开关映射。独立reviewer只读，未运行测试、未读取凭据，Critical0、Important2、Minor0。两项均进入一次修复与回归，不将审查代替运行证据。

- 股票级重取冷却误用原任务重试冷却配置。新增两项配置不同时的反例；分别注入股票300秒和任务60秒，确保两条限制同时成立，原普通任务策略不变。
- 新增面板/历史未把权限撤销403通知父页。三项反例测试先失败；修复统一401/ACCESS_DENIED处理、清除账号恢复信息和子区数据、通知父页清空管理员列表/详情并刷新身份，使迟到响应失效。

前两步原审查及早期部署记录保留如下，不作为本次完整部署状态。

> 2026-10-27：内部行情预检错误响应no-store遗漏已在源码修复；先复现3项失败，再通过9项专项及公共43/行情。Docker当前未运行，本轮未部署、未执行MySQL集成测试；原生产Minor保留待部署验收。见[修复记录](fix-admin-refresh-error-cache-20261027.md)。Task3—7仍待实现。
# SDD010B1前两步部署审查与修复

2026-10-25。用户要求将已实现Task1/2独立部署，调整原计划“全阶段Task7才部署”的执行顺序；不代表Task3—7或FR-11完成。仅替换market镜像，无生产schema迁移，不更改公开页面/网关/调度及原开关。

独立审查范围867ab40..9c5c218，Critical0、Important1、Minor1。

Important：Constraint.headRevision使用默认long反序列化会接受1.9、字符串1和1e0，能够匹配当前revision1。已用真实MockMvc+隔离MySQL及本地collector复现：8项HTTP测试中3项应400实际200。修复复用现有StrictNonNegativeLongDeserializer；仅明确JSON整数可接受，缺失/null/负数/溢出继续拒绝。GREEN及完整回归结果后续追加。

Minor（保留）：新内部context GET成功响应有no-store，但有效服务身份下400/404/503走公共异常处理时缺少该头。当前仅内部安全错误，不含业务私有正文或会话；正常结果和认证拒绝都有no-store，不阻断本次发布。留待后续本阶段接口缓存头统一，不扩大此次修复面。

Declined to judge裁决：Task3—7非本次范围，保持未实现；镜像/健康/回滚由实际部署证据核验，备份不等于回滚演练；测试成绩以主执行者命令结果为准；真实供应商/数值预测/邮件不在本次授权范围，使用只读及隔离证据；工作区由主执行者git status核对。没有忽略额外业务或安全问题。
## 修复与实际部署结果

修复提交db04f5d。真实HTTP的8项反例/正常整数测试全部通过，受影响专项common26/market22通过；随后common43及market232完整clean verify通过，含新HTTP测试，零失败/错误/跳过。只部署market，原旧四字段请求链路和旧预测输入以隔离回归及生产只读三股窗口核对，不以真实供应商调用冒充测试。

生产market镜像sha256:87ee18cc306e937edf244ebeadaa554145bf9d0fcfe5f300397ac9699e0e2207；JAR SHA256698bbe7b2d8f85d4fbeca9086c983f8788ab5ecf65b2741b2faf86cceb89c6f6，与构建产物相同。生产库V13、整库数据dump哈希不变。备份和运行核验见docs/evidence/sdd010b1-partial-deployment.json。Minor缓存头遗漏在实际badQuery GET中再次确认，保留待办，未声称已修复。