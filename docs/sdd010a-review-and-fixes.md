# SDD010A 单次独立审查恢复结果

2026-10-23恢复同一代理 /root/sdd010a_final_review，范围82e8beb..5bac2e2；0264428仅证据文档。未派第二审查。无Critical；Important一项：历史下一页503后丢失可见分页，需修复；Minor两项见下。审查未运行生产或修改文件。

Important：DataPage.vue历史非空cursor请求清空versions.data，失败仅恢复首屏；失去已读页与原cursor重试入口。修复采用恢复明确标注的已读页，原nextCursor保留；具体RED/GREEN证据随后写入。

Minor：1.adminMarketData.ts强制barDays等于交易+停牌日，无法展示非法trade_status行；当前DB CHECK阻止此记录，保留为未来兼容异常记录待办。2.marketDataPresentation.ts没有覆盖全部服务端安全错误码中文映射，仍安全兜底但解释不足。

Declined to judge共七项：后续写业务；外部真实性/停牌/日历/跨源事实；跨页全局原子快照；大规模容量和读取前网络内存硬限；畸形参数同形状预检；详情重复展示列表全部元数据；生产部署/角色/页面/回退验收。逐项裁决见progress.md，不把未评项静默丢弃。

## 已修复与实测

历史下一页503恢复：失败测试复现丢失已读页，保留原页及原游标后完整前端294项、类型/构建通过。默认64并行下两个未改动页面5秒超时，独立8项复核通过；限定2并行后全套通过，没有提高超时或改断言。

实际用户报告详情点击无感：确认按钮与请求有效，详情距视口3472px；增加既有标题焦点断言先失败，再在显式选版本后nextTick聚焦标题，身份/版本仍需匹配。完整前端295项及构建通过，实际宽/窄屏标题均进入视口。

实测390px整体宽876px：绝对定位辅助文字越出表格滚动区；仅给滚动包装层设置定位上下文，类型/构建及真实390px复核通过，页面宽375px，表格316px内滚动880px。该CSS不改变交互逻辑，未为几何结果写实现镜像式jsdom断言。

单次审查的重要问题已修复，不再次派审；两项minor明确保留。生产检查、用户普通角色报告与代理实际操作分开归档，未演练完整旧镜像回退。

## 本轮全部裁决与非阻断待办

- Ruling: Keep fixed checkout/branch per user-approved plan rather than create worktree — preserves explicit workflow — cost if wrong: selective staging must protect unrelated work.
- Final: minor (deferred): Frontend equality barDays=traded+suspended rejects invalid trade_status diagnosis; current DB CHECK blocks these rows, add compatibility before allowing dirty legacy/imported records.
- Final: minor (deferred): Complete Chinese text for every whitelisted safe error code; current fallback safely displays machine code but requires technical interpretation.
- Final: Ruling: Keep replenishment/unavailable-mark/global orphan audit in SDD010B — approved read-only scope — cost if wrong: admin write workflows need later delivery.
- Final: Ruling: Diagnose stored-version evidence only, not external truth/suspension certainty/cross-source accuracy — no independent external validation in scope — cost if wrong: diagnostic cleanliness cannot guarantee data truth.
- Final: Ruling: Keep keyset pagination without cross-page atomic snapshot — approved stable cursor contract — cost if wrong: current-head facts can change between pages, each page has its own query time.
- Final: Ruling: Capacity and pre-decode memory/network hard limits remain unverified — small EXPLAIN and decoded256KiB bound are not load/streaming tests — cost if wrong: large datasets/responses may timeout or allocate memory before rejection.
- Final: Ruling: Same-shape malformed-argument CORS preflight may succeed — actual GET still strict path/role/query validation — cost if wrong: preflight alone does not guarantee eventual query acceptance; no expanded data permission.
- Final: Ruling: Do not duplicate all history metadata in fixed detail — listing already displays state/time and detail labels fixed identity/window — cost if wrong: administrator may need to return to history list for some metadata.
- Final: Ruling: Production/roles/responsive/keyboard remain separate Task6 gates, full image rollback remains unperformed — review is code-only — cost if wrong: cannot claim real acceptance from automated tests alone.
- Final: Ruling: Limit verification run to2 Vitest workers after default64-worker run caused unrelated Users/Messages5s timeouts — both unchanged suites passed separately8/8 with2 workers and no timeout increase — cost if wrong: longer suite wall time; functional assertions and deadlines unchanged. Full bounded run required before deploy.
- Final: Ruling: Add explicit-selection focus/scroll during real acceptance — user report proved result below viewport, reasonable usability without new business — cost if wrong: explicit version click moves focus; background refresh and stale responses cannot move it.
- Final: Ruling: Contain absolute accessibility text within table scroll regions — real narrow-screen overflow affects reading/navigation — cost if wrong: wrapper establishes positioning context; labels and table scrolling must remain verified.
