# quotation

作用与实现：日线和指标值类型。实现：不可变record承载BigDecimal原始事实，接口视图另投影字符串。

AnalysisEvidenceController：内部服务认证后，按指定RAW行情版本和日期范围复用统一指标查询，为MS03提供可追溯分析依据；不能由前端上传指标代替。

SDD010B3 Task3B：计算证据读取/派生发布接入MarketEvidenceGate.local短事务校验，按业务凭据固定RAW；外部采集在事务外，发布前再次校验。门禁默认关闭，实际owner转发和完整上线仍在后续阶段，历史快照与只读元数据保留。
