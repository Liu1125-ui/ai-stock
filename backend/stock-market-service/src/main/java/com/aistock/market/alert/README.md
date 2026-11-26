# 预警生效边界
作用：给新建/修改/启用规则确定排除历史提醒的固定边界。实现：读取已发布RAW与完整交易日历，使用北京时间更新时点；不采集外部行情。

SDD006B1：AlertEvidenceService固定最新应到期日的RAW/日历和双日后复权证据；AlertEvidenceStore保存不可变版本及带租约的幂等回执，采集不占数据库事务。价格与涨跌幅证据可用性分开，失败不造价格或补零。内部prepare/operations/versions只接受服务令牌，不开放网关；此切片不自动触发、不生成消息/邮件。

SDD010B3 Task3B：计算证据读取/派生发布接入MarketEvidenceGate.local短事务校验，按业务凭据固定RAW；外部采集在事务外，发布前再次校验。门禁默认关闭，实际owner转发和完整上线仍在后续阶段，历史快照与只读元数据保留。

Task5C新增alert-evidence-references/{id}只投影三个引用字段，内部认证；不读证据正文/价格，不生成新引用。原versions/{id}仍受使用凭据门禁。

Task5D3 AlertLocalUseOwner使用原请求canonical JSON核对父hash，先准入原RAW再读/采集；真实终态与发布同事务fencing，关闭scope后释放。恢复只查原登记，默认false。
