# SDD010B3 Task5C 预警评价使用凭据计划

按父Task5连续执行，无例行确认；执行方式executing-plans，功能末单次独立审查。

目标：预警持久操作在读取原证据内容前登记ALERT_EVALUATION原RAW，触发/站内信/邮件出站决策与真实终态仍同事务。

1. [x] 测试先行：许可拒绝不读取内容；已固定证据与错RAW；成功先提交触发/站内信/outbox再通知；未知登记不重POST；真实过期/迟到/锁等待证明；内部证明鉴权。
2. [x] 市场新增只读固定引用元数据接口 /internal/v1/market/alert-evidence-references/{id}，只返回evidenceId/instrumentId/rawVersionId，不读价格/证据正文，不登记引用。仍受内部服务认证，非证据内容门禁绕路。原内容入口门禁不变。
3. [x] 通知V5新增alert_evaluation_market_use、原操作fencing。执行身份为原全局operationId、generation=1；先核对实际RUNNING原evidenceId，再取固定引用，持久selector后一次POST；重启只查原市场requestId，不重新取元数据、行情或评价。
4. [x] ALERTEVALUATION专用内部client、scope、proof/recovery；不跨服务依赖prediction包。仅固定证据正文GET带用凭据，账号读取/当前边界不带；保留当前边界新鲜性及SKIPPED_STALE语义。
5. [x] store既有owner guard→operation→rule顺序不变，终态与trigger/inbox/mail同事务。proof沿用guard→operation并在锁后检查真实期限，投影非FAILED终态为SUCCEEDED，resultIdentity优先triggerId否则原operationId。未知/在途不能释放。
6. [x] 配置默认false、能力PARTIAL、固定引用扫描新增使用记录；完整公共/市场/通知回归、打包/静态校验、独立审查，记录并单功能提交。禁止真实SMTP、AI与数据源调用。

保留项：预警生产者和market本地worker在Task5D完成，不能凭该消费者接入提前开启全局生命周期动作。

结果：515项完整verify通过及打包，独立审查无重要项；未部署，继续Task5D。
