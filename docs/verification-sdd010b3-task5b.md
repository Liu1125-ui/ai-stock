# SDD010B3 Task5B 持仓消费者核验

2026-11-08，基线cbb54fa。非空持仓原子绑定原股票全集；空仓无需市场许可。使用原执行lease UUID，generation=1，无重领；原30秒期限/45秒租期、原持仓revision复核不变。

- RED：新owner尚不存在时编译失败，runtime/lifecycle-task5b-red.log。实现初轮参数顺序编译错误已修正。
- 专项37项通过，后补真实owner行锁等待/锁后Clock读取和本机HTTP凭据隔离，最终专项10项通过（其中6项MySQL）。覆盖成功提交后通知、不可重领超时、时钟回拨、拒绝/错RAW全集、空仓、未知登记只查原编号。
- 完整verify：公共65/行情313/预测312共690项，失败/错误/跳过0；两个服务打包成功，20:56:53。日志runtime/lifecycle-task5b-final-verify.log。
- 独立portfolio_market_use_review只读审查：无Important/Critical。配置静态校验及git diff --check通过。
- 新使用表仅保存股票选择和行情回执，无会话、持仓数量或平均成本。报价带凭据，用户snapshot/revision不带。固定引用扫描19类。

V16仅在隔离stock_prediction_test迁移；默认开关false，未部署业务服务，未运行真实行情/AI/邮件。全局PARTIAL，预警、本地派生/调度、管理员与部署验收继续，不能关闭FR-11。
