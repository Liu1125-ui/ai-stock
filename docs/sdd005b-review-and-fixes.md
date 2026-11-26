# SDD-005B最终审核与处置

2026-10-03。按executing-plans/requesting-code-review完成一次独立只读审查，范围f868780..c24234e；未让审查者读凭证、启动容器或修改代码。审查结果Critical0、Important1、Minor0，执行者按实际影响维持Important评级。

## 修复

1. 深层JSON仍小于1MiB却可触发json.loads的RecursionError，HTTP产生未处理500。新增test_deeply_nested_json_is_controlled_before_worker，先在原镜像真实观察RED异常；解析边界捕获RecursionError转为422/MODEL_REQUEST_INVALID，不启动worker、不产生points。GREEN完整44/44，外部弃用提示1；实际重建部署后深层HTTP422、health UP，九组真实三股/三种子每次五点且重复一致。修复52fbfa9，仅HTTP边界和回归文件改变，制品计算实现及包哈希不变。完成一次修复通道，不重复派审查者。

## 决策记录（Rulings）

1. Ruling：用户已授权逐步自治开发，保持固定E:\aiStock、既有codex分支和本代理直接执行，先交付兼容运行单元，再正式模型发布/用户预测；依据用户固定目录、全权负责、简单事项不用再问；若取舍不合适，代价为回滚本批提交重新切分，当前用户预测入口晚一批开放。保持分支，不合并或push；无Git remote。
2. Ruling：保留上游所需类体，仅改包内导入和裁剪无关类；整体研究依赖过重且本机路径不可用于容器，重新设计会破坏权重；以MIT/来源及提取哈希、安全strict重载和225点对照约束。若兼容性判断错，需恢复上游实现并重跑迁移，本批对照仅证明现有窗口迁移一致。
3. Ruling：真实候选ID加入制品/运行实现联合哈希后缀；固定种子ID无法在实现修复后维持不可变包；代价为后续模型登记/调用需完整ID，旧包保留而非覆盖。
4. Final: Ruling：审查边界内不判定未来准确性、不独立重算MS-02存储inputHash、不将候选验收当管理员发布或用户预测；当前inputHash回显，另有转换/标准化张量摘要，管理员/任务与准确性在后续专属验收。若对边界理解错，代价为补充该批规格和相应业务/实验验证；当前不能承诺准确率或开放正式预测。

## 延后项与验证边界

本批无新增Minor。SDD-005A已登记的SDK退出错误诊断仍在pending-items.md，不因本批完成而被删除。外部依赖弃用提示没有被掩盖。43项为审查前初版，44项为最终；Java与前端未变，本批不虚构其新测试或页面验收。
