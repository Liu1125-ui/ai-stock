# SDD007D 比较证据更新回退说明

日期：2026-10-14。本片只升级MS02和MS04，不改变预测、账号、用户比较和AI结果。

## 已保留与已验证

现用两个镜像已保留为aistock/stock-market-service:sdd007d-before、aistock/stock-scheduler-service:sdd007d-before，原始摘要和override保存于忽略的runtime/sdd007d-rollback-images.json及runtime/sdd007d-rollback.override.yaml。旧镜像完整Flyway启动尚未演练，不能据此宣称完整生产回退通过。

隔离库探针同时使用d44300c实际旧TaskRepository、TaskWorker与其PredictionTaskClient（仅统一重命名避免与当前类冲突）。旧程序领取COMPARISON_UPDATE会产生TASK_TYPE_UNSUPPORTED；排空后旧RAW任务可成功。探针所有数据在同一测试库事务回滚，零供应商请求。日志runtime/sdd007d-rollback-probe.log。

## 回退步骤

1. 关闭COMPARISON_UPDATE_PLAN_ENABLED，防止创建新周期。比较worker保留新版，先完成原尝试的只读回执核对；不得把未知结果当成可再次采集。
2. 在不领取新比较尝试的维护配置下，通过现有内部cancel受控取消QUEUED/RETRY_WAIT比较任务，保留任务历史。不取消RUNNING/RECONCILING。
3. 已在途任务由原回执至终态或原租约到期核对，不续租、不重新发POST。确认没有新类型处于QUEUED/RETRY_WAIT/RUNNING/RECONCILING后，关闭比较worker。
4. 使用保存的两个镜像与回退override替换MS02/MS04；保留V12/V13/V7表与已有证据、任务，不降库、不删数据。先检查旧程序Flyway启动兼容；若不兼容，保留新版关闭新开关并前向修复。
5. 核对原比较及解释哈希、模型/预测数量、其他服务健康和旧类型任务，再恢复旧业务运行。不能直接换旧调度镜像留下可领取的新类型。

所有新比较开关当前默认false。真实首验每股固定attemptLimit=1，总最多9次SDK数据查询；启用默认三次重试需三股首验和保全通过。本文为操作手册，不表示真实采集或完整镜像回退已验收。

2026-10-17状态更新：已部署MS02市场V13和MS04调度V7，并完成三股真实首验及页面核对；正常比较计划/工作器当前true。回退时仍须先停新计划、核对在途回执并排空新类型，再按本文切换，不能依赖文中先前默认false的历史状态。旧镜像完整Flyway启动仍未演练。
