# SDD010B3 Task5D3 市场预警证据派生保护计划

沿用父Task5连续执行，按executing-plans实施；本阶段结束一次独立审查。不修改13业务/五服务/两角色范围。

1. [x] RED先测：原RAW拒绝不读bars/不collect、在途DRAINING可完成、终态后释放、固定原requestHash和RAW、不重取头、同请求重放不重复登记；真实超时证明及回拨/迟到；未知登记原编号恢复。
2. [x] V19增加alert_evidence_operation.fencing_revision及market_local_alert_use。原操作只有request_hash，使用记录持久完整原请求JSON（仅operationId/stock/RAW/date/lease，无用户凭据），证明重算原hash与真实父操作核对。旧行不回填伪造请求/时间。
3. [x] AlertLocalUseOwner在alert包内复用同库AlertEvidenceStore，避免service↔owner循环依赖。store/useInput校验原hash且实际RUNNING，终态证明锁真实父行并锁后检查lease，先物理失败后证明。AVAILABLE/SKIPPED_STALE投影SUCCEEDED，FAILED投影FAILED。
4. [x] AlertEvidenceService先持久begin，再申请原RAW scope，再prepareLocal/collector/publish。实际发布、失败、终态fencing仍同事务；scope结束后通知。同库register单独短事务，先持久原requestId/started，未知只查原编号；恢复每批25条不POST、不collect。
5. [x] 默认false的新开关与protocol/evidence gates强绑定；仅开启该owner时，已内部认证的alert-evidence/prepare POST准入移至服务，其他路径继续门禁。新增实际market证明purpose ALERT_EVIDENCE_PUBLISH，仍PARTIAL。固定引用由本地父/使用记录/生命周期bundle核对，不扩跨服务owner报告。
6. [x] HTTP/token/其他路径不放行、配置错误拒绝启动、MySQL真实竞争和完整行情verify、独立审查、单功能提交。保持通知侧当前边界核验，旧证据不触发补发；无真实行情/AI/SMTP。

接续：Task5D4逐入口核对forecast原子发布与realization借用预测验证scope是否完整；必要修改后再Task6—8管理员API/UI和受控部署，未齐备不启用全局enforced。
