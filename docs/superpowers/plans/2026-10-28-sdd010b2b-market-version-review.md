# SDD010B2B 行情版本审核 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 固定版本审核标记、说明、幂等保存和不可覆盖的历史。
**Architecture:** market本库拥有两表事务；common严格契约；gateway精确ADMIN/Origin和有界单次转发；Vue面板按身份和版本隔离。
**Tech Stack:** 现有Spring Boot/MySQL/Vue3/TypeScript，不增加依赖。
**Spec:** docs/superpowers/specs/2026-10-28-sdd010b2b-market-version-review-design.md

## Global Constraints

- 审核非停用，不修改行情版本/当前头/原结果；仍13业务、五服务两角色。
- 输入4KiB、说明1—300、历史20、操作UUID/版本revision字符串；原回执不可变。
- 权威身份、Origin+action标记、零查询参数正文限制、no-store、无POST重试。
- 无真实取数/付费AI/邮件或生产虚构质量标记；不推送远端。

## Review Focus

1. 并发原revision只成功一项，主键冲突不能遗留当前行改变（Task1）。
2. 读取失败不能用revision0覆盖已有审核（Task2/3）。
3. 未确认提交刷新/换号/迟到响应不能重放或串号（Task3）。
4. 普通身份、伪造actor、直连和恶意Origin不能写（Task2）。
5. 新表FK影响既有测试夹具和回滚保全（Task1/4）。

### Task 1：公共契约与事务存储

**Files:** common/marketreview/MarketReviewContract.java与README/测试；market/marketreview/MarketReviewService.java与README；V14__market_version_review.sql；MarketReviewMysqlTest；MarketDatabaseFixture。
**Interfaces:** Command(operationId,expectedRevision,reviewState,note)；Receipt(operationId,instrumentId,versionId,revision,reviewState,note,createdAt)；Page(instrumentId,versionId,revision,current,history,hasMore,queriedAt)。Contract.parse/validate/路径；Service.read(stock,version),save(stock,version,actor,Command),operation(stock,version,actor,op)。
- [x] 契约和MySQL测试先RED：严格JSON、单次保存、相同命令回执、不同内容/actor冲突、陈旧/并发、无记录读取不写、错归属/demo拒绝、版本状态/日线不变。
- [x] 跑common与market专项，观察缺类型失败；实现V14/事务；同测试GREEN，既有清理顺序补新表。
- [x] 选择性提交事务功能。

### Task 2：权威安全与网关

**Files:** market/marketreview/MarketReviewController.java；AdminMarketDataAccessFilter、MarketSecurityConfiguration；gateway/MarketReviewPublicClient.java、adminmarket/MarketReviewProxyController.java、CORS、ProxyHeaderPolicy及README；HTTP和proxy测试。
**Interfaces:** 三资源按spec，ContentType/Origin/marker/body；新admin actor属性不可信请求透传。GET只调用read/operation，POST只save；Receipt/Page验证复用common。
- [x] RED身份/恶意来源/重复头/正文/参数/未知路径及错误脱敏、只调用一次。
- [x] 实现；HTTP与gateway专项及既有行情路由回归GREEN，选择性提交。

### Task 3：审核面板

**Files:** frontend/api/marketVersionReview.ts及test；admin/data/VersionReviewPanel.vue及test；DataPage及README。
**Interfaces:** loadReviews(stock,version)、saveReview(stock,version,Command)、getReviewOperation(stock,version,operationId)；面板props instrumentId/versionId/actorUserId，emit accessDenied。
- [x] RED默认无查询、加载才能写、校验/并发点击、单POST、503 pending与session恢复、404仍阻挡新编号、原命令手动重提、GET核对成功、409刷新、版本/身份隔离及授权清理。
- [x] 实现相邻独立面板和API严格响应校验；全前端test/typecheck/build GREEN，选择性提交。

### Task 4：审查、部署、只读验收

**Files:** docs核验、review/evidence/status/pending、deploy说明。
- [x] 完整受影响common/market/gateway clean verify，既有其他后端源码未变复用上片证据；一次独立最终审查和必要修复回归。
- [x] 备份原库/三个旧镜像；部署market迁移V14、gateway/frontend，核验健康/产物/匿名与ADMIN/权限/窄屏/键盘。只读，不给生产版本编造审核结论。
- [x] 文档准确区分代码/MySQL测试/现场只读验收；保留原开关/卷；提交且工作区干净。FR-11仍部分完成。

## 自审

GET无记录不创建审核；读失败阻挡写，不用空状态兜底。全局op跨版本并发由唯一键冲突+事务回滚，版本内revision锁串行。Page历史与current在同RR事务；当前页不伪造唯一业务数。失联请求不能以一次404判定永远未保存。新表只追加，不侵入原行情数据路径。

执行裁决：自审发现仅保留op可能永久卡住未到达服务器的请求。新增RED反例后改为当前会话保存原命令，404后仅允许显式重提完全相同命令/编号，不自动重试；成功或权限撤销清除。此范围不增加外部调用。
