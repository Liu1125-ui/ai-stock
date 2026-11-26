# SDD010B3 Task5D1 调度拒绝处理核验

2026-11-08，基线2162195。比较/预测验证/预警适配器仅把明确许可拒绝映射为原attempt FAILED，原冻结RAW不变；未知响应和只读回执异常继续原编号核对，不重POST。

- 三项HTTP反例RED：现有客户端抛出Remote，不能直接停止明确被拒任务。实现后39项专项通过；真实MySQL覆盖比较/验证及共享预警工作均无自动重试，原RAW/次数保留。
- 独立scheduler_market_denials_review发现Important：两个旧HTTP客户端宽松解析重复code/尾随JSON。新增两项反例真实RED，改copy mapper严格重复/尾随校验后完整GREEN；不修改注入的共享mapper。
- 修订后完整verify公共65/调度284共349项通过，失败/错误/跳过0，调度打包成功，21:19:05。日志runtime/lifecycle-task5d1-review-final-verify.log。此前347项不是修订后结果。
- 配置/重试/取消范围未扩大，安全中文原因已补齐；git diff --check通过。未部署或真实取数/AI/邮件。

Task5D2市场比较本地owner、后续预警本地派生与入口审计、Task6—8继续；FR-11保持开放。
