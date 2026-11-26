# SDD010B3 Task5A多股比较使用凭据实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline. No implementer subagents; one independent review after the feature.

**Goal:** 比较保护原evidenceIds的真实RAW全集，并保留过期重领及旧执行不可再写的真实终态证明。

**Architecture:** 使用现有lease_generation UUID作为每轮全局执行ID，协议generation=1。父比较操作仍负责业务CAS，独立使用记录保留各轮凭据/终态；短事务内锁父行再写使用记录，网络外置。

**Tech Stack:** Spring Boot/JdbcTemplate/MySQL、现有bounded HTTP、Flyway V15。

**Spec:** docs/superpowers/specs/2026-11-04-sdd010b3-version-lifecycle-design.md §18，以及原Task5。

## Global Constraints

- 两角色、五服务、原业务指纹和公开请求契约不改；不跨库SQL。
- 只保护原股票/evidenceId配对，禁止用新头冒充旧证据。
- 租约到期不能单独释放；必须形成不可逆的执行隔离/终态。
- 新开关false，未齐备不部署或开启全局动作；无真实AI/数据源/SMTP调用。

## Review Focus

- 旧执行在重领后迟到：原UUID永远不能保存结果。
- proof先到、重领后到：原业务仍可重领，旧证明稳定。
- 时钟回拨：已退休UUID不能因时间变化恢复写权限。
- 未知登记与错RAW全集：不能新POST/换头/计算或部分释放。
- 不同用户相同公开requestId：执行身份独立，证明不泄露用户。

## Task A: 每轮持久终态

Files: V15__comparison_market_use.sql；comparison/ComparisonRepository.java；ComparisonUseMysqlTest.java。

- [x] 先写重领/迟到/proof顺序反例并观察失败。
- [x] 新增父行fencing_revision及comparison_market_use；claim重领先退休旧执行，再换UUID。
- [x] complete/fail同事务保存实际终态；proof到期先真正替换UUID，再保存失败，不把父请求永久失败。
- [x] 验证时钟回拨、原指纹、原历史结果不变。

## Task B: 选择、读取与owner协议

Files: marketuse/MarketUseClient.java、MarketUseContext.java、ComparisonMarketUseOwner.java；comparison/ComparisonService.java、BoundedComparisonHttp.java、ComparisonConfiguration.java。

- [x] 先写COMPARISON_EVIDENCE多RAW响应/固定配对测试。
- [x] 扩展Request/Receipt/Scope支持2—3股完整集合，逐use校验；沿用分析和预测单股构造。
- [x] 比较claim后登记原配对，所有证据GET带相同凭据，完整RAW集合匹配后再计算。
- [x] 成功/失败提交后通知；未知登记仅GET原编号；恢复不计算。

## Task C: 证明、恢复与默认配置

Files: MarketUseProofController.java、MarketUseRecovery.java、MarketUseConfiguration.java；application.yml、compose/.env.example；固定引用扫描。

- [x] 证明分发COMPARISON，仅九字段；停止登记后仍可查原终态。
- [x] 恢复每批25条先轮转，坏数据不当成已释放。
- [x] 增加默认false的比较使用开关，固定引用扫描包含新记录；不启生产动作。

## Task D: 验证与提交

- [x] 真MySQL双连接验证重领/保存互锁、proof/重领互锁；本机HTTP检查相同凭据与manifest。
- [x] 相关公共/市场/预测完整回归、独立审查；重要项先RED再修复GREEN。
- [x] 更新核验、实施状态和待办，只提交本功能，然后连续Task5B持仓。

## 执行结果

2026-11-08完成，独立审查无重要问题；公共65/行情313/预测305共683项通过及打包。见 docs/verification-sdd010b3-task5a.md。仅隔离验证，未部署，Task5其余部分继续。
