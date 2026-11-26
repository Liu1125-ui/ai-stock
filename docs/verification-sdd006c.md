# SDD006C 预警邮件核验记录

日期：2026-10-12。代码、隔离验证及默认关闭外发的本机部署完成；真实预警邮件和浏览器视觉验收尚未完成，不能称为整片外发验收通过。

## 实现范围

新触发、站内消息、独立邮件渠道决策同事务提交；不开启邮件也保留渠道原因，不给旧消息补发。工作器读取受保护的当前已验证邮箱与账号修订，再核对规则和偏好；固定证据生成正文，加密保存。发送前提交SENDING，明确失败有限退避，提交结果不确定或发送租约过期转UNKNOWN、不自动重发。关闭发送或缺少SMTP配置时仍安全维护过期状态，不访问账号/SMTP。

本人消息详情提供按需查看/刷新邮件状态；个人中心分别显示个人意愿和服务可用状态。SENT只显示“邮件服务器已接受”，不等同用户收到。无记录显示NOT_RECORDED，状态读接口不发送或修改数据。

## 验证结果

- 顺序执行scripts/check-auth-task1.ps1 clean verify：后端536项，失败/错误/跳过均0。日志runtime/sdd006c-final-fixed-backend.log，覆盖原验证码及站内通知回归。
- 前端28文件107项通过，vue-tsc及生产构建通过。日志runtime/sdd006c-final-fixed-ui.log、sdd006c-final-fixed-ui-build.log。既有大包提示仍在。
- 原算法容器pytest：76项通过，1项既有Starlette/anyio弃用提示；未修改算法或模型。日志runtime/sdd006c-final-python.log。
- 真实本机测试SMTP服务器覆盖接受、RCPT拒绝、DATA后断连及关闭端口；全部为loopback，不连接真实发件服务。
- 数据库隔离测试覆盖原子回滚、唯一决策、并发领取、修订取消、退避/TTL、未知不重发、成功后回写失败、停用/未配置维护矩阵及准备事务临时失败后的恢复。
- 最终独立审查3项Important已RED→GREEN修复，见[sdd006c-review-and-fixes.md](sdd006c-review-and-fixes.md)。没有第二轮代理审查。

## 本机部署与数据保全

构建并更新user/notification/gateway/frontend镜像；恢复既有market/prediction及最后恢复scheduler，保留数据库卷。六Java健康UP，algorithm健康UP；通知库V4成功。实际ALERT_MAIL_WORKER_ENABLED=false，不修改个人偏好。新增邮件决策表0行。

部署前后以及调度恢复后，对比三股RAW头、原预测/模型快照哈希、模型制品哈希与状态、规则及偏好修订、触发/站内消息、原注册邮件计数：全部一致。基线、结果及旧镜像ID保存在本机忽略的runtime/sdd006c-before-state.json、after-state.json、after-scheduler-state.json和rollback-images.json。最终检查发现保存的旧镜像ID均无法解析，不能用于直接回滚。已从部署前Git版本14063e6独立重建四个带rollback-14063e6标签的镜像，旧版本后端打包及前端类型/构建成功，四个标签均可inspect；记录runtime/sdd006c-rebuilt-rollback-images.json。这些是原版本源码重建的回滚制品，不声称与旧二进制完全相同；未实际切换验证。迁移只新增表，应用回滚时保留该表，不删卷。

匿名内部context/target均401，网关不开放内部target返回404，公开邮件状态匿名401。前端HTTP200，index.html与本轮dist一致。以上不替代登录后的真实本人页面核验。

## 尚未完成与限制

1. 浏览器CUA初始化及Node REPL替代均报“failed to write kernel assets / 系统找不到指定的路径（os error 3）”。没有读取或冒用登录令牌；桌面、窄屏、重载和真实本人状态页面仍待工具恢复或用户验收。
2. 当前唯一既有规则停用、偏好关闭、真实触发0。没有修改阈值、生效边界或制造消息，不把注册验证码收到当作预警邮件验收。
3. 真实预警邮件需另行明确收件人、最多封数并授权启用，等待符合规则的新自然触发。实际本轮外发0、MaaS调用0，不承诺真实邮箱送达或长期可用。
4. 网关既有重复Origin/CSRF头行为、更多昵称修订端到端矩阵与状态机纯格式整理另列待办；不把未逐条执行的计划测试描述为已全部覆盖。

当前仓储仍使用JdbcTemplate；MyBatis-Plus常规业务接入后续独立实施。开发据实为Codex，CodeArts真实使用证据后补。沿用本地codex/sdd004-market-refresh，无remote，不push/不创建PR。

机器核验摘要：[evidence/sdd006c-alert-mail-checks.json](evidence/sdd006c-alert-mail-checks.json)。

## 备用回滚操作（本轮未执行）

在项目根目录PowerShell，保持外发关闭后，使用本机已生成override恢复四个部署前源码镜像；保持其他服务和数据库卷。

```powershell
$env:ALERT_MAIL_WORKER_ENABLED='false'
docker compose --env-file .env -f deploy/compose.yaml -f runtime/sdd006c-rollback.override.yaml up -d --no-build --no-deps stock-user-service stock-notification-service stock-gateway frontend
```

操作后仍需检查健康、登录、原数据和注册验证码业务。此流程未实际切换演练，不以镜像构建成功代替回滚运行验收。
