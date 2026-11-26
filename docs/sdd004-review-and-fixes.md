# SDD-004 整体审查与修复记录

日期：2026-10-02。审查范围为 BASE 80ae6c4 至审查时 HEAD 76b78c1，加规格、计划和执行记录。按执行技能要求仅进行一次独立只读整体审查；审查者未读取本机秘密，也未编辑代码或实际数据库。

## 审查发现及修复

1. 发布事务可能在 source_code_map 锁等待后超过租约，回执终态与版本提交可能相互矛盾。修复：刷新发布先锁执行回执，阻塞写入放在最终期限检查前；行情头、导入结果与 AVAILABLE 回执同事务提交，结束回执返回已保存的真实终态，禁止失败覆盖已成功结果。publicationLockWaitCannotCrossDeadlineOrLeaveFalseReceipt、原子回执及成功提交后响应丢失测试均 RED→GREEN。
2. 调度在发布成功但响应丢失或重启时，可能盲目发起新尝试且丢失成功结果。修复：增加受身份保护的 MS-02 GET 执行回执和 MS-04 回执客户端；未知结果进入 RECONCILING，尝试为 UNKNOWN；先核对既有操作，确证失败后才按条件重试。publishedResultRecoveredAfterLostResponseWithoutNewAttempt、restartReconcilesOldSuccessfulReceiptAndUnknownNeverRetriesBlindly 及过期令牌测试 RED→GREEN。新回执路径的解析前身份检查另行 RED→GREEN。
3. 全历史空行情被误当作合法空结果，绕过原历史起点和交易日历完整性。修复：Python 空结果保留完整日历证据；已有历史股票的刷新若 bars 为空，返回 MARKET_DATA_INCOMPLETE，不发布、不中断旧行情查看。emptyFullHistoryIsIncompleteNotSuccessfulEmpty 与 Python 空证据测试 RED→GREEN。
4. Java 调用算法服务遇到拒绝连接/连接重置时被误分类为不可重试的数据非法。修复：HTTP 超时归 SOURCE_TIMEOUT，传输 IOException 归 SOURCE_UNAVAILABLE；畸形 JSON、响应超限仍归 SOURCE_INVALID。实际本地 HTTP 边界测试 RED→GREEN。
5. 上午重启时漏补上一日最新到期计划。修复：北京时间 20 点前最新到期日为昨日，20 点后为当日，股票/目标日任务键不变。上午补建、同日去重及晚间新日测试 RED→GREEN。

## 最终验证

最终全后端 140 项、Python 容器 18 项测试通过，失败与错误均为零；现有前端 18 项及构建通过。真实三股更新、实际查询 FRESH、同内容版本复用、每日计划及调度重启也已验证。详见 verification-sdd004.md 和忽略目录 runtime/sdd004 的具体日志。

审查者未判断：真实外部源成功、父会话完整运行/重启、生产秘密管理、Feign 具体传输实现的严格总体墙钟上限、管理员界面与后续模型业务。前两项已由本会话另行实际验证；生产秘密管理不以本机开发验收代替。Feign 配置连接 2 秒、读取 45 秒，但不宣称覆盖 DNS、重试等所有因素的形式化总体时限证明；发布正确性由独立租约截止检查和回执互斥保证。管理员页面、股票扩容及模型接入依计划保留后续范围。

Ruling：不确定结果保持核对状态，不用自动重试掩盖可能已发布的结果；成本是来源或服务长期不可用时任务可能停留 RECONCILING，恢复后继续核对。规则不影响旧行情查看。
Ruling：未修改原十三项业务，也未新增生产上线承诺。内部调度界面和生产配置属于后续范围，其未完成已在实施状态中明示。
Ruling：用户授权自主完成本阶段，保留当前 codex/sdd004-market-refresh 分支和固定目录，先完成本地小步提交；无 Git 远程，未执行合并、推送或 PR。避免把收尾选择当作新的阻塞审批。
