# 不可变行情版本

作用：本行情库的版本、日线、日历与当前指针。实现：本地事务锁定操作和版本槽，幂等哈希、revision检查后切换head，失败记录独立保存，历史日线不覆盖。其他服务不直接读库。

## RAW修订事件
MarketVersionRepository在发布事务内锁全局sequence后记录head变化，重放或相同head不生成事件；RevisionEventRepository/Controller提供仅内部有界轮询，V8回填已有head。序号锁一直持有至事务结束，防止后提交小ID被跳过。

SDD010B3 Task3B：计算证据读取/派生发布接入MarketEvidenceGate.local短事务校验，按业务凭据固定RAW；外部采集在事务外，发布前再次校验。门禁默认关闭，实际owner转发和完整上线仍在后续阶段，历史快照与只读元数据保留。
