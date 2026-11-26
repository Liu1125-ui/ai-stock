# SDD010B3 Task5B 持仓行情使用实施计划

执行方式：executing-plans，当前工作区逐项执行，功能末一次独立审查。用户已授权连续实施，不增加例行确认。

目标：持仓原股票快照在报价之前原子登记CURRENT_HEADS完整集合；不改变30秒期限、45秒租约、持仓revision复核、空仓及旧结果。

1. [x] 先写真实MySQL与服务反例：多股绑定、拒绝不取报价、空仓不登记、错RAW集合不保存、超时终态/时钟回拨、未知登记原编号恢复。
2. [x] V16新增本地portfolio_market_use及父操作fencing；唯一执行身份使用原lease UUID、协议代次1。持仓不重领，过期物理FAILED后不能复活。锁父行后重新读取Clock，终态证明只投影真实操作。
3. [x] owner登记原股票集合；报价RPC带凭据，用户snapshot/revision不携带；完整RAW集合匹配后才计算，结果提交后通知。恢复只查原市场requestId，不持有会话或重读持仓。
4. [x] 能力/proof/恢复/固定引用扫描和默认false配置同步；保留9字段证明，不暴露owner/request/持仓数量成本。
5. [x] 专项及完整回归/打包、静态配置检查、独立审查，记录并单功能提交；继续预警消费者。

边界：启用保护后非空股票若任一RAW不存在或不可新用，整组拒绝；不降级混用当前头。NO_VALID_CLOSE/日历冲突仍有RAW可保留原部分估值逻辑。空仓无需市场。报价读到NO_DATA或RAW全集不一致时失败并保留旧结果。calendar非RAW集合成员。新配置不部署/开启，禁止真实模型、行情、SMTP调用。

结果：690项verify通过及打包，独立审查无重要项。核验见docs/verification-sdd010b3-task5b.md。未部署，继续Task5C。
