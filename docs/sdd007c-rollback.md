# SDD007C 受控回退步骤

这是应急手册，不表示已在生产演练。保留V9新增表和已有解释，不降库、不删数据。旧MaasOperationGate不认识COMPARISON_EXPLANATION，不能只直接切回旧镜像。

1. 暂停网关新入口：`docker compose --env-file .env -f deploy/compose.yaml stop stock-gateway`。保留新版预测服务，使在途任务有机会完成；这会短暂影响页面API，应安排维护时段。
2. 用stock_prediction本库账户只读检查slot_id=1。活跃租约仍有效时等待；正常MaaS预算35秒、租约50秒，不强清有效任务。令COMPARISON_AI_ENABLED=false，保留其他开关。
3. 若C槽已经过期仍存在，仅执行下列本库事务。凭据只由现有容器环境提供，不写脚本或命令实参。命令执行前后均记录槽和操作状态，不输出授权Key。

```sql
START TRANSACTION;
SELECT operation_id,operation_kind,lease_until
FROM prediction_explanation_slot WHERE slot_id=1 FOR UPDATE;
UPDATE comparison_ai_operation o
JOIN prediction_explanation_slot s ON s.operation_id=o.operation_id
SET o.state='FAILED',o.error_code='AI_INTERRUPTED',o.finished_at=UTC_TIMESTAMP(3)
WHERE s.slot_id=1 AND s.operation_kind='COMPARISON_EXPLANATION'
AND s.lease_until<=UTC_TIMESTAMP(3) AND o.state='RUNNING';
UPDATE prediction_explanation_slot
SET operation_id=NULL,operation_kind=NULL,lease_until=NULL
WHERE slot_id=1 AND operation_kind='COMPARISON_EXPLANATION'
AND lease_until<=UTC_TIMESTAMP(3);
COMMIT;
```

4. 确认C槽不存在，最好共享槽已空，再切回保存的三个镜像：

```powershell
docker compose --env-file .env -f deploy/compose.yaml -f runtime/sdd007c-rollback.override.yaml up -d --no-build stock-prediction-service stock-gateway frontend
```

5. 检查旧服务健康、Flyway对已应用V9的兼容性及既有单股业务；不关闭迁移校验掩盖启动错误。若旧镜像不接受现有schema历史，恢复新版并保持C开关关闭，采用前向修复。账号/自选/模型/预测/比较/旧解释均保持，邮件工作器仍false。

旧镜像标签：aistock/stock-prediction-service:before-sdd007c-20261013、aistock/stock-gateway:before-sdd007c-20261013、aistock/frontend:before-sdd007c-20261013。回退前核对runtime/sdd007c/before-images.json中的实际摘要。

## 已执行的隔离验证

2026-10-13：探针直接编译09f1f8c旧MaasOperationGate，在隔离测试库事务中设置过期C槽，确认旧代码拒绝该kind；按上述条件清理后，旧gate可以继续旧业务。事务最后回滚，原槽及操作数量保留。见runtime/sdd007c-rollback-probe.log。旧生产镜像及旧Flyway对V9的完整启动尚未演练，不能等同于本项隔离验证。
