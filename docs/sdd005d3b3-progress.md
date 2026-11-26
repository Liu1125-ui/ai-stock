# SDD ledger — plan: docs/superpowers/plans/2026-10-05-sdd005d3b3-revision-verification-plan.md
Ruling: 继续用户授权自主常规细节，固定E:\aiStock codex分支，设计与计划自主自审后inline执行，不重复批准流程；不push/merge。
Ruling: 使用tracked ledger和显式PowerShell检查，不运行Bash包装工具，不宣称执行了其自动brief。
Preflight: Task1 event items及affected DuePage由Task2消费，字段一致；不修改既有verify body，事件key加入eventId应对RAW回切。现有任务claim无需新增字段，任务key保留触发身份，实际证据记最新有效RAW。

Task 1: complete — 缺RevisionEventRepository编译RED，事件去重/回切/失败与范围完整hidden/分页GREEN，含旧发布/日扫描回归。隔离MySQL V8成功；日志runtime/d3b3-task1-red.log和green.log。

Task 2: complete — 缺类/方法编译RED；5项持久扫描、2项HTTP契约及旧worker/分页/Spring上下文回归GREEN。一次Mockito throw后when重设桩触发测试自身异常，改doReturn；不是生产异常。检查发现Feign MARKET_URL在Compose刻意为空，新增专用REVISION_MARKET_URL，不改变既有发现方式。全量回归将额外核验事件插入后的事务回滚。

Final review: 唯一只读reviewer revision_verification_final_review，范围fdbe261..6ec76fd，Critical0/Important1/Minor0。Important：事件HTTP跨20点/故障跨日，旧cutoff重核不能替换更高due current。一次fix pass：cursor和publish推进日期重置页，HTTP后重读clock，任务key加入due的SHA256；跨日新测试RED expected10/7 actual10/5，随后8项扫描/3项影响范围GREEN（含完成五点current新证据更新，后者为既有逻辑补覆盖不冒称曾RED）。日志runtime/d3b3-crossday-*.log。
Ruling: 调整原固定cutoff为逐轮固定、跨日重新分页 — 防止完整current旧RAW永久遗漏 — 已提交旧轮任务保留，新轮幂等键独立，有界扫描增加工作但不丢修订。

Final regression: 后端clean verify最终279项，失败/错误/跳过0，2026-10-05测试日志runtime/d3b3-final-full.log。2026-10-07继续部署时原MySQL/Nacos等容器停止，before采样失败且保留10月5日baseline；恢复Compose全栈，无重置库。运行probe按当前北京时间20点规则得到10月6日cutoff，允许正常新增行情任务但逐条确认旧payload及原预测/model不变，避免用旧日期误验。运行验收继续进行。

Task 3: complete — 最终279后端0失败/错误/跳过；前端未改，D3B2原40及构建证据保留。10月7日V8/V4部署、服务健康、新接口匿名401/网关404；六个真实发布事件消费，受控重核SKIPPED重复同task/一attempt/null版本。部分扫描及终态两次重启保留；原预测/model/旧任务payload与旧baseline一致。新行情更新合法，已记录；首目标10/8未到期，FR06真实效果继续开放。证据docs/evidence/sdd005d3b3-20261007.json。保留本分支无remote不push，CodeArts未核实，图形未观察。
