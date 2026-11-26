# SDD010B3 Task5A 多股比较核验

2026-11-08；基线72f1bef。仅比较消费者接入，FR-11仍未完成。

比较沿用原evidenceId与股票配对，每轮原lease UUID为内部执行身份。重领与终态证明在父行锁内隔离旧执行，终态记录与结果同事务提交。旧执行即使时钟回拨也不能保存；新轮仍可重领。

新V15仅在标记明确的隔离测试MySQL应用。开发期间为验证收紧后的CHECK约束重建了隔离stock_prediction_test，未触碰业务库或卷。所有新增开关默认false，未部署、未启用全局生命周期动作。

验证：

- Maven auth-integration/cli-build完整verify：公共65、行情313、预测305，共683项，失败/错误/跳过均0；两个服务打包成功。日志runtime/lifecycle-task5a-final-verify.log，BUILD SUCCESS，20:40:18。
- 新增6项真实MySQL专项覆盖证明与重领双向顺序/双线程竞争、旧执行迟到与时钟回拨、完整创建、拒绝/错RAW集合、登记响应未知后的原编号恢复。新增完整集合回执校验，本机HTTP验证凭据转发。
- 专项首轮缺少实现编译失败，随后实现；后补测试出现Mockito重设已有Answer时空参数触发异常，改用doAnswer夹具后通过。该次异常属于测试夹具，未掩盖业务失败。
- 独立审查comparison_market_use_review无Important/Critical；未委托实现或使用真实数据源。
- Compose配置静态校验与git diff --check通过。前端、Python本阶段未修改，未重跑或宣称新增验收。

未完成：持仓、预警、本地派生/调度消费者及管理员生命周期入口、全链路部署验收。生产开关保持关闭；无真实AI、取数、邮件调用。
